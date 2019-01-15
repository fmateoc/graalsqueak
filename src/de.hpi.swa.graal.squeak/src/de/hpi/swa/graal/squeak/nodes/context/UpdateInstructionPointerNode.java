package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;

public abstract class UpdateInstructionPointerNode extends AbstractNodeWithCode {
    private final int initialPC;

    public static UpdateInstructionPointerNode create(final CompiledCodeObject code) {
        return UpdateInstructionPointerNodeGen.create(code);
    }

    protected UpdateInstructionPointerNode(final CompiledCodeObject code) {
        super(code);
        initialPC = code instanceof CompiledBlockObject ? ((CompiledBlockObject) code).getInitialPC() : ((CompiledMethodObject) code).getInitialPC();
    }

    public abstract void executeUpdate(VirtualFrame frame, int value);

    @Specialization(guards = {"isVirtualized(frame)"})
    protected final void doUpdateVirtualized(final VirtualFrame frame, final int value) {
        frame.setInt(code.instructionPointerSlot, initialPC + value);
    }

    @Fallback
    protected final void doUpdate(final VirtualFrame frame, final int value) {
        getContext(frame).setInstructionPointer(initialPC + value);
    }
}
