package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
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
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackWriteNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class ContextObjectNodes {

    public static ContextObject getMaterializedContextForMarker(final FrameMarker obj) {
        final Frame targetFrame = FrameAccess.findFrameForMarker(obj);
        if (targetFrame == null) {
            throw new SqueakException("Could not find frame");
        }
        final CompiledCodeObject code = FrameAccess.getMethod(targetFrame);
        final MaterializedFrame materializedFrame = targetFrame.materialize();
        final ContextObject context = ContextObject.create(code.image, code.sqContextSize(), materializedFrame, obj);
        materializedFrame.setObject(code.thisContextOrMarkerSlot, context);
        return context;
    }

    @ImportStatic(CONTEXT.class)
    public abstract static class ContextObjectReadNode extends Node {

        public static ContextObjectReadNode create() {
            return ContextObjectReadNodeGen.create();
        }

        public abstract Object execute(Frame frame, FrameMarker obj, long index);

        @Specialization(guards = {"!obj.isMatchingFrame(frame)"})
        protected static final Object doMaterialize(final Frame frame, final FrameMarker obj, final long index) {
            return getMaterializedContextForMarker(obj).at0(index);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == SENDER_OR_NIL"})
        protected static final Object doSenderVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            final Object senderOrMarker = frame.getArguments()[FrameAccess.SENDER_OR_SENDER_MARKER];
            if (senderOrMarker instanceof FrameMarker) {
                return doMaterialize(frame, obj, index);
            } else {
                return senderOrMarker;
            }
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == INSTRUCTION_POINTER"})
        protected static final Object doPCVirtualized(final Frame frame, final FrameMarker obj, final long index) {
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

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == STACKPOINTER"})
        protected static final Object doSPVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            return (long) FrameUtil.getIntSafe(frame, FrameAccess.getMethod(frame).stackPointerSlot);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == METHOD"})
        protected static final CompiledCodeObject doMethodVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getMethod(frame);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == CLOSURE_OR_NIL"})
        protected static final Object doClosureVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            final BlockClosureObject closure = FrameAccess.getClosure(frame);
            if (closure != null) {
                return closure;
            } else {
                return FrameAccess.getMethod(frame).image.nil; // TODO: make better
            }
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == RECEIVER"})
        protected static final Object doReceiverVirtualized(final Frame frame, final FrameMarker obj, final long index) {
            return FrameAccess.getReceiver(frame);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index >= TEMP_FRAME_START"})
        protected static final Object doStackVirtualized(final Frame frame, final FrameMarker obj, final long index) {
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
    }

    @ImportStatic({CONTEXT.class, FrameAccess.class})
    public abstract static class ContextObjectWriteNode extends Node {

        public static ContextObjectWriteNode create() {
            return ContextObjectWriteNodeGen.create();
        }

        public abstract void execute(VirtualFrame frame, FrameMarker obj, long index, Object value);

        @Specialization(guards = {"!obj.isMatchingFrame(frame)"})
        protected static final void doMaterialize(final VirtualFrame frame, final FrameMarker obj, final long index, final Object value) {
            getMaterializedContextForMarker(obj).atput0(index, value);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == SENDER_OR_NIL"})
        protected static final void doSenderVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final Object value) {
            // Bailing, frame needs to be materialized.
            doMaterialize(frame, obj, index, value);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == INSTRUCTION_POINTER"})
        protected static final void doPCVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final NilObject value) {
            frame.setInt(FrameAccess.getMethod(frame).instructionPointerSlot, -1);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == INSTRUCTION_POINTER", "value >= 0"})
        protected static final void doPCVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final long value) {
            final CompiledCodeObject blockOrMethod = FrameAccess.getMethod(frame);
            final int initalPC;
            if (blockOrMethod instanceof CompiledBlockObject) {
                initalPC = ((CompiledBlockObject) blockOrMethod).getInitialPC();
            } else {
                initalPC = ((CompiledMethodObject) blockOrMethod).getInitialPC();
            }
            frame.setInt(FrameAccess.getMethod(frame).instructionPointerSlot, (int) value - initalPC);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == STACKPOINTER"})
        protected static final void doSPVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final long value) {
            frame.setInt(FrameAccess.getMethod(frame).stackPointerSlot, (int) value);
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == METHOD"})
        protected static final void doMethodVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final CompiledCodeObject value) {
            frame.getArguments()[FrameAccess.METHOD] = value;
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosureVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final NilObject value) {
            frame.getArguments()[FrameAccess.CLOSURE_OR_NULL] = null;
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == CLOSURE_OR_NIL"})
        protected static final void doClosureVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final BlockClosureObject value) {
            frame.getArguments()[FrameAccess.CLOSURE_OR_NULL] = value;
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index == RECEIVER"})
        protected static final void doReceiverVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final Object value) {
            frame.getArguments()[FrameAccess.RECEIVER] = value;
        }

        @Specialization(guards = {"obj.isMatchingFrame(frame)", "index >= TEMP_FRAME_START"})
        protected static final void doStackVirtualized(final VirtualFrame frame, final FrameMarker obj, final long index, final Object value,
                        @Cached("create(getMethod(frame))") final FrameStackWriteNode writeNode) {
            final int stackIndex = (int) (index - CONTEXT.TEMP_FRAME_START);
            writeNode.execute(frame, stackIndex, value);
        }

        @Fallback
        protected static final void doFail(final FrameMarker obj, final long index, final Object value) {
            throw new SqueakException("Unexpected values:", obj, index);
        }
    }

}
