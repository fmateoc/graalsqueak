package de.hpi.swa.graal.squeak.nodes.context.frame;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@ReportPolymorphism
@ImportStatic(CONTEXT.class)
public abstract class FrameStackReadAndClearNode extends AbstractNodeWithCode {
    @Children FrameSlotReadNode[] readNodes;

    protected FrameStackReadAndClearNode(final CompiledCodeObject code) {
        super(code);
        readNodes = new FrameSlotReadNode[code.getNumStackSlots()];
    }

    public static FrameStackReadAndClearNode create(final CompiledCodeObject code) {
        return FrameStackReadAndClearNodeGen.create(code);
    }

    public final Object executePop(final VirtualFrame frame) {
        final int newSP = FrameAccess.getStackPointer(frame, code) - 1;
        assert newSP >= 0 : "Bad stack pointer";
        FrameAccess.setStackPointer(frame, code, newSP);
        return execute(frame, newSP, 1)[0];
    }

    @ExplodeLoop
    public final Object[] executePopN(final VirtualFrame frame, final int numPop) {
        CompilerAsserts.compilationConstant(numPop);
        final int newSP = FrameAccess.getStackPointer(frame, code) - numPop;
        assert newSP >= 0 : "Bad stack pointer";
        FrameAccess.setStackPointer(frame, code, newSP);
        return execute(frame, newSP, numPop);
    }

    protected abstract Object[] execute(VirtualFrame frame, int sp, int numPop);

    @SuppressWarnings("unused")
    @Specialization(guards = {"numPop == 0"})
    protected static final Object[] doNone(final VirtualFrame frame, final int sp, final int numPop) {
        return ArrayUtils.EMPTY_ARRAY;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"numPop == 1", "sp == cachedSP"}, limit = "MAX_STACK_SIZE")
    protected final Object[] doSingle(final VirtualFrame frame, final int sp, final int numPop,
                    @Cached("sp") final int cachedSP) {
        return new Object[]{getReadNode(cachedSP).executeRead(frame)};
    }

    @SuppressWarnings("unused")
    @ExplodeLoop
    @Specialization(guards = {"numPop > 1", "sp == cachedSP"}, limit = "6")
    protected final Object[] doMultiple(final VirtualFrame frame, final int sp, final int numPop,
                    @Cached("sp") final int cachedSP) {
        CompilerAsserts.compilationConstant(numPop);
        final Object[] result = new Object[numPop];
        for (int i = 0; i < numPop; i++) {
            result[i] = getReadNode(cachedSP + i).executeRead(frame);
            assert result[i] != null;
        }
        return result;
    }

    protected final FrameSlotReadNode getReadNode(final int index) {
        if (readNodes[index] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Only clear stack values, not receiver, arguments, or temporary variables.
            final boolean clear = index >= code.getNumArgsAndCopied() + code.getNumTemps();
            readNodes[index] = insert(FrameSlotReadNode.create(code.getStackSlot(index), clear));
        }
        return readNodes[index];
    }
}
