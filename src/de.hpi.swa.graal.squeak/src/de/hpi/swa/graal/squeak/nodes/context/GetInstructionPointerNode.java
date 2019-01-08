package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;

public abstract class GetInstructionPointerNode extends AbstractNodeWithCode {

    public static GetInstructionPointerNode create(final CompiledCodeObject code) {
        return GetInstructionPointerNodeGen.create(code);
    }

    protected GetInstructionPointerNode(final CompiledCodeObject code) {
        super(code);
    }

    public abstract int executeUpdate(VirtualFrame frame);

    @Specialization(guards = {"isVirtualized(frame)"})
    protected final int doUpdateVirtualized(final VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, code.instructionPointerSlot);
    }

    @Fallback
    protected final int doUpdate(final VirtualFrame frame) {
        return (int) getContext(frame).getInstructionPointer();
    }
}
