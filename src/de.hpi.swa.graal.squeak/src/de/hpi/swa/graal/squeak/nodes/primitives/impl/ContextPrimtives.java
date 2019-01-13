package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodes.ContextObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodes.ContextObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public class ContextPrimtives extends AbstractPrimitiveFactoryHolder {

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 395)
    protected abstract static class PrimFindNextUnwindContextUpToNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @Child private GetOrCreateContextNode contextNode;

        public PrimFindNextUnwindContextUpToNode(final CompiledMethodObject method) {
            super(method);
            contextNode = GetOrCreateContextNode.create(method);
        }

        @Specialization(guards = "!receiver.hasFrameMarker()")
        protected final Object doFindNextMaterialized(final ContextObject receiver, final AbstractSqueakObject previousContextOrNil) {
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

        @Specialization(guards = "receiver.hasFrameMarker()")
        protected final Object doFindNextVirtualized(final ContextObject receiver, final ContextObject previousContext) {
            return doFindNextMarkers(receiver.getFrameMarker(), previousContext.getFrameMarker());
        }

        @Specialization(guards = "receiver.hasFrameMarker()")
        protected final Object doFindNextVirtualizedNil(final ContextObject receiver, @SuppressWarnings("unused") final NilObject nil) {
            return doFindNextMarkers(receiver.getFrameMarker(), null);
        }

        @Specialization
        protected final Object previousMarker(final ContextObject receiver, final FrameMarker previousMarker) {
            return doFindNextMarkers(receiver.getFrameMarker(), previousMarker);
        }

        @Specialization
        protected final Object doFindNext(final FrameMarker receiver, final ContextObject previousContext) {
            return doFindNextMarkers(receiver, previousContext.getFrameMarker());
        }

        @Specialization
        protected final Object doFindNext(final FrameMarker receiver, @SuppressWarnings("unused") final NilObject nil) {
            return doFindNextMarkers(receiver, null);
        }

        @Specialization
        protected final Object doFindNextMarkers(final FrameMarker receiver, final FrameMarker previousMarker) {
            final ContextObject[] bottomContextOnTruffleStack = new ContextObject[1];
            final Object handlerContext = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
                boolean foundMyself = false;

                @Override
                public Object visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (!FrameAccess.isGraalSqueakFrame(current)) {
                        return null;
                    }
                    final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                    if (!foundMyself) {
                        if (receiver.matchesContextOrMarker(contextOrMarker)) {
                            foundMyself = true;
                        }
                    } else {
                        if (previousMarker != null && previousMarker.matchesContextOrMarker(contextOrMarker)) {
                            return code.image.nil;
                        }
                        if (FrameAccess.getMethod(current).isUnwindMarked()) {
                            return FrameAccess.returnMarkerOrContext(contextOrMarker, frameInstance);
                        }
                        if (contextOrMarker instanceof ContextObject) {
                            bottomContextOnTruffleStack[0] = (ContextObject) contextOrMarker;
                        } else { // Unset otherwise
                            assert contextOrMarker instanceof FrameMarker;
                            bottomContextOnTruffleStack[0] = null;
                        }
                    }
                    return null;
                }
            });
            // Continue search in materialized contexts if necessary.
            if (handlerContext == null) {
                ContextObject current = bottomContextOnTruffleStack[0];
                if (current == null) {
                    return code.image.nil;
                }
                while (current.getFrameMarker() != previousMarker) {
                    final Object sender = current.getSender();
                    if (sender == code.image.nil || sender == previousMarker) {
                        break;
                    } else {
                        current = (ContextObject) sender;
                        if (current.getFrameMarker() == previousMarker) {
                            return code.image.nil;
                        } else if (current.isUnwindContext()) {
                            return current;
                        }
                    }
                }
                return code.image.nil;
            } else {
                return handlerContext;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 396)
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

        @Specialization(guards = {"receiver.matches(frame)"})
        protected final Object doTerminateMatchingReceiver(final VirtualFrame frame, final FrameMarker receiver, final FrameMarker previousMarker) {
            terminateBetween(receiver, previousMarker);
            final ContextObject previousContext = previousMarker.getMaterializedContext();
            final ContextObject context = receiver.getMaterializedContext(frame);
            context.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        @Specialization(guards = {"previousMarker.matches(frame)"})
        protected final Object doTerminateMatchingPreviousMarker(final VirtualFrame frame, final FrameMarker receiver, final FrameMarker previousMarker) {
            terminateBetween(receiver, previousMarker);
            final ContextObject previousContext = previousMarker.getMaterializedContext(frame);
            final ContextObject context = receiver.getMaterializedContext();
            context.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        @Specialization(guards = {"!receiver.matches(frame)", "!previousMarker.matches(frame)"})
        protected final Object doTerminateNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker receiver, final FrameMarker previousMarker) {
            terminateBetween(receiver, previousMarker);
            final ContextObject previousContext = previousMarker.getMaterializedContext();
            final ContextObject context = receiver.getMaterializedContext();
            context.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        @Specialization(guards = {"previousMarker.matches(frame)"})
        protected final Object doTerminateMatching(final VirtualFrame frame, final ContextObject receiver, final FrameMarker previousMarker) {
            terminateBetween(receiver.getFrameMarker(), previousMarker);
            final ContextObject previousContext = previousMarker.getMaterializedContext(frame);
            receiver.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        @Specialization(guards = {"!previousMarker.matches(frame)"})
        protected final Object doTerminateNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final ContextObject receiver, final FrameMarker previousMarker) {
            terminateBetween(receiver.getFrameMarker(), previousMarker);
            final ContextObject previousContext = previousMarker.getMaterializedContext();
            receiver.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        @Specialization(guards = {"receiver.matches(frame)"})
        protected final Object doTerminateMatching(final VirtualFrame frame, final FrameMarker receiver, final ContextObject previousContext) {
            terminateBetween(receiver, previousContext.getFrameMarker());
            final ContextObject context = receiver.getMaterializedContext(frame);
            context.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        @Specialization(guards = {"!receiver.matches(frame)"})
        protected final Object doTerminateNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker receiver, final ContextObject previousContext) {
            terminateBetween(receiver, previousContext.getFrameMarker());
            final ContextObject context = receiver.getMaterializedContext();
            context.atput0(CONTEXT.SENDER_OR_NIL, previousContext); // flagging context as dirty
            return receiver;
        }

        private void terminateBetween(final ContextObject start, final ContextObject end) {
            ContextObject current = start;
            while (current.hasMaterializedSender()) {
                final Object sender = start.getSender();
                current.terminate();
                if (sender == code.image.nil || sender == end) {
                    return;
                } else if (sender instanceof FrameMarker) {
                    throw new SqueakException("Not yet supported"); // FIXME
                } else {
                    current = (ContextObject) sender;
                }
            }
            terminateBetween(current.getFrameMarker(), end);
// throw new SqueakException("virtual sender not yet supported"); // FIXME
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

        private void terminateBetween(final FrameMarker start, final FrameMarker end) {
            assert start != null && end != null;
            final Object result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
                boolean foundMyself = false;

                @Override
                public Object visitFrame(final FrameInstance frameInstance) {
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
                        if (FrameAccess.matchesContextOrMarker(end, contextOrMarker)) {
                            return contextOrMarker;
                        } else {
                            final Frame currentWritable = frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
                            // Terminate frame
                            currentWritable.setInt(currentCode.instructionPointerSlot, -1);
                            currentWritable.getArguments()[FrameAccess.SENDER_OR_SENDER_MARKER] = code.image.nil;
                        }
                    }
                    return null;
                }
            });
            assert result != null : "did not terminate anything";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 397)
    protected abstract static class PrimNextHandlerContextNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private GetOrCreateContextNode contextNode;

        protected PrimNextHandlerContextNode(final CompiledMethodObject method) {
            super(method);
            contextNode = GetOrCreateContextNode.create(code);
        }

        @Specialization
        protected final Object findNext(final ContextObject receiver) {
            return findNext(receiver.getFrameMarker());
        }

        @Specialization
        protected final Object findNext(final FrameMarker receiver) {
            final Object result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
                boolean foundMyself = false;

                public Object visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (!FrameAccess.isGraalSqueakFrame(current)) {
                        return null;
                    }
                    final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                    if (!foundMyself) {
                        if (receiver.matchesContextOrMarker(contextOrMarker)) {
                            foundMyself = true;
                        }
                    } else {
                        if (FrameAccess.getMethod(current).isExceptionHandlerMarked()) {
                            assert FrameAccess.getClosure(current) == null : "Context with closure cannot be exception handler";
                            return FrameAccess.returnMarkerOrContext(contextOrMarker, frameInstance);
                        }
                    }
                    return null;
                }
            });
            return result != null ? result : code.image.nil;
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

        @Specialization(guards = {"index < getStackSize(frame)", "receiver.matches(frame)"})
        protected static final Object doFrameMarkerMatching(final VirtualFrame frame, final FrameMarker receiver, final long index,
                        @Cached("create()") final ContextObjectReadNode readNode) {
            return readNode.execute(frame, receiver, CONTEXT.TEMP_FRAME_START + index - 1);
        }

        @Specialization(guards = {"index < getStackSize(frame)", "!receiver.matches(frame)"})
        protected static final Object doFrameMarkerNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker receiver, final long index,
                        @Cached("create()") final ContextObjectReadNode readNode) {
            final Object result = Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null;
                }
                final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                if (receiver == contextOrMarker) {
                    return readNode.execute(current, receiver, CONTEXT.TEMP_FRAME_START + index - 1);
                } else if (contextOrMarker instanceof ContextObject && receiver == ((ContextObject) contextOrMarker).getFrameMarker()) {
                    return ((ContextObject) contextOrMarker).atTemp(index - 1);
                }
                return null;
            });
            if (result == null) {
                throw new SqueakException("Unable to find frameMarker:", receiver);
            }
            return result;
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

        @Specialization(guards = {"index < getStackSize(frame)", "receiver.matches(frame)"})
        protected static final Object doFrameMarkerMatching(final VirtualFrame frame, final FrameMarker receiver, final long index, final Object value,
                        @Cached("create()") final ContextObjectWriteNode writeNode) {
            writeNode.execute(frame, receiver, CONTEXT.TEMP_FRAME_START + index - 1, value);
            return value;
        }

        @Specialization(guards = {"index < getStackSize(frame)", "!receiver.matches(frame)"})
        protected static final Object doFrameMarkerNotMatching(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker receiver, final long index, final Object value,
                        @Cached("create()") final ContextObjectWriteNode writeNode) {
            final Object result = Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null;
                }
                final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                if (receiver == contextOrMarker) {
                    final Frame currentWritable = frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
                    writeNode.execute(currentWritable, receiver, CONTEXT.TEMP_FRAME_START + index - 1, value);
                    return value;
                } else if (contextOrMarker instanceof ContextObject && receiver == ((ContextObject) contextOrMarker).getFrameMarker()) {
                    ((ContextObject) contextOrMarker).atTemp(index - 1);
                    return value;
                } else {
                    return null;
                }
            });
            if (result != value) {
                throw new SqueakException("Unable to find frameMarker:", receiver);
            }
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

        @Specialization
        protected static final long doSizeMarker(final FrameMarker receiver) {
            final Long result = Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null;
                }
                final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                if (receiver.matchesContextOrMarker(contextOrMarker)) {
                    return (long) FrameAccess.getStackPointer(current);
                } else {
                    return null;
                }
            });
            if (result == null) {
                throw new SqueakException("Unable to find frameMarker:", receiver);
            }
            return result;
        }
    }

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ContextPrimtivesFactory.getFactories();
    }
}
