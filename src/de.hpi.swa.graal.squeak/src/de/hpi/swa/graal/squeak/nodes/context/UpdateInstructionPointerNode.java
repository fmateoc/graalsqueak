package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.accessing.CompiledCodeNodes.GetInitialPCNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public abstract class UpdateInstructionPointerNode extends AbstractNodeWithCode {
    @Child private GetInitialPCNode initalPCNode = GetInitialPCNode.create();

    protected UpdateInstructionPointerNode(final CompiledCodeObject code) {
        super(code);
    }

    public static UpdateInstructionPointerNode create(final CompiledCodeObject code) {
        return UpdateInstructionPointerNodeGen.create(code);
    }

    public abstract void executeUpdate(VirtualFrame frame, int value);

    @Specialization(guards = "!isVirtualized(frame)")
    protected final void doUpdate(final VirtualFrame frame, final int value) {
        FrameAccess.setInstructionPointer(frame, code, initalPCNode.execute(code) + value);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected final void doNothing(final VirtualFrame frame, final int value) {
        // Nothing to do.
        // FrameAccess.setInstructionPointer(frame, code, initialPC + value);
    }
}
