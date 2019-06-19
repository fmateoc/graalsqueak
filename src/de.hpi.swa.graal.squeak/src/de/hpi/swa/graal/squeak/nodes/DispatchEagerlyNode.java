package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.nodes.context.frame.CreateEagerArgumentsNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@NodeInfo(cost = NodeCost.NONE)
public abstract class DispatchEagerlyNode extends AbstractNode {
    protected static final int PRIMITIVE_CACHE_SIZE = 1;
    protected static final int INLINE_CACHE_SIZE = 3;

    public static DispatchEagerlyNode create() {
        return DispatchEagerlyNodeGen.create();
    }

    public abstract Object executeDispatch(VirtualFrame frame, CompiledMethodObject method, Object[] receiverAndArguments, Object contextOrMarker);

    @SuppressWarnings("unused")
    @Specialization(guards = {"method.hasPrimitive()", "primitiveNode != null", "method == primitiveNode.getMethod()"}, //
                    limit = "PRIMITIVE_CACHE_SIZE", assumptions = {"callTargetStable"}, rewriteOn = PrimitiveFailed.class)
    protected static final Object doPrimitiveEagerly(final VirtualFrame frame, final CompiledMethodObject method, final Object[] receiverAndArguments, final Object contextOrMarker,
                    @Cached("method.getCallTargetStable()") final Assumption callTargetStable,
                    @Cached("method.image.primitiveNodeFactory.forIndex(method, method.primitiveIndex())") final AbstractPrimitiveNode primitiveNode,
                    @Cached final CreateEagerArgumentsNode createEagerArgumentsNode) {
        return primitiveNode.executeWithArguments(frame, createEagerArgumentsNode.executeCreate(primitiveNode.getNumArguments(), receiverAndArguments));
    }

    @Specialization(guards = {"method.getCallTarget() == cachedTarget"}, //
                    limit = "INLINE_CACHE_SIZE", assumptions = "callTargetStable")
    protected static final Object doDirect(final CompiledMethodObject method, final Object[] receiverAndArguments, final Object contextOrMarker,
                    @SuppressWarnings("unused") @Cached("method.getCallTargetStable()") final Assumption callTargetStable,
                    @SuppressWarnings("unused") @Cached("method.getCallTarget()") final RootCallTarget cachedTarget,
                    @Cached("create(cachedTarget)") final DirectCallNode callNode) {
        return callNode.call(FrameAccess.newWith(method, contextOrMarker, null, receiverAndArguments));
    }

    @Specialization(replaces = {"doPrimitiveEagerly", "doDirect"})
    protected static final Object doIndirect(final CompiledMethodObject method, final Object[] receiverAndArguments, final Object contextOrMarker,
                    @Cached final IndirectCallNode callNode) {
        return callNode.call(method.getCallTarget(), FrameAccess.newWith(method, contextOrMarker, null, receiverAndArguments));
    }
}
