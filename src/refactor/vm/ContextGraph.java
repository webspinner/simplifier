package refactor.vm;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.ArrayList;
import java.util.List;

import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.MethodLocation;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.util.ReferenceUtil;
import org.jf.dexlib2.writer.builder.BuilderMethod;
import org.jf.util.SparseArray;

import refactor.handler.OpHandler;
import refactor.handler.OpHandlerFactory;

public class ContextGraph {

    private final TIntIntHashMap indexToAddress;
    private final SparseArray<List<ContextNode>> indexToNodePile;
    private final String methodDescriptor;

    ContextGraph(VirtualMachine vm, BuilderMethod method) {
        methodDescriptor = ReferenceUtil.getMethodDescriptor(method);

        MutableMethodImplementation implementation = (MutableMethodImplementation) method.getImplementation();
        List<BuilderInstruction> instructions = implementation.getInstructions();
        indexToAddress = buildIndexToAddress(instructions);

        indexToNodePile = buildIndexToNodePile(vm, methodDescriptor, instructions);
    }

    ContextGraph(ContextGraph other) {
        methodDescriptor = other.methodDescriptor;
        indexToAddress = other.indexToAddress;

        indexToNodePile = new SparseArray<List<ContextNode>>(other.indexToNodePile.size());
        for (int i = 0; i < other.indexToNodePile.size(); i++) {
            int index = i;
            List<ContextNode> otherNodePile = other.indexToNodePile.get(index);
            List<ContextNode> nodePile = new ArrayList<ContextNode>(otherNodePile.size());
            for (ContextNode otherNode : otherNodePile) {
                nodePile.add(new ContextNode(otherNode));
            }

            indexToNodePile.put(index, nodePile);
        }
    }

    public String getMethodDescriptor() {
        return methodDescriptor;
    }

    private static TIntIntHashMap buildIndexToAddress(List<BuilderInstruction> instructions) {
        TIntIntHashMap indicies = new TIntIntHashMap(instructions.size());

        for (BuilderInstruction instruction : instructions) {
            MethodLocation location = instruction.getLocation();
            int index = location.getIndex();
            int address = location.getCodeAddress();
            indicies.put(index, address);
        }

        return indicies;
    }

    private static SparseArray<List<ContextNode>> buildIndexToNodePile(VirtualMachine vm, String methodDescriptor,
                    List<BuilderInstruction> instructions) {
        OpHandlerFactory handlerFactory = new OpHandlerFactory(vm, methodDescriptor);

        SparseArray<List<ContextNode>> result = new SparseArray<List<ContextNode>>(instructions.size());
        for (BuilderInstruction instruction : instructions) {
            int index = instruction.getLocation().getIndex();
            OpHandler handler = handlerFactory.create(instruction, index);
            ContextNode node = new ContextNode(handler);

            // Most node piles will be a template node and one node with context.
            List<ContextNode> nodePile = new ArrayList<ContextNode>(2);
            nodePile.add(node);

            result.put(index, nodePile);
        }

        return result;
    }

    public List<ContextNode> getNodePileByIndex(int index) {
        List<ContextNode> result = indexToNodePile.get(index);
        result = result.subList(1, result.size()); // remove template node

        return result;
    }

    public List<ContextNode> getNodePileByAddress(int address) {
        int index = indexToAddress.get(address);

        return getNodePileByIndex(index);
    }

    ContextNode getTemplateNodeByAddress(int address) {
        int index = indexToAddress.get(address);

        return getTemplateNodeByIndex(index);
    }

    ContextNode getTemplateNodeByIndex(int index) {
        return indexToNodePile.get(index).get(0);
    }

    ContextNode getRootNode() {
        // There is only one entry point for a method.
        return indexToNodePile.get(0).get(0);
    }

    int getNodeCount() {
        return indexToNodePile.size();
    }

    void setRootContext(MethodContext mectx) {
        getRootNode().setContext(mectx);
    }

    public TIntList getTerminalIndicies() {
        TIntList result = new TIntArrayList();

        // TODO: implement

        return result;
    }

    public boolean isInstructionReachable(int index) {
        if (index == 0) {
            // Root is always reachable
            return true;
        }

        List<ContextNode> nodePile = indexToNodePile.get(index);

        // If this index was reached during execution, there would be clones in the pile.
        return nodePile.size() > 1;
    }
}
