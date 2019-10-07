/*
 * Copyright (c) 2017-2019 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.NotProvided;
import de.hpi.swa.graal.squeak.nodes.DispatchBlockNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectToObjectArrayCopyNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectSizeNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuaternaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.SenaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.impl.ControlPrimitives.PrimRelinquishProcessorNode;

public final class BlockClosurePrimitives extends AbstractPrimitiveFactoryHolder {
    protected static final int BLOCK_CACHE_LIMIT = 1;

    @GenerateNodeFactory
    @SqueakPrimitive(indices = {201, 221})
    @ImportStatic(BlockClosurePrimitives.class)
    public abstract static class PrimClosureValue0Node extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimClosureValue0Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"cachedBlock.getNumArgs() == 0", "closure.getCompiledBlock() == cachedBlock"}, limit = "BLOCK_CACHE_LIMIT")
        protected final Object doValueCached(final VirtualFrame frame, final BlockClosureObject closure,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure0(closure, cachedBlock, getContextOrMarker(frame));
        }

        @Specialization(guards = {"closure.getCompiledBlock().getNumArgs() == 0"}, replaces = "doValueCached")
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject closure,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure0(closure, closure.getCompiledBlock(), getContextOrMarker(frame));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 202)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValue1Node extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimClosureValue1Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"cachedBlock.getNumArgs() == 1", "closure.getCompiledBlock() == cachedBlock"}, limit = "BLOCK_CACHE_LIMIT")
        protected final Object doValueCached(final VirtualFrame frame, final BlockClosureObject closure, final Object arg,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure1(closure, cachedBlock, getContextOrMarker(frame), arg);
        }

        @Specialization(guards = {"closure.getCompiledBlock().getNumArgs() == 1"}, replaces = "doValueCached")
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject closure, final Object arg,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure1(closure, closure.getCompiledBlock(), getContextOrMarker(frame), arg);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 203)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValue2Node extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimClosureValue2Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"cachedBlock.getNumArgs() == 2", "closure.getCompiledBlock() == cachedBlock"}, limit = "BLOCK_CACHE_LIMIT")
        protected final Object doValueCached(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure2(closure, cachedBlock, getContextOrMarker(frame), arg1, arg2);
        }

        @Specialization(guards = {"closure.getCompiledBlock().getNumArgs() == 2"}, replaces = "doValueCached")
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure2(closure, closure.getCompiledBlock(), getContextOrMarker(frame), arg1, arg2);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 204)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValue3Node extends AbstractPrimitiveNode implements QuaternaryPrimitive {

        protected PrimClosureValue3Node(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"cachedBlock.getNumArgs() == 3", "closure.getCompiledBlock() == cachedBlock"}, limit = "BLOCK_CACHE_LIMIT")
        protected final Object doValueCached(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure3(closure, cachedBlock, getContextOrMarker(frame), arg1, arg2, arg3);
        }

        @Specialization(guards = {"closure.getCompiledBlock().getNumArgs() == 3"}, replaces = "doValueCached")
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure3(closure, closure.getCompiledBlock(), getContextOrMarker(frame), arg1, arg2, arg3);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 205)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValueNode extends AbstractPrimitiveNode implements SenaryPrimitive {

        protected PrimClosureValueNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedBlock.getNumArgs() == 4", "closure.getCompiledBlock() == cachedBlock"}, limit = "BLOCK_CACHE_LIMIT")
        protected final Object doValue4Cached(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4,
                        final NotProvided arg5,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure4(closure, cachedBlock, getContextOrMarker(frame), arg1, arg2, arg3, arg4);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock().getNumArgs() == 4"}, replaces = "doValue4Cached")
        protected final Object doValue4(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4, final NotProvided arg5,
                        @Shared("dispatchNode") @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure4(closure, closure.getCompiledBlock(), getContextOrMarker(frame), arg1, arg2, arg3, arg4);
        }

        @Specialization(guards = {"cachedBlock.getNumArgs() == 5", "closure.getCompiledBlock() == cachedBlock", "!isNotProvided(arg5)"}, limit = "BLOCK_CACHE_LIMIT")
        protected final Object doValue5Cached(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure5(closure, cachedBlock, getContextOrMarker(frame), arg1, arg2, arg3, arg4, arg5);
        }

        @Specialization(guards = {"closure.getCompiledBlock().getNumArgs() == 5", "!isNotProvided(arg5)"}, replaces = "doValue5Cached")
        protected final Object doValue5(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5,
                        @Shared("dispatchNode") @Cached final DispatchBlockNode dispatchNode) {
            return dispatchNode.executeClosure5(closure, closure.getCompiledBlock(), getContextOrMarker(frame), arg1, arg2, arg3, arg4, arg5);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = {206, 222})
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValueAryNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimClosureValueAryNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"cachedBlock.getNumArgs() == sizeNode.execute(argArray)", "closure.getCompiledBlock() == cachedBlock"}, limit = "BLOCK_CACHE_LIMIT")
        protected final Object doValueCached(final VirtualFrame frame, final BlockClosureObject closure, final ArrayObject argArray,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @SuppressWarnings("unused") @Cached final SqueakObjectSizeNode sizeNode,
                        @Cached final DispatchBlockNode dispatchNode,
                        @Cached final ArrayObjectToObjectArrayCopyNode getObjectArrayNode) {
            return dispatchNode.executeClosureN(closure, cachedBlock, getContextOrMarker(frame), getObjectArrayNode.execute(argArray));
        }

        @Specialization(guards = {"closure.getCompiledBlock().getNumArgs() == sizeNode.execute(argArray)"}, replaces = "doValueCached")
        protected final Object doValue(final VirtualFrame frame, final BlockClosureObject closure, final ArrayObject argArray,
                        @SuppressWarnings("unused") @Cached final SqueakObjectSizeNode sizeNode,
                        @Cached final DispatchBlockNode dispatchNode,
                        @Cached final ArrayObjectToObjectArrayCopyNode getObjectArrayNode) {
            return dispatchNode.executeClosureN(closure, closure.getCompiledBlock(), getContextOrMarker(frame), getObjectArrayNode.execute(argArray));
        }
    }

    /**
     * Non-context-switching closureValue primitives (#221 and #222) are not in use because
     * interrupt checks only happen in the idleProcess (see {@link PrimRelinquishProcessorNode}).
     * Using standard closureValue primitives instead.
     */

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return BlockClosurePrimitivesFactory.getFactories();
    }
}
