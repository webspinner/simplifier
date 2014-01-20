package refactor.handler;

import java.util.logging.Logger;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction22t;

import refactor.vm.MethodContext;
import simplify.Main;
import simplify.exec.InstructionExecutor;
import simplify.exec.UnknownValue;

public class IfOpHandler extends OpHandler {

    private static enum IfType {
        EQUAL,
        NOT_EQUAL,
        LESS,
        LESS_OR_EQUAL,
        GREATER,
        GREATOR_OR_EQUAL
    }

    private static final Logger log = Logger.getLogger(Main.class.getSimpleName());

    private static IfType getIfType(String opName) {
        IfType result = null;

        if (opName.contains("-eq")) {
            result = IfType.EQUAL;
        } else if (opName.contains("-ne")) {
            result = IfType.NOT_EQUAL;
        } else if (opName.contains("-lt")) {
            result = IfType.LESS;
        } else if (opName.contains("-le")) {
            result = IfType.LESS_OR_EQUAL;
        } else if (opName.contains("-gt")) {
            result = IfType.GREATER;
        } else if (opName.contains("-ge")) {
            result = IfType.GREATOR_OR_EQUAL;
        }

        return result;
    }

    private static boolean isTrue(IfType ifType, int cmp) {
        boolean result = false;

        switch (ifType) {
        case EQUAL:
            result = (cmp == 0);
            break;
        case GREATER:
            result = (cmp == 1);
            break;
        case GREATOR_OR_EQUAL:
            result = (cmp >= 0);
            break;
        case LESS:
            result = (cmp == -1);
            break;
        case LESS_OR_EQUAL:
            result = (cmp <= 0);
            break;
        case NOT_EQUAL:
            result = (cmp != 0);
            break;
        }

        return result;
    }

    static IfOpHandler create(Instruction instruction, int address) {
        int branchOffset = ((OffsetInstruction) instruction).getCodeOffset();
        int targetAddress = address + branchOffset;

        String opName = instruction.getOpcode().name;
        IfType ifType = getIfType(opName);
        int register1 = ((OneRegisterInstruction) instruction).getRegisterA();

        if (instruction instanceof Instruction22t) {
            // if-* vA, vB, :label
            Instruction22t instr = (Instruction22t) instruction;

            return new IfOpHandler(address, opName, ifType, targetAddress, register1, instr.getRegisterB());
        } else {
            // if-*z vA, vB, :label (Instruction 21t)
            return new IfOpHandler(address, opName, ifType, targetAddress, register1);
        }
    }

    private final int address;
    private final String opName;
    private final int targetAddress;
    private final IfType ifType;

    private final int register1;

    private int register2;

    private boolean compareToZero;

    private IfOpHandler(int address, String opName, IfType ifType, int targetAddress, int register1) {
        this.address = address;
        this.opName = opName;
        this.ifType = ifType;
        this.targetAddress = targetAddress;
        this.register1 = register1;
        compareToZero = true;
    }

    private IfOpHandler(int address, String opName, IfType ifType, int targetAddress, int register1, int register2) {
        this(address, opName, ifType, targetAddress, register1);
        this.register2 = register2;
        compareToZero = false;
    }

    @Override
    public int[] execute(MethodContext mectx) {
        Object A = mectx.getRegisterValue(register1, address);
        Object B;
        if (compareToZero) {
            B = 0;
        } else {
            B = mectx.getRegisterValue(register2, address);
        }

        // Ambiguous predicate. Must assume either branch.
        if ((A instanceof UnknownValue) || (B instanceof UnknownValue)) {
            return getPossibleChildren();
        }

        int cmp = CompareToBuilder.reflectionCompare(A, B);
        log.finer("IF compare: " + A + " vs " + B + " = " + cmp);

        int result = InstructionExecutor.NEXT_INSTRUCTION;
        if (isTrue(ifType, cmp)) {
            result = targetAddress;
        }

        return new int[] { result };
    }

    @Override
    public int getAddress() {
        return address;
    }

    @Override
    public int[] getPossibleChildren() {
        return new int[] { targetAddress, InstructionExecutor.NEXT_INSTRUCTION };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(opName);

        sb.append(" r").append(register1);
        if (!compareToZero) {
            sb.append(", r").append(register2);
        }
        sb.append(", #").append(targetAddress);

        return sb.toString();
    }

}
