package simplify.vm.handlers;

import org.jf.dexlib2.iface.instruction.Instruction;

import simplify.vm.MethodContext;

public class UnaryMathOpHandler extends OpHandler {

    static UnaryMathOpHandler create(Instruction instruction, int address) {
        String opName = instruction.getOpcode().name;
        int childAddress = address + instruction.getCodeUnits();

        return new UnaryMathOpHandler(address, opName, childAddress);
    }

    UnaryMathOpHandler(int address, String opName, int childAddress) {
        super(address, opName, childAddress);
    }

    @Override
    public int[] execute(MethodContext mctx) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return null;
    }

}
