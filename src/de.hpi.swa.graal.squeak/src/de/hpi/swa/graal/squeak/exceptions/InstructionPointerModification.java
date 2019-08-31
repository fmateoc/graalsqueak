package de.hpi.swa.graal.squeak.exceptions;

import com.oracle.truffle.api.nodes.ControlFlowException;

import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;

public final class InstructionPointerModification extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    private final ContextObject targetContext;

    private InstructionPointerModification(final ContextObject targetContext) {
        this.targetContext = targetContext;
    }

    public static InstructionPointerModification create(final ContextObject targetContext) {
        return new InstructionPointerModification(targetContext);
    }

    public int getPcOrRethrow(final ContextObject contextOrNull, final boolean hasPrimitive) {
        if (targetContext == contextOrNull) {
            final int pc = targetContext.getInstructionPointerForBytecodeLoop();
            if (hasPrimitive && pc == 0) {
                // skip primitive and go directly to fallback code.
                return CallPrimitiveNode.NUM_BYTECODES;
            } else {
                return pc;
            }
        } else {
            throw this;
        }
    }
}
