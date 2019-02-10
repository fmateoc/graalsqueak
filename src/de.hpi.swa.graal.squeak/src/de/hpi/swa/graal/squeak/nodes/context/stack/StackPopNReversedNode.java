package de.hpi.swa.graal.squeak.nodes.context.stack;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackReadAndClearNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@ImportStatic(FrameAccess.class)
public abstract class StackPopNReversedNode extends AbstractNodeWithCode {
    protected final int numPop;

    protected StackPopNReversedNode(final CompiledCodeObject code, final int numPop) {
        super(code);
        this.numPop = numPop;
    }

    public static StackPopNReversedNode create(final CompiledCodeObject code, final int numPop) {
        return StackPopNReversedNodeGen.create(code, numPop);
    }

    public final Object[] executePopN(final VirtualFrame frame) {
        return executePopNSpecialized(frame, FrameAccess.getStackPointer(frame, code));
    }

    public abstract Object[] executePopNSpecialized(VirtualFrame frame, int currentSP);

    @SuppressWarnings("unused")
    @Specialization(guards = {"numPop == 0"})
    protected static final Object[] doEmpty(final VirtualFrame frame, final int currentSP) {
        return ArrayUtils.EMPTY_ARRAY;
    }

    @Specialization(guards = {"numPop == 1", "currentSP < 0"})
    protected final Object[] doPopFromFrameArguments(final VirtualFrame frame, final int currentSP) {
        final Object[] arguments = frame.getArguments();
        final int argumentIndex = arguments.length - 1 + currentSP;
        final Object[] result = new Object[]{arguments[argumentIndex]};
        arguments[argumentIndex] = code.image.nil;
        return result;
    }

    @Specialization(guards = {"numPop > 1", "currentSP < 0"})
    protected final Object[] doPopNFromFrameArguments(final VirtualFrame frame, final int currentSP) {
        FrameAccess.setStackPointer(frame, code, currentSP - numPop);
        final Object[] arguments = frame.getArguments();
        final int argumentsLength = arguments.length;
        final int startIndex = argumentsLength + currentSP - numPop;
        final int endIndex = argumentsLength + currentSP;
        final Object[] result = Arrays.copyOfRange(arguments, startIndex, endIndex);
        Arrays.fill(arguments, startIndex, endIndex, code.image.nil);
        return result;
    }

    @ExplodeLoop
    @Specialization(guards = {"numPop > 0", "currentSP >= 0"})
    protected final Object[] doPopNMixed(final VirtualFrame frame, final int currentSP,
                    @Cached("create(code)") final FrameStackReadAndClearNode readAndClearNode) {
        assert currentSP - numPop >= 0;
        final Object[] result = new Object[numPop];
        for (int i = 1; i <= numPop; i++) {
            result[numPop - i] = readAndClearNode.execute(frame, currentSP - i);
            assert result[numPop - i] != null;
        }
        FrameAccess.setStackPointer(frame, code, currentSP - numPop);
        return result;
    }

    @SuppressWarnings("unused")
    @Fallback
    protected static final Object[] doFail(final VirtualFrame frame, final int currentSP) {
        throw SqueakException.create("Should never happen");
    }
}
