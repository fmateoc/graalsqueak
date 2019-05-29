package de.hpi.swa.graal.squeak.model;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.SqueakGuards;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.nodes.accessing.UpdateSqueakObjectHashNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

@ExportLibrary(SqueakObjectLibrary.class)
public final class WeakPointersObject extends AbstractPointersObject {
    public WeakPointersObject(final SqueakImageContext image, final long hash, final ClassObject sqClass) {
        super(image, hash, sqClass);
    }

    public WeakPointersObject(final SqueakImageContext image, final ClassObject classObject, final int size) {
        super(image, classObject);
        setPointers(ArrayUtils.withAll(size, NilObject.SINGLETON));
    }

    private WeakPointersObject(final WeakPointersObject original) {
        super(original.image, original.getSqueakClass());
        setPointersUnsafe(original.getPointers().clone());
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        final Object[] pointersValues = chunk.getPointers();
        final int length = pointersValues.length;
        setPointers(new Object[length]);
        final SqueakObjectLibrary objectLibrary = SqueakObjectLibrary.getUncached(this);
        for (int i = 0; i < length; i++) {
            objectLibrary.atput0(this, i, pointersValues[i]);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "WeakPointersObject: " + getSqueakClass();
    }

    public void setWeakPointer(final int index, final Object value) {
        setPointer(index, new WeakReference<>(value, image.weakPointersQueue));
    }

    @ExportMessage
    public Object at0(final int index) {
        final Object value = getPointer(index);
        if (value instanceof WeakReference<?>) {
            return NilObject.nullToNil(((Reference<?>) value).get());
        } else {
            return value;
        }
    }

    @ExportMessage
    protected static final class Atput0 {
        @Specialization(guards = "pointers.getSqueakClass().getBasicInstanceSize() <= index")
        protected static void doWeakInVariablePart(final WeakPointersObject pointers, final int index, final AbstractSqueakObject value) {
            pointers.setWeakPointer(index, value);
        }

        @Fallback
        protected static void doNonWeak(final WeakPointersObject pointers, final int index, final Object value) {
            pointers.setPointer(index, value);
        }

    }

    // @ExportMessage
    public void pointersBecomeOneWay(final Object[] from, final Object[] to, final boolean copyHash,
                    @Cached final UpdateSqueakObjectHashNode updateHashNode) {
        for (int i = 0; i < from.length; i++) {
            final Object fromPointer = from[i];
            for (int j = 0; j < pointers.length; j++) {
                final Object newPointer = pointers[j];
                if (newPointer == fromPointer) {
                    final Object toPointer = to[i];
                    pointers[j] = toPointer;
                    updateHashNode.executeUpdate(fromPointer, toPointer, copyHash);
                }
            }
        }
    }

    @ImportStatic(SqueakGuards.class)
    @ExportMessage
    protected static final class ReplaceFromToWithStartingAt {
        @Specialization(guards = "inBounds(rcvr.instsize(), rcvr.size(), start, stop, repl.instsize(), repl.size(), replStart)")
        protected static boolean doWeakPointers(final WeakPointersObject rcvr, final int start, final int stop, final WeakPointersObject repl, final int replStart) {
            System.arraycopy(repl.getPointers(), replStart - 1, rcvr.getPointers(), start - 1, 1 + stop - start);
            return true;
        }

        @Specialization(guards = "inBounds(rcvr.instsize(), rcvr.size(), start, stop, repl.instsize(), repl.size(), replStart)")
        protected static boolean doWeakPointersPointers(final WeakPointersObject rcvr, final int start, final int stop, final PointersObject repl, final int replStart,
                        @CachedLibrary("rcvr") final SqueakObjectLibrary rcvrLib) {
            final int repOff = replStart - start;
            for (int i = start - 1; i < stop; i++) {
                rcvrLib.atput0(rcvr, i, repl.at0(repOff + i));
            }
            return true;
        }

        @Specialization(guards = "inBounds(rcvr.instsize(), rcvr.size(), start, stop, replLib.instsize(repl), replLib.size(repl), replStart)")
        protected static boolean doWeakPointersArray(final WeakPointersObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart,
                        @CachedLibrary("rcvr") final SqueakObjectLibrary rcvrLib,
                        @CachedLibrary(limit = "3") final SqueakObjectLibrary replLib) {
            final int repOff = replStart - start;
            for (int i = start - 1; i < stop; i++) {
                rcvrLib.atput0(rcvr, i, replLib.at0(repl, repOff + i));
            }
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static boolean doFail(final WeakPointersObject rcvr, final int start, final int stop, final Object repl, final int replStart) {
            return false;
        }
    }

    @ExportMessage
    public WeakPointersObject shallowCopy() {
        return new WeakPointersObject(this);
    }
}
