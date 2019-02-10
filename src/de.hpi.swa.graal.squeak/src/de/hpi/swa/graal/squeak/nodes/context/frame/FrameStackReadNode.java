package de.hpi.swa.graal.squeak.nodes.context.frame;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@NodeInfo(cost = NodeCost.NONE)
@ImportStatic(CONTEXT.class)
public abstract class FrameStackReadNode extends AbstractNodeWithCode {

    protected FrameStackReadNode(final CompiledCodeObject code) {
        super(code);
    }

    public static FrameStackReadNode create(final CompiledCodeObject code) {
        return FrameStackReadNodeGen.create(code);
    }

    public final Object executeTemp(final Frame frame, final int stackIndex) {
        return execute(frame, stackIndex - code.getNumArgsAndCopied());
    }

    public abstract Object execute(Frame frame, int stackIndex);

    @Specialization(guards = "index < 0")
    protected static final Object doReadFromFrameArgument(final Frame frame, final int index) {
        final Object[] arguments = frame.getArguments();
        assert arguments.length + index >= FrameAccess.ArgumentIndicies.RECEIVER.ordinal();
        return arguments[arguments.length + index];
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"index >= 0", "index == cachedIndex"}, limit = "MAX_STACK_SIZE")
    protected static final Object doReadFromFrameSlot(final Frame frame, final int index,
                    @Cached("index") final int cachedIndex,
                    @Cached("code.getStackSlot(index)") final FrameSlot slot,
                    @Cached("create(slot)") final FrameSlotReadNode readNode) {
        return readNode.executeRead(frame);
    }

    @SuppressWarnings("unused")
    @Specialization(replaces = "doReadFromFrameSlot")
    protected static final Object doFail(final Frame frame, final int stackIndex) {
        throw SqueakException.create("Unexpected failure in FrameStackReadNode");
    }
}
