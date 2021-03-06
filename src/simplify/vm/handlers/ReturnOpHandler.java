package simplify.vm.handlers;

import java.util.logging.Logger;

import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction11x;

import simplify.Main;
import simplify.vm.MethodContext;

public class ReturnOpHandler extends OpHandler {

    private static final Logger log = Logger.getLogger(Main.class.getSimpleName());

    static ReturnOpHandler create(Instruction instruction, int address) {
        String opName = instruction.getOpcode().name;
        Instruction11x instr = (Instruction11x) instruction;
        int register = instr.getRegisterA();

        return new ReturnOpHandler(address, opName, register);
    }

    private final int register;

    ReturnOpHandler(int address, String opName, int register) {
        super(address, opName, new int[0]);

        this.register = register;
    }

    @Override
    public int[] execute(MethodContext mctx) {
        mctx.assignReturnRegister(mctx.readRegister(register));

        return getPossibleChildren();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getOpName());

        sb.append(" r").append(register);

        return sb.toString();
    }

}
