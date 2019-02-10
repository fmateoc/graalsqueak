package de.hpi.swa.graal.squeak.nodes.context.stack;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.nodes.SqueakNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackReadAndClearNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public abstract class StackPopNode extends SqueakNodeWithCode {

    protected StackPopNode(final CompiledCodeObject code) {
        super(code);
    }

    public static StackPopNode create(final CompiledCodeObject code) {
        return StackPopNodeGen.create(code);
    }

    @Override
    public final Object executeRead(final VirtualFrame frame) {
        return executeReadSpecialized(frame, FrameAccess.getStackPointer(frame, code));
    }

    protected abstract Object executeReadSpecialized(VirtualFrame frame, int currentSP);

    @SuppressWarnings("unused")
    @Specialization(guards = {"currentSP < 0"})
    protected final Object doPopFromFrameArguments(final VirtualFrame frame, final int currentSP) {
        final Object[] arguments = frame.getArguments();
        final int argumentIndex = arguments.length - 1 + currentSP;
        final Object result = arguments[argumentIndex];
        arguments[argumentIndex] = code.image.nil;
        return result;
    }

    @Specialization(guards = {"currentSP >= 0"})
    public Object doPopFromFrameSlots(final VirtualFrame frame, final int currentSP,
                    @Cached("create(code)") final FrameStackReadAndClearNode readAndClearNode) {
        final int newSP = currentSP - 1;
        assert newSP + code.getNumArgsAndCopied() >= 0 : "Bad stack pointer";
        FrameAccess.setStackPointer(frame, code, newSP);
        return readAndClearNode.execute(frame, newSP);
    }
}
