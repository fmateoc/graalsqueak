package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.io.DisplayPoint;
import de.hpi.swa.graal.squeak.io.SqueakIOConstants;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.BooleanObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.NotProvided;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CHARACTER_SCANNER;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.ERROR_TABLE;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.FORM;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuaternaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.SeptenaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;

public final class IOPrimitives extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return IOPrimitivesFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 90)
    protected abstract static class PrimMousePointNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        private static final DisplayPoint NULL_POINT = new DisplayPoint(0, 0);

        protected PrimMousePointNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final PointersObject doMousePoint(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return method.image.asPoint(method.image.getDisplay().getLastMousePosition());
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected final PointersObject doMousePointHeadless(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return method.image.asPoint(NULL_POINT);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 91)
    protected abstract static class PrimTestDisplayDepthNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        // TODO: support all depths? {1, 2, 4, 8, 16, 32}
        @CompilationFinal(dimensions = 1) private static final int[] SUPPORTED_DEPTHS = new int[]{32};

        protected PrimTestDisplayDepthNode(final CompiledMethodObject method) {
            super(method);
        }

        @ExplodeLoop
        @Specialization
        protected static final boolean doTest(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final long depth) {
            for (int i = 0; i < SUPPORTED_DEPTHS.length; i++) {
                if (SUPPORTED_DEPTHS[i] == depth) {
                    return BooleanObject.TRUE;
                }
            }
            return BooleanObject.FALSE;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 92)
    protected abstract static class PrimSetDisplayModeNode extends AbstractPrimitiveNode implements QuinaryPrimitive {

        protected PrimSetDisplayModeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final AbstractSqueakObject doSet(final AbstractSqueakObject receiver, final long depth, final long width, final long height, final boolean fullscreen) {
            method.image.getDisplay().adjustDisplay(depth, width, height, fullscreen);
            return receiver;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final AbstractSqueakObject doSetHeadless(final AbstractSqueakObject receiver, final long depth, final long width, final long height, final boolean fullscreen) {
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 93)
    protected abstract static class PrimInputSemaphoreNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimInputSemaphoreNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final AbstractSqueakObject doSet(final AbstractSqueakObject receiver, final long semaIndex) {
            method.image.getDisplay().setInputSemaphoreIndex((int) semaIndex);
            return receiver;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final AbstractSqueakObject doSetHeadless(final AbstractSqueakObject receiver, @SuppressWarnings("unused") final long semaIndex) {
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 94)
    protected abstract static class PrimGetNextEventNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimGetNextEventNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final PointersObject doGetNext(final PointersObject eventSensor, final ArrayObject targetArray) {
            targetArray.setStorage(method.image.getDisplay().getNextEvent());
            return eventSensor;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final PointersObject doGetNextHeadless(final PointersObject eventSensor, @SuppressWarnings("unused") final ArrayObject targetArray) {
            targetArray.setStorage(SqueakIOConstants.NULL_EVENT);
            return eventSensor;
        }
    }

    /** Primitive 96 (primitiveCopyBits) not in use anymore. */

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 97)
    protected abstract static class PrimSnapshotNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        public PrimSnapshotNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization
        public static final Object doSnapshot(final VirtualFrame frame, final PointersObject receiver) {
            // TODO: implement primitiveSnapshot
            throw new PrimitiveFailed();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 98)
    protected abstract static class PrimStoreImageSegmentNode extends AbstractPrimitiveNode implements QuaternaryPrimitive {

        protected PrimStoreImageSegmentNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "segmentWordArray.isIntType()")
        protected static final AbstractSqueakObject doStore(final AbstractSqueakObject receiver, final ArrayObject rootsArray, final NativeObject segmentWordArray, final ArrayObject outPointerArray) {
            /**
             * TODO: implement primitive. In the meantime, pretend this primitive succeeds so that
             * some tests (e.g. BitmapStreamTests) run quickly.
             */
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 99)
    protected abstract static class PrimLoadImageSegmentNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimLoadImageSegmentNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "segmentWordArray.isIntType()")
        protected final ArrayObject doLoad(final AbstractSqueakObject receiver, final NativeObject segmentWordArray, final ArrayObject outPointerArray) {
            /**
             * TODO: implement primitive. In the meantime, pretend this primitive succeeds so that
             * some tests (e.g. BitmapStreamTests) run quickly.
             */
            return method.image.newEmptyArray();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 101)
    protected abstract static class PrimBeCursorNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimBeCursorNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final PointersObject doCursor(final PointersObject receiver, @SuppressWarnings("unused") final NotProvided mask) {
            method.image.getDisplay().setCursor(validateAndExtractWords(receiver), null, extractDepth(receiver));
            return receiver;
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final PointersObject doCursor(final PointersObject receiver, final PointersObject maskObject) {
            final int[] words = validateAndExtractWords(receiver);
            final int depth = extractDepth(receiver);
            if (depth == 1) {
                final int[] mask = ((NativeObject) maskObject.at0(FORM.BITS)).getIntStorage();
                method.image.getDisplay().setCursor(words, mask, 2);
            } else {
                method.image.getDisplay().setCursor(words, null, depth);
            }
            return receiver;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final PointersObject doCursorHeadless(final PointersObject receiver, @SuppressWarnings("unused") final NotProvided mask) {
            return receiver;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final PointersObject doCursorHeadless(final PointersObject receiver, @SuppressWarnings("unused") final PointersObject maskObject) {
            return receiver;
        }

        private int[] validateAndExtractWords(final PointersObject receiver) {
            final int[] words = ((NativeObject) receiver.at0(FORM.BITS)).getIntStorage();
            final long width = (long) receiver.at0(FORM.WIDTH);
            final long height = (long) receiver.at0(FORM.HEIGHT);
            if (width != SqueakIOConstants.CURSOR_WIDTH || height != SqueakIOConstants.CURSOR_HEIGHT) {
                CompilerDirectives.transferToInterpreter();
                method.image.printToStdErr("Unexpected cursor width:", width, "or height:", height, ". Proceeding with cropped cursor...");
                return Arrays.copyOf(words, SqueakIOConstants.CURSOR_WIDTH * SqueakIOConstants.CURSOR_HEIGHT);
            }
            return words;
        }

        private static int extractDepth(final PointersObject receiver) {
            return (int) (long) receiver.at0(FORM.DEPTH);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 102)
    protected abstract static class PrimBeDisplayNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimBeDisplayNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"method.image.hasDisplay()", "receiver.size() >= 4"})
        protected final boolean doDisplay(final PointersObject receiver) {
            method.image.setSpecialObject(SPECIAL_OBJECT.THE_DISPLAY, receiver);
            method.image.getDisplay().open(receiver);
            return BooleanObject.TRUE;
        }

        @Specialization(guards = {"!method.image.hasDisplay()"})
        protected static final boolean doDisplayHeadless(@SuppressWarnings("unused") final PointersObject receiver) {
            return BooleanObject.FALSE;
        }
    }

    @ImportStatic(CHARACTER_SCANNER.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 103)
    protected abstract static class PrimScanCharactersNode extends AbstractPrimitiveNode implements SeptenaryPrimitive {
        private static final int END_OF_RUN = 257 - 1;
        private static final int CROSSED_X = 258 - 1;

        protected PrimScanCharactersNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"startIndex > 0", "stopIndex > 0", "sourceString.isByteType()", "stopIndex <= sourceString.getByteLength()", "receiver.size() >= 4",
                        "stopsLib.size(stops) >= 258", "hasCorrectSlots(receiver, scanMapLib)"}, limit = "1")
        protected static final Object doScan(final PointersObject receiver, final long startIndex, final long stopIndex, final NativeObject sourceString, final long rightX,
                        final ArrayObject stops, final long kernData,
                        @CachedLibrary("receiver.at0(XTABLE)") final SqueakObjectLibrary scanXTableLib,
                        @CachedLibrary("receiver.at0(MAP)") final SqueakObjectLibrary scanMapLib,
                        @CachedLibrary("stops") final SqueakObjectLibrary stopsLib) {
            final ArrayObject scanXTable = (ArrayObject) receiver.at0(CHARACTER_SCANNER.XTABLE);
            final ArrayObject scanMap = (ArrayObject) receiver.at0(CHARACTER_SCANNER.MAP);
            final int scanMapSize = scanMapLib.size(scanMap);
            final byte[] sourceBytes = sourceString.getByteStorage();

            final int maxGlyph = scanXTableLib.size(scanXTable) - 2;
            long scanDestX = (long) receiver.at0(CHARACTER_SCANNER.DEST_X);
            long scanLastIndex = startIndex;
            while (scanLastIndex <= stopIndex) {
                final int ascii = sourceBytes[(int) (scanLastIndex - 1)] & 0xFF;
                final Object stopReason = stopsLib.at0(stops, ascii);
                if (stopReason != NilObject.SINGLETON) {
                    storeStateInReceiver(receiver, scanDestX, scanLastIndex);
                    return stopReason;
                }
                if (ascii < 0 || scanMapSize <= ascii) {
                    throw PrimitiveFailed.andTransferToInterpreter();
                }
                final int glyphIndex = (int) (long) scanMapLib.at0(scanMap, ascii);
                if (glyphIndex < 0 || glyphIndex > maxGlyph) {
                    throw PrimitiveFailed.andTransferToInterpreter();
                }
                final long sourceX1;
                final long sourceX2;
                sourceX1 = (long) scanXTableLib.at0(scanXTable, glyphIndex);
                sourceX2 = (long) scanXTableLib.at0(scanXTable, glyphIndex + 1);
                final long nextDestX = scanDestX + sourceX2 - sourceX1;
                if (nextDestX > rightX) {
                    storeStateInReceiver(receiver, scanDestX, scanLastIndex);
                    return stopsLib.at0(stops, CROSSED_X);
                }
                scanDestX = nextDestX + kernData;
                scanLastIndex++;
            }
            storeStateInReceiver(receiver, scanDestX, stopIndex);
            return stopsLib.at0(stops, END_OF_RUN);
        }

        private static void storeStateInReceiver(final PointersObject receiver, final long scanDestX, final long scanLastIndex) {
            receiver.atput0(CHARACTER_SCANNER.DEST_X, scanDestX);
            receiver.atput0(CHARACTER_SCANNER.LAST_INDEX, scanLastIndex);
        }

        protected static final boolean hasCorrectSlots(final PointersObject receiver, final SqueakObjectLibrary scanMapLib) {
            final Object scanMap = receiver.at0(CHARACTER_SCANNER.MAP);
            return receiver.at0(CHARACTER_SCANNER.DEST_X) instanceof Long && receiver.at0(CHARACTER_SCANNER.XTABLE) instanceof ArrayObject &&
                            scanMap instanceof ArrayObject && scanMapLib.size(scanMap) == 256;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 105)
    protected abstract static class PrimStringReplaceNode extends AbstractPrimitiveNode implements QuinaryPrimitive {
        protected PrimStringReplaceNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "inBounds(rcvrLib.instsize(rcvr), rcvrLib.size(rcvr), start, stop, replLib.instsize(repl), replLib.size(repl), replStart)", limit = "1")
        protected static final Object doReplace(final Object rcvr, final long start, final long stop, final Object repl, final long replStart,
                        @CachedLibrary("rcvr") final SqueakObjectLibrary rcvrLib,
                        @CachedLibrary("repl") final SqueakObjectLibrary replLib,
                        @Cached final BranchProfile failProfile) {
            if (rcvrLib.replaceFromToWithStartingAt(rcvr, (int) start, (int) stop, repl, (int) replStart)) {
                return rcvr;
            } else {
                failProfile.enter();
                throw new PrimitiveFailed(ERROR_TABLE.BAD_INDEX);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!inBounds(rcvrLib.instsize(rcvr), rcvrLib.size(rcvr), start, stop, replLib.instsize(repl), replLib.size(repl), replStart)", limit = "1")
        protected static final Object doBadIndex(final Object rcvr, final long start, final long stop, final Object repl, final long replStart,
                        @CachedLibrary("rcvr") final SqueakObjectLibrary rcvrLib,
                        @CachedLibrary("repl") final SqueakObjectLibrary replLib) {
            throw new PrimitiveFailed(ERROR_TABLE.BAD_INDEX);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 106)
    protected abstract static class PrimScreenSizeNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimScreenSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"hasVisibleDisplay(receiver)"})
        protected final PointersObject doSize(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return method.image.asPoint(method.image.getDisplay().getWindowSize());
        }

        @Specialization(guards = "!hasVisibleDisplay(receiver)")
        protected final PointersObject doSizeHeadless(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return method.image.asPoint(method.image.flags.getLastWindowSize());
        }

        // guard helper to work around code generation issue.
        protected final boolean hasVisibleDisplay(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return method.image.hasDisplay() && method.image.getDisplay().isVisible();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 107)
    protected abstract static class PrimMouseButtonsNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimMouseButtonsNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final long doMouseButtons(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return method.image.getDisplay().getLastMouseButton();
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final long doMouseButtonsHeadless(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return 0L;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 108)
    protected abstract static class PrimKeyboardNextNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimKeyboardNextNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final Object doNext(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            final long keyboardNext = method.image.getDisplay().keyboardNext();
            return keyboardNext == 0 ? NilObject.SINGLETON : keyboardNext;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final NilObject doNextHeadless(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return NilObject.SINGLETON;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 109)
    protected abstract static class PrimKeyboardPeekNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimKeyboardPeekNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final Object doPeek(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            final long keyboardPeek = method.image.getDisplay().keyboardPeek();
            return keyboardPeek == 0 ? NilObject.SINGLETON : keyboardPeek;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final NilObject doPeekHeadless(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return NilObject.SINGLETON;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 126)
    protected abstract static class PrimDeferDisplayUpdatesNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        public PrimDeferDisplayUpdatesNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final AbstractSqueakObject doDefer(final AbstractSqueakObject receiver, final boolean flag) {
            method.image.getDisplay().setDeferUpdates(flag);
            return receiver;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected static final AbstractSqueakObject doNothing(final AbstractSqueakObject receiver, @SuppressWarnings("unused") final boolean flag) {
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 127)
    protected abstract static class PrimShowDisplayRectNode extends AbstractPrimitiveNode implements QuinaryPrimitive {

        protected PrimShowDisplayRectNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"method.image.hasDisplay()", "left < right", "top < bottom"})
        protected final PointersObject doShow(final PointersObject receiver, final long left, final long right, final long top, final long bottom) {
            method.image.getDisplay().showDisplayRect((int) left, (int) right, (int) top, (int) bottom);
            return receiver;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!method.image.hasDisplay() || (left > right || top > bottom)"})
        protected static final PointersObject doDrawHeadless(final PointersObject receiver, final long left, final long right, final long top, final long bottom) {
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 133)
    protected abstract static class PrimSetInterruptKeyNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimSetInterruptKeyNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final AbstractSqueakObject set(final AbstractSqueakObject receiver) {
            // TODO: interrupt key is obsolete in image, but maybe still needed in the vm?
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 140)
    protected abstract static class PrimBeepNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimBeepNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "method.image.hasDisplay()")
        protected final AbstractSqueakObject doBeep(final AbstractSqueakObject receiver) {
            method.image.getDisplay().beep();
            return receiver;
        }

        @Specialization(guards = "!method.image.hasDisplay()")
        protected final AbstractSqueakObject doNothing(final AbstractSqueakObject receiver) {
            method.image.printToStdOut((char) 7);
            return receiver;
        }
    }
}
