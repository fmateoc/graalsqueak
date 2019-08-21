package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.nodes.context.frame.CreateEagerArgumentsNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@ReportPolymorphism
@NodeInfo(cost = NodeCost.NONE)
public abstract class DispatchEagerlyNode extends AbstractNodeWithCode {
    protected static final int INLINE_CACHE_SIZE = 6;

    protected DispatchEagerlyNode(final CompiledCodeObject code) {
        super(code);
    }

    public static DispatchEagerlyNode create(final CompiledCodeObject code) {
        return DispatchEagerlyNodeGen.create(code);
    }

    public abstract Object executeDispatch(VirtualFrame frame, CompiledMethodObject method, Object[] receiverAndArguments);

    @Specialization(guards = {"method.hasPrimitive()", "method == cachedMethod", "primitiveNode != null"}, //
                    limit = "INLINE_CACHE_SIZE", assumptions = {"cachedMethod.getCallTargetStable()"}, rewriteOn = PrimitiveFailed.class)
    protected static final Object doPrimitiveEagerly(final VirtualFrame frame, @SuppressWarnings("unused") final CompiledMethodObject method, final Object[] receiverAndArguments,
                    @SuppressWarnings("unused") @Cached("method") final CompiledMethodObject cachedMethod,
                    @Cached("cachedMethod.image.primitiveNodeFactory.forIndex(cachedMethod, cachedMethod.primitiveIndex())") final AbstractPrimitiveNode primitiveNode,
                    @Cached final CreateEagerArgumentsNode createEagerArgumentsNode) {
        return primitiveNode.executeWithArguments(frame, createEagerArgumentsNode.executeCreate(primitiveNode.getNumArguments(), receiverAndArguments));
    }

    @Specialization(guards = {"method == cachedMethod"}, //
                    limit = "INLINE_CACHE_SIZE", assumptions = {"cachedMethod.getCallTargetStable()", "cachedMethod.getDoesNotNeedSenderAssumption()"}, replaces = "doPrimitiveEagerly")
    protected static final Object doDirect(final VirtualFrame frame, @SuppressWarnings("unused") final CompiledMethodObject method, final Object[] receiverAndArguments,
                    @SuppressWarnings("unused") @Cached("method") final CompiledMethodObject cachedMethod,
                    @Cached("create(code)") final SenderForDispatchNode senderNode,
                    @Cached("create(cachedMethod.getCallTarget())") final DirectCallNode callNode) {
        return callNode.call(FrameAccess.newWith(cachedMethod, senderNode.execute(frame, cachedMethod), null, receiverAndArguments));
    }

    @Specialization(replaces = {"doDirect"})
    protected static final Object doIndirect(final VirtualFrame frame, final CompiledMethodObject method, final Object[] receiverAndArguments,
                    @Cached("create(code)") final SenderForDispatchNode senderNode,
                    @Cached final IndirectCallNode callNode) {
        return callNode.call(method.getCallTarget(), FrameAccess.newWith(method, senderNode.execute(frame, method), null, receiverAndArguments));
    }
}
