package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;
import de.hpi.swa.graal.squeak.nodes.SqueakGuards;
import de.hpi.swa.graal.squeak.nodes.SqueakNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectAtPut0Node;

/**
 * This node should only be used for stores into associations, receivers, and remote temps as it
 * also marks {@link ContextObject}s as escaped when stored.
 */
@ImportStatic(SqueakGuards.class)
@NodeChild(value = "objectNode", type = SqueakNode.class)
@NodeChild(value = "valueNode", type = SqueakNode.class)
@NodeInfo(cost = NodeCost.NONE)
@ReportPolymorphism
public abstract class SqueakObjectAtPutAndMarkContextsNode extends Node {
    @Child private SqueakObjectAtPut0Node atPut0Node = SqueakObjectAtPut0Node.create();
    private final long index;

    public static SqueakObjectAtPutAndMarkContextsNode create(final long index, final SqueakNode object, final SqueakNode value) {
        return SqueakObjectAtPutAndMarkContextsNodeGen.create(index, object, value);
    }

    protected SqueakObjectAtPutAndMarkContextsNode(final long variableIndex) {
        index = variableIndex;
    }

    public abstract void executeWrite(VirtualFrame frame);

    @Specialization(guards = {"!isNativeObject(object)", "value.matches(frame)"})
    protected final void doFrameMarkerMatching(final VirtualFrame frame, final AbstractSqueakObject object, final FrameMarker value) {
        final ContextObject context = value.getMaterializedContext(frame);
        context.markEscaped();
        atPut0Node.execute(frame, object, index, context);
    }

    @Specialization(guards = {"!isNativeObject(object)", "!value.matches(frame)"})
    protected final void doFrameMarkerNotMatching(final VirtualFrame frame, final AbstractSqueakObject object, final FrameMarker value) {
        final ContextObject context = value.getMaterializedContext();
        context.markEscaped();
        atPut0Node.execute(frame, object, index, context);
    }

    @Specialization(guards = {"!isNativeObject(object)"})
    protected final void doContext(final VirtualFrame frame, final AbstractSqueakObject object, final ContextObject value) {
        value.markEscaped();
        atPut0Node.execute(frame, object, index, value);
    }

    @Specialization(guards = {"value.matches(frame)"})
    protected final void doFrameMarkerMatching(final VirtualFrame frame, final FrameMarker object, final FrameMarker value) {
        final ContextObject target = object.getMaterializedContext();
        final ContextObject context = value.getMaterializedContext(frame);
        target.markEscaped();
        context.markEscaped();
        target.atput0(index, context);
    }

    @Specialization(guards = {"!value.matches(frame)"})
    protected final void doFrameMarkerNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker object, final FrameMarker value) {
        final ContextObject target = object.getMaterializedContext();
        final ContextObject context = value.getMaterializedContext();
        target.markEscaped();
        context.markEscaped();
        if (context.isDirty) {
            context.image.printToStdErr("Mark as clean (terrible hack!!!):", context);
            context.isDirty = false;
        }
        target.atput0(index, context);
    }

    @Specialization(guards = {"object.matches(frame)"})
    protected final void doFrameMarkerMatching(final VirtualFrame frame, final FrameMarker object, final ContextObject value) {
        final ContextObject target = object.getMaterializedContext(frame);
        target.markEscaped();
        value.markEscaped();
        target.atput0(index, value);
    }

    @Specialization(guards = {"!object.matches(frame)"})
    protected final void doFrameMarkerNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker object, final ContextObject value) {
        final ContextObject target = object.getMaterializedContext();
        target.markEscaped();
        value.markEscaped();
        target.atput0(index, value);
    }

    @Specialization(guards = {"object.matches(frame)"})
    protected final void doFrameMarkerMatching(final VirtualFrame frame, final FrameMarker object, final BlockClosureObject value) {
        final ContextObject target = object.getMaterializedContext(frame);
        target.markEscaped();
        value.getHomeContext().markEscaped();
        target.atput0(index, value);
    }

    @Specialization(guards = {"!object.matches(frame)"})
    protected final void doFrameMarkerNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker object, final BlockClosureObject value) {
        final ContextObject target = object.getMaterializedContext();
        target.markEscaped();
        value.getHomeContext().markEscaped();
        target.atput0(index, value);
    }

    @Specialization(guards = {"object.matches(frame)", "!isContextObject(value)", "!isFrameMarker(value)", "!isBlockClosureObject(value)"})
    protected final void doFrameMarkerMatching(final VirtualFrame frame, final FrameMarker object, final Object value) {
        final ContextObject target = object.getMaterializedContext(frame);
        target.markEscaped();
        target.atput0(index, value);
    }

    @Specialization(guards = {"!object.matches(frame)", "!isContextObject(value)", "!isFrameMarker(value)", "!isBlockClosureObject(value)"})
    protected final void doFrameMarkerNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker object, final Object value) {
        final ContextObject target = object.getMaterializedContext();
        target.markEscaped();
        target.atput0(index, value);
    }

    @Specialization(guards = {"!isNativeObject(object)"})
    protected final void doSqueakObject(final VirtualFrame frame, final AbstractSqueakObject object, final BlockClosureObject value) {
        atPut0Node.execute(frame, object, index, value);
    }

    @Specialization(guards = {"!isNativeObject(object)", "!isContextObject(value)", "!isFrameMarker(value)", "!isBlockClosureObject(value)"})
    protected final void doSqueakObject(final VirtualFrame frame, final AbstractSqueakObject object, final Object value) {
        atPut0Node.execute(frame, object, index, value);
    }

    @Fallback
    protected final void doFail(final Object object, final Object value) {
        throw new SqueakException(object, "at:", index, "put:", value, "failed");
    }
}
