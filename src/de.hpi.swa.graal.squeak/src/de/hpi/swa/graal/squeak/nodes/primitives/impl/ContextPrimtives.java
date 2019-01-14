package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public class ContextPrimtives extends AbstractPrimitiveFactoryHolder {

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 76)
    protected abstract static class PrimStoreStackPointerNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimStoreStackPointerNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final ContextObject store(final ContextObject receiver, final long value) {
            receiver.setStackPointer(value);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 195)
    protected abstract static class PrimFindNextUnwindContextUpToNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        public PrimFindNextUnwindContextUpToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "receiver.hasMaterializedSender()")
        protected final Object doFindNext(final ContextObject receiver, final AbstractSqueakObject previousContextOrNil) {
            ContextObject current = receiver;
            while (current != previousContextOrNil) {
                final Object sender = current.getSender();
                if (sender == code.image.nil || sender == previousContextOrNil) {
                    break;
                } else {
                    current = (ContextObject) sender;
                    if (current.isUnwindContext()) {
                        return current;
                    }
                }
            }
            return code.image.nil;
        }

        @Specialization(guards = "!receiver.hasMaterializedSender()")
        protected final Object doFindNextAvoidingMaterialization(final ContextObject receiver, final AbstractSqueakObject previousContextOrNil) {
            // Sender is not materialized, so avoid materialization by walking Truffle frames.
            final Object result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
                boolean foundMyself = false;

                public Object visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (!FrameAccess.isGraalSqueakFrame(current)) {
                        return null;
                    }
                    final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                    if (!foundMyself) {
                        if (contextOrMarker == receiver || contextOrMarker == receiver.getFrameMarker()) {
                            foundMyself = true;
                        }
                    } else {
                        if (contextOrMarker == code.image.nil || contextOrMarker == previousContextOrNil) {
                            return code.image.nil;
                        } else if (FrameAccess.getMethod(current).isUnwindMarked()) {
                            return FrameAccess.returnContextObject(contextOrMarker, frameInstance);
                        }
                    }
                    return null;
                }
            });
            assert result != null;
            return result;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 196)
    protected abstract static class PrimTerminateToNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        public PrimTerminateToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doUnwindAndTerminate(final ContextObject receiver, final ContextObject previousContext) {
            /*
             * Terminate all the Contexts between me and previousContext, if previousContext is on
             * my Context stack. Make previousContext my sender.
             */
            terminateBetween(receiver, previousContext);
            receiver.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        @Specialization
        protected static final Object doTerminate(final ContextObject receiver, final NilObject nil) {
            receiver.atput0(CONTEXT.SENDER_OR_NIL, nil); // flagging context as dirty
            return receiver;
        }

        private void terminateBetween(final ContextObject start, final ContextObject end) {
            ContextObject current = start;
            while (current.hasMaterializedSender()) {
                final AbstractSqueakObject sender = start.getSender();
                current.terminate();
                if (sender == code.image.nil || sender == end) {
                    return;
                } else {
                    current = (ContextObject) sender;
                }
            }
            terminateBetween(current.getFrameMarker(), end);
        }

        private void terminateBetween(final FrameMarker start, final ContextObject end) {
            assert start != null;
            final ContextObject[] bottomContextOnTruffleStack = new ContextObject[1];
            final ContextObject result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<ContextObject>() {
                boolean foundMyself = false;

                @Override
                public ContextObject visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (!FrameAccess.isGraalSqueakFrame(current)) {
                        return null;
                    }
                    final CompiledCodeObject currentCode = FrameAccess.getMethod(current);
                    final Object contextOrMarker = current.getValue(currentCode.thisContextOrMarkerSlot);
                    if (!foundMyself) {
                        if (FrameAccess.matchesContextOrMarker(start, contextOrMarker)) {
                            foundMyself = true;
                        }
                    } else {
                        if (contextOrMarker == end) {
                            return end;
                        }
                        if (contextOrMarker instanceof ContextObject) {
                            bottomContextOnTruffleStack[0] = (ContextObject) contextOrMarker;
                        } else {
                            bottomContextOnTruffleStack[0] = null;
                        }
                        final Frame currentWritable = frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
                        // Terminate frame
                        currentWritable.setInt(currentCode.instructionPointerSlot, -1);
                        currentWritable.getArguments()[FrameAccess.SENDER_OR_SENDER_MARKER] = code.image.nil;
                    }
                    return null;
                }
            });
            if (result == null && bottomContextOnTruffleStack[0] != null) {
                terminateBetween(bottomContextOnTruffleStack[0], end);
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 197)
    protected abstract static class PrimNextHandlerContextNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        protected PrimNextHandlerContextNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.hasMaterializedSender()"})
        protected final Object findNext(final ContextObject receiver) {
            ContextObject context = receiver;
            while (true) {
                if (context.getMethod().isExceptionHandlerMarked()) {
                    return context;
                }
                final AbstractSqueakObject sender = context.getSender();
                if (sender instanceof ContextObject) {
                    context = (ContextObject) sender;
                } else {
                    assert sender == code.image.nil;
                    return code.image.nil;
                }
            }
        }

        @Specialization(guards = {"!receiver.hasMaterializedSender()"})
        protected final Object findNextAvoidingMaterialization(final ContextObject receiver) {
            final boolean[] foundMyself = new boolean[1];
            final Object[] lastSender = new Object[1];
            final Object result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {

                public Object visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (!FrameAccess.isGraalSqueakFrame(current)) {
                        return null;
                    }
                    final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                    if (!foundMyself[0]) {
                        if (contextOrMarker == receiver || contextOrMarker == receiver.getFrameMarker()) {
                            foundMyself[0] = true;
                        }
                    } else {
                        if (FrameAccess.getMethod(current).isExceptionHandlerMarked()) {
                            assert FrameAccess.getClosure(current) == null : "Context with closure cannot be exception handler";
                            return FrameAccess.returnContextObject(contextOrMarker, frameInstance);
                        } else {
                            lastSender[0] = FrameAccess.getSender(current);
                        }
                    }
                    return null;
                }
            });
            if (result == null) {
                if (!foundMyself[0]) {
                    return findNext(receiver); // Fallback to other version.
                }
                if (lastSender[0] instanceof ContextObject) {
                    return findNext((ContextObject) lastSender[0]);
                } else {
                    return code.image.nil;
                }
            } else {
                return result;
            }
        }
    }

    @ImportStatic(FrameAccess.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 210)
    protected abstract static class PrimContextAtNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimContextAtNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"index < receiver.getStackSize()"})
        protected static final Object doContextObject(final ContextObject receiver, final long index) {
            return receiver.atTemp(index - 1);
        }
    }

    @ImportStatic(FrameAccess.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 211)
    protected abstract static class PrimContextAtPutNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        protected PrimContextAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "index < receiver.getStackSize()")
        protected static final Object doContextObject(final ContextObject receiver, final long index, final Object value) {
            receiver.atTempPut(index - 1, value);
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
            return FrameAccess.getStackPointer(receiver.getTruffleFrame());
        }

        @Specialization(guards = "!receiver.hasTruffleFrame()")
        protected static final long doSizeWithoutFrame(final ContextObject receiver) {
            return receiver.size() - receiver.instsize();
        }

    }

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ContextPrimtivesFactory.getFactories();
    }
}
