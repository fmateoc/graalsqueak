package de.hpi.swa.graal.squeak.nodes.context.frame;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@NodeInfo(cost = NodeCost.NONE)
@ImportStatic(CONTEXT.class)
public abstract class FrameStackWriteNode extends AbstractNodeWithCode {

    protected FrameStackWriteNode(final CompiledCodeObject code) {
        super(code);
    }

    public static FrameStackWriteNode create(final CompiledCodeObject code) {
        return FrameStackWriteNodeGen.create(code);
    }

    public final void executeTemp(final Frame frame, final int stackIndex, final Object value) {
        execute(frame, stackIndex - code.getNumArgsAndCopied(), value);
    }

    public abstract void execute(Frame frame, int stackIndex, Object value);

    @Specialization(guards = "index < 0")
    protected static final void doWriteToFrameArgument(final Frame frame, final int index, final Object value) {
        final Object[] arguments = frame.getArguments();
        assert arguments.length + index > FrameAccess.expectedArgumentSize(-1) : "Overwriting value at non-argument index";
        arguments[arguments.length + index] = value;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"index >= 0", "index == cachedIndex", "code == cacheCode"}, limit = "MAX_STACK_SIZE")
    protected static final void doWriteToFrameSlot(final VirtualFrame frame, final int index, final Object value,
                    @Cached("index") final int cachedIndex,
                    @Cached("code") final CompiledCodeObject cacheCode,
                    @Cached("create(cacheCode.getStackSlot(index))") final FrameSlotWriteNode writeNode) {
        writeNode.executeWrite(frame, value);
    }

    @SuppressWarnings("unused")
    @Specialization(replaces = "doWriteToFrameSlot")
    protected static final void doFail(final Frame frame, final int stackIndex, final Object value) {
        throw SqueakException.create("Unexpected failure in FrameStackWriteNode");
    }
}
