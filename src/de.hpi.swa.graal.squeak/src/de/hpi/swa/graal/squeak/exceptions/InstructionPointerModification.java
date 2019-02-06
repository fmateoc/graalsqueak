package de.hpi.swa.graal.squeak.exceptions;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ControlFlowException;

import de.hpi.swa.graal.squeak.model.ContextObject;

public final class InstructionPointerModification extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    private final ContextObject targetContext;
    private final int newBytecodeLoopPC;

    private InstructionPointerModification(final ContextObject targetContext, final int newBytecodeLoopPC) {
        this.targetContext = targetContext;
        this.newBytecodeLoopPC = newBytecodeLoopPC;
    }

    public static InstructionPointerModification create(final ContextObject targetContext, final int newBytecodeLoopPC) {
        return new InstructionPointerModification(targetContext, newBytecodeLoopPC);
    }

    public ContextObject getTargetContext() {
        return targetContext;
    }

    public int getNewBytecodeLoopPC() {
        return newBytecodeLoopPC;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "Instruction pointer modified to " + newBytecodeLoopPC;
    }
}
