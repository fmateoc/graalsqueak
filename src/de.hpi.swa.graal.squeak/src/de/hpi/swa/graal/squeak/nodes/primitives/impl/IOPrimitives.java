package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.awt.AWTError;
import java.awt.Toolkit;
import java.util.List;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.exceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractPointersObject;
import de.hpi.swa.graal.squeak.model.BaseSqueakObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.ListObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.FORM;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT_INDEX;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.model.SqueakObject;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.impl.MiscellaneousPrimitives.SimulationPrimitiveNode;
import de.hpi.swa.graal.squeak.util.SqueakDisplay;

public class IOPrimitives extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return IOPrimitivesFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 90)
    protected abstract static class PrimMousePointNode extends AbstractPrimitiveNode {

        protected PrimMousePointNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doMousePoint() {
            return code.image.wrap(code.image.display.getLastMousePosition());
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 91, numArguments = 2)
    protected abstract static class PrimTestDisplayDepthNode extends AbstractPrimitiveNode {
        private static final int[] SUPPORTED_DEPTHS = new int[]{32}; // TODO: support all depths?
                                                                     // {1, 2, 4, 8, 16, 32}

        protected PrimTestDisplayDepthNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doTest(@SuppressWarnings("unused") final BaseSqueakObject receiver, final long depth) {
            for (int i = 0; i < SUPPORTED_DEPTHS.length; i++) {
                if (SUPPORTED_DEPTHS[i] == depth) {
                    return code.image.sqTrue;
                }
            }
            return code.image.sqFalse;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 92, numArguments = 5)
    protected abstract static class PrimSetDisplayModeNode extends AbstractPrimitiveNode {

        protected PrimSetDisplayModeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doSet(final BaseSqueakObject receiver, final long depth, final long width, final long height, final boolean fullscreen) {
            code.image.display.adjustDisplay(depth, width, height, fullscreen);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 94, numArguments = 2)
    protected abstract static class PrimGetNextEventNode extends AbstractPrimitiveNode {

        protected PrimGetNextEventNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doGetNext(final PointersObject eventSensor, final ListObject targetArray) {
            final long[] nextEvent = code.image.display.getNextEvent();
            for (int i = 0; i < SqueakDisplay.EVENT_SIZE; i++) {
                targetArray.atput0(i, nextEvent[i]);
            }
            return eventSensor;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 96, variableArguments = true)
    protected abstract static class PrimCopyBitsNode extends SimulationPrimitiveNode {

        protected PrimCopyBitsNode(final CompiledMethodObject method) {
            super(method, "BitBltPlugin", "primitiveCopyBits");
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 101, variableArguments = true)
    protected abstract static class PrimBeCursorNode extends AbstractPrimitiveNode {

        protected PrimBeCursorNode(final CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeWithArguments(final VirtualFrame frame, final Object... rcvrAndArgs) {
            return doCursor(rcvrAndArgs);
        }

        @Specialization
        protected final Object doCursor(final Object[] rcvrAndArgs) {
            final Object cursorObject = rcvrAndArgs[0];
            if (!(cursorObject instanceof PointersObject)) {
                throw new SqueakException("Unexpected cursorObject: " + cursorObject.toString());
            }
            final PointersObject cursor = (PointersObject) cursorObject;
            int[] words = ((NativeObject) cursor.at0(FORM.BITS)).getWords();
            final long width = (long) cursor.at0(FORM.WIDTH);
            final long height = (long) cursor.at0(FORM.HEIGHT);
            if (width != SqueakDisplay.CURSOR_WIDTH || height != SqueakDisplay.CURSOR_HEIGHT) {
                throw new SqueakException("Unexpected cursor width: " + width + " or height: " + height);
            }
            final long depth = (long) cursor.at0(FORM.DEPTH);
            if (depth != 1) {
                throw new SqueakException("Unexpected cursor depth: " + depth);
            }
            if (rcvrAndArgs.length == 2) {
                final Object maskObject = rcvrAndArgs[1];
                if (!(maskObject instanceof PointersObject)) {
                    throw new SqueakException("Unexpected maskObject: " + maskObject.toString());
                }
                final int[] mask = ((NativeObject) ((PointersObject) maskObject).at0(FORM.BITS)).getWords();
                words = mergeCursorWithMask(words, mask);
            }
            code.image.display.setCursor(words);
            return rcvrAndArgs[0];
        }

        private static int[] mergeCursorWithMask(final int[] cursorWords, final int[] maskWords) {
            final int[] words = new int[16];
            for (int i = 0; i < words.length; i++) {
                words[i] = cursorWords[i] ^= ~maskWords[i];
            }
            return words;
        }

        // TODO: properly support arbitrary-sized 32 bit ARGB forms
        // private static int[] mergeCursorWithMask(int[] cursorWords, int[] maskWords) {
        // int[] words = new int[16];
        // int cursorWord, maskWord, bit, merged;
        // for (int y = 0; y < SqueakDisplay.CURSOR_HEIGHT; y++) {
        // cursorWord = cursorWords[y];
        // maskWord = maskWords[y];
        // bit = 0x80000000;
        // merged = 0;
        // for (int x = 0; x < SqueakDisplay.CURSOR_WIDTH; x++) {
        // merged = merged | ((maskWord & bit) >> x) | ((cursorWord & bit) >> (x + 1));
        // bit = bit >>> 1;
        // }
        // words[y] = merged;
        // }
        // return words;
        // }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 102)
    protected abstract static class PrimBeDisplayNode extends AbstractPrimitiveNode {

        protected PrimBeDisplayNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final boolean doDisplay(final PointersObject receiver) {
            if (receiver.size() < 4) {
                throw new PrimitiveFailed();
            }
            code.image.display.setSqDisplay(receiver);
            code.image.display.open();
            code.image.specialObjectsArray.atput0(SPECIAL_OBJECT_INDEX.TheDisplay, receiver);
            return code.image.sqTrue;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 105, numArguments = 5)
    protected abstract static class PrimReplaceFromToNode extends AbstractPrimitiveNode {
        protected PrimReplaceFromToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Override
        public final Object executeWithArguments(final VirtualFrame frame, final Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(final VirtualFrame frame) {
            try {
                return executeReplace(frame);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeReplace(VirtualFrame frame);

        @Specialization
        protected static final Object replace(final LargeIntegerObject rcvr, final long start, final long stop, final LargeIntegerObject repl, final long replStart) {
            return replaceInLarge(rcvr, start, stop, repl.getBytes(), replStart);
        }

        @Specialization
        protected final Object replace(final LargeIntegerObject rcvr, final long start, final long stop, final long repl, final long replStart) {
            return replaceInLarge(rcvr, start, stop, asLargeInteger(repl).getBytes(), replStart);
        }

        @Specialization
        protected static final Object replace(final LargeIntegerObject rcvr, final long start, final long stop, final NativeObject repl, final long replStart) {
            return replaceInLarge(rcvr, start, stop, repl.getBytes(), replStart);
        }

        private static Object replaceInLarge(final LargeIntegerObject rcvr, final long start, final long stop, final byte[] replBytes, final long replStart) {
            final byte[] rcvrBytes = rcvr.getBytes();
            final int repOff = (int) (replStart - start);
            for (int i = (int) (start - 1); i < stop; i++) {
                rcvrBytes[i] = replBytes[repOff + i];
            }
            rcvr.setBytes(rcvrBytes);
            return rcvr;
        }

        @Specialization
        protected static final Object replace(final NativeObject rcvr, final long start, final long stop, final LargeIntegerObject repl, final long replStart) {
            final int repOff = (int) (replStart - start);
            final byte[] replBytes = repl.getBytes();
            for (int i = (int) (start - 1); i < stop; i++) {
                rcvr.setNativeAt0(i, replBytes[repOff + i]);
            }
            return rcvr;
        }

        @Specialization
        protected static final Object replace(final NativeObject rcvr, final long start, final long stop, final NativeObject repl, final long replStart) {
            final int repOff = (int) (replStart - start);
            for (int i = (int) (start - 1); i < stop; i++) {
                rcvr.setNativeAt0(i, repl.getNativeAt0(repOff + i));
            }
            return rcvr;
        }

        @Specialization(guards = "replStart == 1")
        protected static final Object replace(final NativeObject rcvr, final long start, final long stop, final long repl, @SuppressWarnings("unused") final long replStart) {
            for (int i = (int) (start - 1); i < stop; i++) {
                rcvr.setNativeAt0(i, repl);
            }
            return rcvr;
        }

        @Specialization
        protected static final Object replace(final NativeObject rcvr, final long start, final long stop, final AbstractPointersObject repl, final long replStart) {
            final int repOff = (int) (replStart - start);
            for (int i = (int) (start - 1); i < stop; i++) {
                final Object value = repl.at0(repOff + i);
                if (value instanceof Character) {
                    rcvr.setNativeAt0(i, Character.getNumericValue((Character) value));
                } else if (value instanceof Long) {
                    rcvr.setNativeAt0(i, (long) value);
                } else {
                    throw new PrimitiveFailed();
                }
            }
            return rcvr;
        }

        @Specialization
        protected static final Object doListObject(final AbstractPointersObject rcvr, final long start, final long stop, final ListObject repl, final long replStart) {
            return doSqueakObject(rcvr, start, stop, repl, replStart);
        }

        @Specialization
        protected static final Object doListObject(final AbstractPointersObject rcvr, final long start, final long stop, final NativeObject repl, final long replStart) {
            return doSqueakObject(rcvr, start, stop, repl, replStart);
        }

        @Specialization
        protected static final Object replace(final CompiledMethodObject rcvr, final long start, final long stop, final CompiledMethodObject repl, final long replStart) {
            return doSqueakObject(rcvr, start, stop, repl, replStart);
        }

        private static Object doSqueakObject(final SqueakObject rcvr, final long start, final long stop, final SqueakObject repl, final long replStart) {
            final long repOff = replStart - start;
            for (int i = (int) (start - 1); i < stop; i++) {
                rcvr.atput0(i, repl.at0(repOff + i));
            }
            return rcvr;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 106)
    protected abstract static class PrimScreenSizeNode extends AbstractPrimitiveNode {

        protected PrimScreenSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final BaseSqueakObject doSize(@SuppressWarnings("unused") final BaseSqueakObject receiver) {
            return code.image.wrap(code.image.display.getSize());
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 107)
    protected abstract static class PrimMouseButtonsNode extends AbstractPrimitiveNode {

        protected PrimMouseButtonsNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doMouseButtons(@SuppressWarnings("unused") final BaseSqueakObject receiver) {
            return code.image.wrap(code.image.display.getLastMouseButton());
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 108)
    protected abstract static class PrimKeyboardNextNode extends AbstractPrimitiveNode {

        protected PrimKeyboardNextNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doNext(@SuppressWarnings("unused") final BaseSqueakObject receiver) {
            final int keyboardNext = code.image.display.keyboardNext();
            if (keyboardNext == 0) {
                return code.image.nil;
            } else {
                return code.image.wrap(keyboardNext);
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 109)
    protected abstract static class PrimKeyboardPeekNode extends AbstractPrimitiveNode {

        protected PrimKeyboardPeekNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doPeek(@SuppressWarnings("unused") final BaseSqueakObject receiver) {
            final int keyboardPeek = code.image.display.keyboardPeek();
            if (keyboardPeek == 0) {
                return code.image.nil;
            } else {
                return code.image.wrap(keyboardPeek);
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 126, numArguments = 2)
    protected abstract static class PrimDeferDisplayUpdatesNode extends AbstractPrimitiveNode {

        public PrimDeferDisplayUpdatesNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final BaseSqueakObject doDefer(final BaseSqueakObject receiver, @SuppressWarnings("unused") final boolean flag) {
            // TODO: uncomment: code.image.display.setDeferUpdates(flag);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 127, numArguments = 5)
    protected abstract static class PrimDrawRectNode extends AbstractPrimitiveNode {

        protected PrimDrawRectNode(final CompiledMethodObject method) {
            super(method);
        }

        protected static final boolean inBounds(final long left, final long right, final long top, final long bottom) {
            return (left <= right) && (top <= bottom);
        }

        @Specialization(guards = "inBounds(left, right, top, bottom)")
        protected final BaseSqueakObject doDraw(final PointersObject receiver, final long left, final long right, final long top, final long bottom) {
            code.image.display.forceRect((int) left, (int) right, (int) top, (int) bottom);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 133)
    protected abstract static class PrimSetInterruptKeyNode extends AbstractPrimitiveNode {

        protected PrimSetInterruptKeyNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final BaseSqueakObject set(final BaseSqueakObject receiver) {
            // TODO: interrupt key is obsolete in image, but maybe still needed in the vm?
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 140)
    protected abstract static class PrimBeepNode extends AbstractPrimitiveNode {

        protected PrimBeepNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final BaseSqueakObject doBeep(final BaseSqueakObject receiver) {
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (AWTError e) {
                code.image.getError().println("BEEP (unable to find default AWT Toolkit).");
            }
            return receiver;
        }
    }
}
