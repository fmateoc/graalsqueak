package de.hpi.swa.graal.squeak.util;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameUtil;

import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;

public final class FrameAccess {
    /**
     * GraalSqueak frame arguments.
     *
     * <pre>
     * CompiledCodeObject
     * SenderOrNull
     * ClosureOrNull
     * Receiver
     * Arguments*
     * CopiedValues*
     * </pre>
     */
    public static final int METHOD = 0;
    public static final int SENDER_OR_SENDER_MARKER = 1;
    public static final int CLOSURE_OR_NULL = 2;
    public static final int RECEIVER = 3;
    public static final int ARGUMENTS_START = 4;

    /**
     * GraalSqueak frame slots.
     *
     * <pre>
     * thisContextOrMarker
     * instructionPointer
     * stackPointer
     * stack*
     * </pre>
     */

    public static CompiledMethodObject getMethod(final Frame frame) {
        return (CompiledMethodObject) frame.getArguments()[METHOD];
    }

    public static Object getSender(final Frame frame) {
        return frame.getArguments()[SENDER_OR_SENDER_MARKER];
    }

    public static BlockClosureObject getClosure(final Frame frame) {
        return (BlockClosureObject) frame.getArguments()[CLOSURE_OR_NULL];
    }

    public static CompiledCodeObject getBlockOrMethod(final Frame frame) {
        final BlockClosureObject closure = getClosure(frame);
        return closure != null ? closure.getCompiledBlock() : getMethod(frame);
    }

    public static Object getReceiver(final Frame frame) {
        return frame.getArguments()[RECEIVER];
    }

    public static Object getArgument(final Frame frame, final int index) {
        return frame.getArguments()[RECEIVER + index];
    }

    public static Object[] getReceiverAndArguments(final Frame frame) {
        CompilerAsserts.neverPartOfCompilation();
        return Arrays.copyOfRange(frame.getArguments(), RECEIVER, frame.getArguments().length);
    }

    public static Object getContextOrMarker(final Frame frame) {
        return FrameUtil.getObjectSafe(frame, getBlockOrMethod(frame).thisContextOrMarkerSlot);
    }

    public static int getStackPointer(final Frame frame) {
        return FrameUtil.getIntSafe(frame, getBlockOrMethod(frame).stackPointerSlot);
    }

    public static int getStackSize(final Frame frame) {
        return getBlockOrMethod(frame).getSqueakContextSize();
    }

    public static boolean isGraalSqueakFrame(final Frame frame) {
        final Object[] arguments = frame.getArguments();
        return arguments.length >= RECEIVER && arguments[METHOD] instanceof CompiledCodeObject;
    }

    public static boolean matchesContextOrMarker(final FrameMarker frameMarker, final Object contextOrMarker) {
        return contextOrMarker == frameMarker || (contextOrMarker instanceof ContextObject && ((ContextObject) contextOrMarker).getFrameMarker() == frameMarker);
    }

    public static Object[] newWith(final CompiledMethodObject method, final Object sender, final BlockClosureObject closure, final Object[] arguments) {
        final Object[] frameArguments = new Object[RECEIVER + arguments.length];
        assert method != null : "Method should never be null";
        assert sender != null : "Sender should never be null";
        assert arguments.length > 0 : "At least a receiver must be provided";
        assert arguments[0] != null : "Receiver should never be null";
        frameArguments[METHOD] = method;
        frameArguments[SENDER_OR_SENDER_MARKER] = sender;
        frameArguments[CLOSURE_OR_NULL] = closure;
        for (int i = 0; i < arguments.length; i++) {
            frameArguments[RECEIVER + i] = arguments[i];
        }
        return frameArguments;
    }

    public static Object[] newDummyWith(final CompiledCodeObject code, final Object sender, final BlockClosureObject closure, final Object[] arguments) {
        final Object[] frameArguments = new Object[RECEIVER + arguments.length];
        assert sender != null : "Sender should never be null";
        assert arguments.length > 0 : "At least a receiver must be provided";
        frameArguments[METHOD] = code;
        frameArguments[SENDER_OR_SENDER_MARKER] = sender;
        frameArguments[CLOSURE_OR_NULL] = closure;
        for (int i = 0; i < arguments.length; i++) {
            frameArguments[RECEIVER + i] = arguments[i];
        }
        return frameArguments;
    }

    @TruffleBoundary
    public static Frame findFrameForMarker(final FrameMarker frameMarker) {
        CompilerDirectives.bailout("Finding materializable frames should never be part of compiled code as it triggers deopts");
        return Truffle.getRuntime().iterateFrames(frameInstance -> {
            final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
            if (!isGraalSqueakFrame(current)) {
                return null;
            }
            final Object contextOrMarker = getContextOrMarker(current);
            if (frameMarker == contextOrMarker || (contextOrMarker instanceof ContextObject && ((ContextObject) contextOrMarker).getFrameMarker() == frameMarker)) {
                return frameInstance.getFrame(FrameInstance.FrameAccess.MATERIALIZE);
            }
            return null;
        });
    }

    public static Object returnMarkerOrContext(final Object contextOrMarker, final FrameInstance frameInstance) {
        if (contextOrMarker instanceof FrameMarker) {
            final Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.MATERIALIZE);
            final CompiledCodeObject method = FrameAccess.getBlockOrMethod(frame);
            assert FrameUtil.getObjectSafe(frame, method.thisContextOrMarkerSlot) == contextOrMarker : "ContextObject should not be allocated";
            final ContextObject context = ContextObject.create(method.image, method.getSqueakContextSize(), frame.materialize(), (FrameMarker) contextOrMarker);
            frame.setObject(method.thisContextOrMarkerSlot, context);
            return context;
        } else {
            assert contextOrMarker instanceof ContextObject;
            return contextOrMarker;
        }
    }

    public static <T> T iterateFrames(final SqueakFrameVisitor<T> visitor) {
        return iterateFrames(visitor, FrameInstance.FrameAccess.READ_ONLY);
    }

    public static <T> T iterateFrames(final SqueakFrameVisitor<T> visitor, final FrameInstance.FrameAccess access) {
        return Truffle.getRuntime().iterateFrames(frameInstance -> {
            final Frame frame = frameInstance.getFrame(access);
            if (isGraalSqueakFrame(frame)) {
                return visitor.visitFrame(frameInstance, frame);
            } else {
                return null;
            }
        });
    }
}
