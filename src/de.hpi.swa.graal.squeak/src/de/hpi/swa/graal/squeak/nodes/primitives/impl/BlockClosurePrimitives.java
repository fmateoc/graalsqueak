package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.nodes.BlockActivationNode;
import de.hpi.swa.graal.squeak.nodes.GetBlockFrameArgumentsNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.GetObjectArrayNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectSizeNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuaternaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;

public final class BlockClosurePrimitives extends AbstractPrimitiveFactoryHolder {

    private abstract static class AbstractClosureValuePrimitiveNode extends AbstractPrimitiveNode {
        @Child protected BlockActivationNode dispatch = BlockActivationNode.create();
        @Child protected GetBlockFrameArgumentsNode getFrameArguments = GetBlockFrameArgumentsNode.create();

        protected AbstractClosureValuePrimitiveNode(final CompiledMethodObject method) {
            super(method);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 200)
    public abstract static class PrimClosureCopyWithCopiedValuesNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimClosureCopyWithCopiedValuesNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected static final Object doCopy(final VirtualFrame frame, final ContextObject outerContext, final long numArgs, final ArrayObject copiedValues) {
            throw new SqueakException("Not implemented and not used in Squeak anymore");
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 201)
    public abstract static class PrimClosureValue0Node extends AbstractClosureValuePrimitiveNode implements UnaryPrimitive {

        protected PrimClosureValue0Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == 0"})
        protected final Object doClosure(final VirtualFrame frame, final BlockClosureObject block) {
            return dispatch.executeBlock(block, getFrameArguments.execute(block, getContextOrMarker(frame), new Object[0]));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 202)
    protected abstract static class PrimClosureValue1Node extends AbstractClosureValuePrimitiveNode implements BinaryPrimitive {

        protected PrimClosureValue1Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == 1"})
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject block, final Object arg) {
            return dispatch.executeBlock(block, getFrameArguments.execute(block, getContextOrMarker(frame), new Object[]{arg}));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 203)
    protected abstract static class PrimClosureValue2Node extends AbstractClosureValuePrimitiveNode implements TernaryPrimitive {

        protected PrimClosureValue2Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == 2"})
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject block, final Object arg1, final Object arg2) {
            return dispatch.executeBlock(block, getFrameArguments.execute(block, getContextOrMarker(frame), new Object[]{arg1, arg2}));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 204)
    protected abstract static class PrimClosureValue3Node extends AbstractClosureValuePrimitiveNode implements QuaternaryPrimitive {

        protected PrimClosureValue3Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == 3"})
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject block, final Object arg1, final Object arg2, final Object arg3) {
            return dispatch.executeBlock(block, getFrameArguments.execute(block, getContextOrMarker(frame), new Object[]{arg1, arg2, arg3}));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 205)
    protected abstract static class PrimClosureValue4Node extends AbstractClosureValuePrimitiveNode implements QuinaryPrimitive {

        protected PrimClosureValue4Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == 4"})
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject block, final Object arg1, final Object arg2, final Object arg3, final Object arg4) {
            return dispatch.executeBlock(block, getFrameArguments.execute(block, getContextOrMarker(frame), new Object[]{arg1, arg2, arg3, arg4}));
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 206)
    protected abstract static class PrimClosureValueAryNode extends AbstractClosureValuePrimitiveNode implements BinaryPrimitive {

        protected PrimClosureValueAryNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == sizeNode.execute(argArray)"}, limit = "1")
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject block, final ArrayObject argArray,
                        @SuppressWarnings("unused") @Cached("create()") final SqueakObjectSizeNode sizeNode,
                        @Cached("create()") final GetObjectArrayNode getObjectArrayNode) {
            return dispatch.executeBlock(block, getFrameArguments.execute(block, getContextOrMarker(frame), getObjectArrayNode.execute(argArray)));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 221)
    public abstract static class PrimClosureValueNoContextSwitchNode extends AbstractClosureValuePrimitiveNode implements UnaryPrimitive {
        private static final Object[] noArgument = new Object[0];

        protected PrimClosureValueNoContextSwitchNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == 0"})
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject block) {
            return code.image.runWithoutInterrupts(() -> dispatch.executeBlock(block,
                            getFrameArguments.execute(block, getContextOrMarker(frame), noArgument)));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 222)
    protected abstract static class PrimClosureValueAryNoContextSwitchNode extends AbstractClosureValuePrimitiveNode implements BinaryPrimitive {

        protected PrimClosureValueAryNoContextSwitchNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"block.getCompiledBlock().getNumArgs() == sizeNode.execute(argArray)"}, limit = "1")
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject block, final ArrayObject argArray,
                        @SuppressWarnings("unused") @Cached("create()") final SqueakObjectSizeNode sizeNode,
                        @Cached("create()") final GetObjectArrayNode getObjectArrayNode) {
            final Object[] arguments = getObjectArrayNode.execute(argArray);
            return code.image.runWithoutInterrupts(() -> dispatch.executeBlock(block,
                            getFrameArguments.execute(block, getContextOrMarker(frame), arguments)));
        }
    }

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return BlockClosurePrimitivesFactory.getFactories();
    }
}
