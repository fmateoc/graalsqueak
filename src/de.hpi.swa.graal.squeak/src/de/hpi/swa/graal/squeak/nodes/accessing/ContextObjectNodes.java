package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodesFactory.ContextObjectReadNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodesFactory.ContextObjectWriteNodeGen;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class ContextObjectNodes {

    @ImportStatic(CONTEXT.class)
    public abstract static class ContextObjectReadNode extends Node {

        public static ContextObjectReadNode create() {
            return ContextObjectReadNodeGen.create();
        }

        public abstract Object execute(Frame frame, FrameMarker obj, long index);

        @Specialization(guards = {"!obj.matches(frame)", "index == SENDER_OR_NIL"})
        protected static final Object doSender(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return doSenderMatching(getReadableFrame(obj), obj, index);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == SENDER_OR_NIL"})
        protected static final Object doSenderMatching(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getSender(frame);
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == INSTRUCTION_POINTER"})
        protected static final Object doPC(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return doPCMatching(getReadableFrame(obj), obj, index);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == INSTRUCTION_POINTER"})
        protected static final Object doPCMatching(final Frame frame, final FrameMarker obj, final long index) {
            final CompiledCodeObject blockOrMethod = FrameAccess.getMethod(frame);
            final int pc = FrameUtil.getIntSafe(frame, blockOrMethod.instructionPointerSlot);
            if (pc < 0) {
                return blockOrMethod.image.nil;
            } else {
                final int initalPC;
                if (blockOrMethod instanceof CompiledBlockObject) {
                    initalPC = ((CompiledBlockObject) blockOrMethod).getInitialPC();
                } else {
                    initalPC = ((CompiledMethodObject) blockOrMethod).getInitialPC();
                }
                return (long) (initalPC + pc);
            }
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == STACKPOINTER"})
        protected static final Object doSP(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return doSPMatching(getReadableFrame(obj), obj, index);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == STACKPOINTER"})
        protected static final long doSPMatching(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getStackPointer(frame);
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == METHOD"})
        protected static final Object doMethod(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return doMethodMatching(getReadableFrame(obj), obj, index);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == METHOD"})
        protected static final CompiledCodeObject doMethodMatching(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getMethod(frame);
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final Object doClosure(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return doClosureMatching(getReadableFrame(obj), obj, index);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final Object doClosureMatching(final Frame frame, final FrameMarker obj, final long index) {
            final BlockClosureObject closure = FrameAccess.getClosure(frame);
            if (closure != null) {
                return closure;
            } else {
                return FrameAccess.getMethod(frame).image.nil; // TODO: make better
            }
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == RECEIVER"})
        protected static final Object doReceiver(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return doReceiverMatching(getReadableFrame(obj), obj, index);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == RECEIVER"})
        protected static final Object doReceiverMatching(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getReceiver(frame);
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == TEMP_FRAME_START"})
        protected static final Object doStack(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index) {
            return doStackMatching(getReadableFrame(obj), obj, index);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index >= TEMP_FRAME_START"})
        protected static final Object doStackMatching(final Frame frame, final FrameMarker obj, final long index) {
            final int stackIndex = (int) (index - CONTEXT.TEMP_FRAME_START);
            final CompiledCodeObject code = FrameAccess.getMethod(frame);
            if (stackIndex >= code.getNumStackSlots()) {
                return code.image.nil; // TODO: make this better.
            } else {
                return frame.getValue(code.getStackSlot(stackIndex));
            }
        }

        @Fallback
        protected static final long doFail(final FrameMarker obj, final long index) {
            throw new SqueakException("Unexpected values:", obj, index);
        }

        private static Frame getReadableFrame(final FrameMarker marker) {
            final Frame targetFrame = Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null;
                }
                final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                if (marker.matchesContextOrMarker(contextOrMarker)) {
                    return current;
                } else {
                    return null;
                }
            });
            assert targetFrame != null : "Unable to find frame for " + marker;
            return targetFrame;
        }
    }

    @ImportStatic({CONTEXT.class, FrameAccess.class})
    public abstract static class ContextObjectWriteNode extends Node {

        public static ContextObjectWriteNode create() {
            return ContextObjectWriteNodeGen.create();
        }

        public abstract void execute(Frame frame, FrameMarker obj, long index, Object value);

        @Specialization(guards = {"!obj.matches(frame)", "index == SENDER_OR_NIL"})
        protected static final void doSender(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index, final Object value) {
            // Bailing, frame needs to be materialized.
            obj.getMaterializedContext().atput0(index, value);
        }

        @Specialization(guards = {"obj.matches(frame)", "index == SENDER_OR_NIL"})
        protected static final void doSenderMatching(final Frame frame, final FrameMarker obj, final long index, final Object value) {
            // Bailing, frame needs to be materialized.
            obj.getMaterializedContext(frame).atput0(index, value);
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == INSTRUCTION_POINTER"})
        protected static final void doPC(@SuppressWarnings("unused") final Frame frame, final FrameMarker obj, final long index, final NilObject value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doPCMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == INSTRUCTION_POINTER"})
        protected static final void doPCMatching(final Frame frame, final FrameMarker obj, final long index, final NilObject value) {
            frame.setInt(FrameAccess.getMethod(frame).instructionPointerSlot, -1);
        }

        @Specialization(guards = {"!obj.matches(frame)", "index == INSTRUCTION_POINTER", "value >= 0"})
        protected static final void doPC(@SuppressWarnings("unused") final VirtualFrame frame, final FrameMarker obj, final long index, final long value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doPCMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == INSTRUCTION_POINTER", "value >= 0"})
        protected static final void doPCMatching(final Frame frame, final FrameMarker obj, final long index, final long value) {
            final CompiledCodeObject blockOrMethod = FrameAccess.getMethod(frame);
            final int initalPC;
            if (blockOrMethod instanceof CompiledBlockObject) {
                initalPC = ((CompiledBlockObject) blockOrMethod).getInitialPC();
            } else {
                initalPC = ((CompiledMethodObject) blockOrMethod).getInitialPC();
            }
            frame.setInt(blockOrMethod.instructionPointerSlot, (int) value - initalPC);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!obj.matches(frame)", "index == STACKPOINTER"})
        protected static final void doSP(final Frame frame, final FrameMarker obj, final long index, final long value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doSPMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == STACKPOINTER"})
        protected static final void doSPMatching(final Frame frame, final FrameMarker obj, final long index, final long value) {
            frame.setInt(FrameAccess.getMethod(frame).stackPointerSlot, (int) value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!obj.matches(frame)", "index == METHOD"})
        protected static final void doMethod(final Frame frame, final FrameMarker obj, final long index, final CompiledCodeObject value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doMethodMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == METHOD"})
        protected static final void doMethodMatching(final Frame frame, final FrameMarker obj, final long index, final CompiledCodeObject value) {
            frame.getArguments()[FrameAccess.METHOD] = value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosure(final Frame frame, final FrameMarker obj, final long index, final NilObject value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doClosureMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosureMatching(final Frame frame, final FrameMarker obj, final long index, final NilObject value) {
            frame.getArguments()[FrameAccess.CLOSURE_OR_NULL] = null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosure(final Frame frame, final FrameMarker obj, final long index, final BlockClosureObject value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doClosureMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosureMatching(final Frame frame, final FrameMarker obj, final long index, final BlockClosureObject value) {
            frame.getArguments()[FrameAccess.CLOSURE_OR_NULL] = value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!obj.matches(frame)", "index == RECEIVER"})
        protected static final void doReceiver(final Frame frame, final FrameMarker obj, final long index, final Object value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doReceiverMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.matches(frame)", "index == RECEIVER"})
        protected static final void doReceiverMatching(final Frame frame, final FrameMarker obj, final long index, final Object value) {
            frame.getArguments()[FrameAccess.RECEIVER] = value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!obj.matches(frame)", "index >= TEMP_FRAME_START"})
        protected static final void doStack(final Frame frame, final FrameMarker obj, final long index, final Object value) {
            final Object writableFrameOrContext = getWritableFrameOrContext(obj);
            if (writableFrameOrContext instanceof ContextObject) {
                ((ContextObject) writableFrameOrContext).atput0(index, value);
            } else {
                doStackMatching((Frame) writableFrameOrContext, obj, index, value);
            }
        }

        @Specialization(guards = {"obj.matches(frame)", "index >= TEMP_FRAME_START"})
        protected static final void doStackMatching(final Frame frame, @SuppressWarnings("unused") final FrameMarker obj, final long index, final Object value) {
            final int stackIndex = (int) (index - CONTEXT.TEMP_FRAME_START);
            final CompiledCodeObject code = FrameAccess.getBlockOrMethod(frame);
            // FIXME FIXME this is bad!
            assert value != null;
            final FrameSlot stackSlot = code.getStackSlot(stackIndex);
            frame.getFrameDescriptor().setFrameSlotKind(stackSlot, FrameSlotKind.Object);
            frame.setObject(stackSlot, value);
        }

        @Fallback
        protected static final void doFail(final FrameMarker obj, final long index, final Object value) {
            throw new SqueakException("Unexpected values:", obj, index, value);
        }

        private static Object getWritableFrameOrContext(final FrameMarker obj) {
            final Object targetFrame = Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null;
                }
                final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                if (obj == contextOrMarker) {
                    return frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
                } else if (contextOrMarker instanceof ContextObject && ((ContextObject) contextOrMarker).getFrameMarker() == obj) {
                    return contextOrMarker;
                } else {
                    return null;
                }
            });
            assert targetFrame != null : "Unable to find frame for " + obj;
            return targetFrame;
        }
    }

}
