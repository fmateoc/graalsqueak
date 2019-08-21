package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodes.ContextObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodes.ContextObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public class ContextPrimitives extends AbstractPrimitiveFactoryHolder {

    @ImportStatic(CONTEXT.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 76)
    protected abstract static class PrimStoreStackPointerNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimStoreStackPointerNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"0 <= newStackPointer", "newStackPointer <= LARGE_FRAMESIZE"})
        protected static final ContextObject store(final ContextObject receiver, final long newStackPointer) {
            /**
             * Not need to "nil any newly accessible cells" as cells are always nil-initialized and
             * their values are cleared (overwritten with nil) on stack pop.
             */
            receiver.setStackPointer((int) newStackPointer);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 195)
    protected abstract static class PrimFindNextUnwindContextUpToNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        public PrimFindNextUnwindContextUpToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final AbstractSqueakObject doFindNext(final ContextObject receiver, final ContextObject previousContext) {
            return findNext(receiver, previousContext);
        }

        @Specialization
        protected static final AbstractSqueakObject doFindNextAvoidingMaterializationNil(final ContextObject receiver, final NilObject nil) {
            return findNext(receiver, nil);
        }

        private static AbstractSqueakObject findNext(final ContextObject receiver, final AbstractSqueakObject previousContextOrNil) {
            ContextObject current = receiver;
            while (current != previousContextOrNil) {
                final Object sender = current.getSender();
                if (sender == NilObject.SINGLETON || sender == previousContextOrNil) {
                    break;
                } else {
                    current = (ContextObject) sender;
                    if (current.getMethod().isUnwindMarked()) {
                        return current;
                    }
                }
            }
            return NilObject.SINGLETON;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 196)
    protected abstract static class PrimTerminateToNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        public PrimTerminateToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "hasSender(receiver, previousContext)")
        protected static final ContextObject doTerminateAllContexts(final ContextObject receiver, final ContextObject previousContext) {
            terminateTo(receiver, previousContext);
            return doTerminate(receiver, previousContext);
        }

        @Specialization(guards = "!hasSender(receiver, previousContext)")
        protected static final ContextObject doTerminate(final ContextObject receiver, final ContextObject previousContext) {
            receiver.setSender(previousContext);
            return receiver;
        }

        @Specialization
        protected static final ContextObject doTerminate(final ContextObject receiver, @SuppressWarnings("unused") final NilObject nil) {
            /* hasSender() always nil. */
            receiver.removeSender();
            return receiver;
        }

        protected static final boolean hasSender(final ContextObject context, final ContextObject other) {
            if (context == other) {
                return true;
            }
            AbstractSqueakObject s = context.getSender();
            while (s != NilObject.SINGLETON) {
                if (s == other) {
                    return true;
                }
                s = ((ContextObject) s).getSender();
            }
            return false;
        }

        private static void terminateTo(final ContextObject context, final ContextObject previousContext) {
            ContextObject currentContext = context.getNotNilSender();
            while (currentContext != previousContext) {
                final ContextObject sendingContext = currentContext.getNotNilSender();
                currentContext.terminate();
                currentContext = sendingContext;
            }
            context.setSender(previousContext);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 197)
    protected abstract static class PrimNextHandlerContextNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        protected PrimNextHandlerContextNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final AbstractSqueakObject findNext(final ContextObject receiver) {
            ContextObject context = receiver;
            while (true) {
                if (context.getMethod().isExceptionHandlerMarked()) {
                    return context;
                }
                final AbstractSqueakObject sender = context.getSender();
                if (sender == NilObject.SINGLETON) {
                    return NilObject.SINGLETON;
                } else {
                    context = (ContextObject) sender;
                }
            }
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(FrameAccess.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 210)
    protected abstract static class PrimContextAtNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimContextAtNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"index < receiver.getStackSize()"})
        protected static final Object doContextObject(final ContextObject receiver, final long index,
                        @Cached final ContextObjectReadNode readNode) {
            return readNode.execute(receiver, CONTEXT.TEMP_FRAME_START + index - 1);
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 211)
    protected abstract static class PrimContextAtPutNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimContextAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "index < receiver.getStackSize()")
        protected static final Object doContextObject(final ContextObject receiver, final long index, final Object value,
                        @Cached final ContextObjectWriteNode writeNode) {
            writeNode.execute(receiver, CONTEXT.TEMP_FRAME_START + index - 1, value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 212)
    protected abstract static class PrimContextSizeNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimContextSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "receiver.hasTruffleFrame()")
        protected static final long doSize(final ContextObject receiver) {
            return receiver.getStackPointer();
        }

        @Specialization(guards = "!receiver.hasTruffleFrame()")
        protected static final long doSizeWithoutFrame(final ContextObject receiver) {
            return receiver.size() - receiver.instsize();
        }
    }

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ContextPrimitivesFactory.getFactories();
    }
}
