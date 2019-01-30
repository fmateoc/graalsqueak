package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public abstract class UpdateInstructionPointerNode extends AbstractNodeWithCode {
    private final int initialPC;

    protected UpdateInstructionPointerNode(final CompiledCodeObject code) {
        super(code);
        initialPC = code instanceof CompiledBlockObject ? ((CompiledBlockObject) code).getInitialPC() : ((CompiledMethodObject) code).getInitialPC();
    }

    public static UpdateInstructionPointerNode create(final CompiledCodeObject code) {
        return UpdateInstructionPointerNodeGen.create(code);
    }

    public abstract void executeUpdate(VirtualFrame frame, int value);

    @Specialization(guards = "!isVirtualized(frame)")
    protected final void doUpdate(final VirtualFrame frame, final int value) {
        FrameAccess.setInstructionPointer(frame, code, initialPC + value);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected final void doNothing(final VirtualFrame frame, final int value) {
        // Nothing to do.
        // FrameAccess.setInstructionPointer(frame, code, initialPC + value);
    }
}
