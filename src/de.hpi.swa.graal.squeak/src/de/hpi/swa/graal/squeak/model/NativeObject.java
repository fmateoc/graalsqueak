package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.util.ArrayConversionUtils;

@ExportLibrary(SqueakObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class NativeObject extends AbstractSqueakObjectWithClassAndHash {
    public static final short BYTE_MAX = (short) (Math.pow(2, Byte.SIZE) - 1);
    public static final int SHORT_MAX = (int) (Math.pow(2, Short.SIZE) - 1);
    public static final long INTEGER_MAX = (long) (Math.pow(2, Integer.SIZE) - 1);

    @CompilationFinal private Object storage;

    public NativeObject(final SqueakImageContext image) { // constructor for special selectors
        super(image, -1, null);
        storage = new byte[0];
    }

    private NativeObject(final SqueakImageContext image, final ClassObject classObject, final Object storage) {
        super(image, classObject);
        assert storage != null;
        this.storage = storage;
    }

    private NativeObject(final SqueakImageContext image, final long hash, final ClassObject classObject, final Object storage) {
        super(image, hash, classObject);
        assert storage != null;
        this.storage = storage;
    }

    public static NativeObject newNativeBytes(final SqueakImageChunk chunk) {
        return new NativeObject(chunk.image, chunk.getHash(), chunk.getSqClass(), chunk.getBytes());
    }

    public static NativeObject newNativeBytes(final SqueakImageContext img, final ClassObject klass, final byte[] bytes) {
        return new NativeObject(img, klass, bytes);
    }

    public static NativeObject newNativeBytes(final SqueakImageContext img, final ClassObject klass, final int size) {
        return newNativeBytes(img, klass, new byte[size]);
    }

    public static NativeObject newNativeInts(final SqueakImageChunk chunk) {
        return new NativeObject(chunk.image, chunk.getHash(), chunk.getSqClass(), chunk.getInts());
    }

    public static NativeObject newNativeInts(final SqueakImageContext img, final ClassObject klass, final int size) {
        return newNativeInts(img, klass, new int[size]);
    }

    public static NativeObject newNativeInts(final SqueakImageContext img, final ClassObject klass, final int[] words) {
        return new NativeObject(img, klass, words);
    }

    public static NativeObject newNativeLongs(final SqueakImageChunk chunk) {
        return new NativeObject(chunk.image, chunk.getHash(), chunk.getSqClass(), chunk.getLongs());
    }

    public static NativeObject newNativeLongs(final SqueakImageContext img, final ClassObject klass, final int size) {
        return newNativeLongs(img, klass, new long[size]);
    }

    public static NativeObject newNativeLongs(final SqueakImageContext img, final ClassObject klass, final long[] longs) {
        return new NativeObject(img, klass, longs);
    }

    public static NativeObject newNativeShorts(final SqueakImageChunk chunk) {
        return new NativeObject(chunk.image, chunk.getHash(), chunk.getSqClass(), chunk.getShorts());
    }

    public static NativeObject newNativeShorts(final SqueakImageContext img, final ClassObject klass, final int size) {
        return newNativeShorts(img, klass, new short[size]);
    }

    public static NativeObject newNativeShorts(final SqueakImageContext img, final ClassObject klass, final short[] shorts) {
        return new NativeObject(img, klass, shorts);
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        if (isByteType()) {
            final byte[] bytes = chunk.getBytes();
            setStorage(bytes);
            if (image.getDebugErrorSelector() == null && Arrays.equals(SqueakImageContext.DEBUG_ERROR_SELECTOR_NAME, bytes)) {
                image.setDebugErrorSelector(this);
            } else if (image.getDebugSyntaxErrorSelector() == null && Arrays.equals(SqueakImageContext.DEBUG_SYNTAX_ERROR_SELECTOR_NAME, bytes)) {
                image.setDebugSyntaxErrorSelector(this);
            }
        } else if (isShortType()) {
            setStorage(chunk.getShorts());
        } else if (isIntType()) {
            setStorage(chunk.getInts());
        } else if (isLongType()) {
            setStorage(chunk.getLongs());
        } else {
            throw SqueakException.create("Unsupported type");
        }
    }

    public void become(final NativeObject other) {
        super.becomeOtherClass(other);
        final Object otherStorage = other.storage;
        other.setStorage(storage);
        setStorage(otherStorage);
    }

    public void convertToBytesStorage(final byte[] bytes) {
        assert storage.getClass() != bytes.getClass() : "Converting storage of same type unnecessary";
        setStorage(bytes);
    }

    public void convertToIntsStorage(final byte[] bytes) {
        assert storage.getClass() != bytes.getClass() : "Converting storage of same type unnecessary";
        setStorage(ArrayConversionUtils.intsFromBytes(bytes));
    }

    public void convertToLongsStorage(final byte[] bytes) {
        assert storage.getClass() != bytes.getClass() : "Converting storage of same type unnecessary";
        setStorage(ArrayConversionUtils.longsFromBytes(bytes));
    }

    public void convertToShortsStorage(final byte[] bytes) {
        assert storage.getClass() != bytes.getClass() : "Converting storage of same type unnecessary";
        setStorage(ArrayConversionUtils.shortsFromBytes(bytes));
    }

    public int getByteLength() {
        return getByteStorage().length;
    }

    public byte[] getByteStorage() {
        assert isByteType();
        return (byte[]) storage;
    }

    public int getIntLength() {
        return getIntStorage().length;
    }

    public int[] getIntStorage() {
        assert isIntType();
        return (int[]) storage;
    }

    public int getLongLength() {
        return getLongStorage().length;
    }

    public long[] getLongStorage() {
        assert isLongType();
        return (long[]) storage;
    }

    public int getShortLength() {
        return getShortStorage().length;
    }

    public short[] getShortStorage() {
        assert isShortType();
        return (short[]) storage;
    }

    public boolean hasSameFormat(final ClassObject other) {
        return getSqueakClass().getFormat() == other.getFormat();
    }

    public boolean hasSameStorageType(final NativeObject other) {
        return storage.getClass() == other.storage.getClass();
    }

    public boolean isByteType() {
        return storage.getClass() == byte[].class;
    }

    public boolean isIntType() {
        return storage.getClass() == int[].class;
    }

    public boolean isLongType() {
        return storage.getClass() == long[].class;
    }

    public boolean isShortType() {
        return storage.getClass() == short[].class;
    }

    public void setStorage(final Object storage) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.storage = storage;
    }

    public String asStringUnsafe() {
        return ArrayConversionUtils.bytesToString(getByteStorage());
    }

    @TruffleBoundary
    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (isByteType()) {
            final ClassObject squeakClass = getSqueakClass();
            if (squeakClass.isStringClass()) {
                return asStringUnsafe();
            } else if (squeakClass.isSymbolClass()) {
                return "#" + asStringUnsafe();
            } else {
                return "byte[" + getByteLength() + "]";
            }
        } else if (isShortType()) {
            return "short[" + getShortLength() + "]";
        } else if (isIntType()) {
            return "int[" + getIntLength() + "]";
        } else if (isLongType()) {
            return "long[" + getLongLength() + "]";
        } else {
            throw SqueakException.create("Unexpected native object type");
        }
    }

    public boolean isDebugErrorSelector() {
        return this == image.getDebugErrorSelector();
    }

    public boolean isDebugSyntaxErrorSelector() {
        return this == image.getDebugSyntaxErrorSelector();
    }

    public boolean isDoesNotUnderstand() {
        return this == image.doesNotUnderstand;
    }

    /*
     * INTEROPERABILITY
     */

    @ExportMessage
    public boolean isString() {
        final ClassObject squeakClass = getSqueakClass();
        return squeakClass.isStringClass() || squeakClass.isSymbolClass();
    }

    @ExportMessage
    public String asString() throws UnsupportedMessageException {
        if (isString()) {
            return asStringUnsafe();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public abstract static class AcceptsValue {
        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(@SuppressWarnings("unused") final NativeObject obj, final char value) {
            return value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final char value) {
            return value <= NativeObject.SHORT_MAX;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isIntType() || obj.isLongType()")
        protected static final boolean doNativeInts(final NativeObject obj, final char value) {
            return true;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(final NativeObject obj, final CharacterObject value) {
            return false; // Value of CharacterObjects is always larger than `Character.MAX_VALUE`.
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final CharacterObject value) {
            return value.getValue() <= NativeObject.SHORT_MAX;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isIntType() || obj.isLongType()")
        protected static final boolean doNativeInts(final NativeObject obj, final CharacterObject value) {
            return true;
        }

        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.SHORT_MAX;
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final boolean doNativeInts(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.INTEGER_MAX;
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final boolean doNativeLongs(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value;
        }

        @Specialization(guards = {"obj.isByteType()"})
        protected static final boolean doNativeBytesLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.BYTE_MAX);
        }

        @Specialization(guards = {"obj.isShortType()"})
        protected static final boolean doNativeShortsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.SHORT_MAX);
        }

        @Specialization(guards = {"obj.isIntType()"})
        protected static final boolean doNativeIntsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.INTEGER_MAX);
        }

        @Specialization(guards = {"obj.isLongType()"})
        protected static final boolean doNativeLongsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.isZeroOrPositive() && value.lessThanOneShiftedBy64();
        }
    }

    @ExportMessage
    public abstract static class At0 {
        @Specialization(guards = "obj.isByteType()")
        protected static final long doNativeBytes(final NativeObject obj, final int index) {
            return Byte.toUnsignedLong(obj.getByteStorage()[index]);
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final long doNativeShorts(final NativeObject obj, final int index) {
            return Short.toUnsignedLong(obj.getShortStorage()[index]);
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final long doNativeInts(final NativeObject obj, final int index) {
            return Integer.toUnsignedLong(obj.getIntStorage()[index]);
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final Object doNativeLongs(final NativeObject obj, final int index) {
            final long value = obj.getLongStorage()[index];
            if (value >= 0) {
                return value;
            } else {
                return LargeIntegerObject.valueOf(obj.image, value).toUnsigned();
            }
        }
    }

    @ExportMessage
    @ImportStatic(NativeObject.class)
    public abstract static class Atput0 {
        @Specialization(guards = {"obj.isByteType()", "value >= 0", "value <= BYTE_MAX"})
        protected static final void doNativeBytes(final NativeObject obj, final int index, final long value) {
            obj.getByteStorage()[index] = (byte) value;
        }

        @Specialization(guards = {"obj.isShortType()", "value >= 0", "value <= SHORT_MAX"})
        protected static final void doNativeShorts(final NativeObject obj, final int index, final long value) {
            obj.getShortStorage()[index] = (short) value;
        }

        @Specialization(guards = {"obj.isIntType()", "value >= 0", "value <= INTEGER_MAX"})
        protected static final void doNativeInts(final NativeObject obj, final int index, final long value) {
            obj.getIntStorage()[index] = (int) value;
        }

        @Specialization(guards = {"obj.isLongType()", "value >= 0"})
        protected static final void doNativeLongs(final NativeObject obj, final int index, final long value) {
            obj.getLongStorage()[index] = value;
        }

        protected static final boolean inByteRange(final char value) {
            return value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = {"obj.isByteType()", "inByteRange(value)"})
        protected static final void doNativeBytesChar(final NativeObject obj, final int index, final char value) {
            doNativeBytes(obj, index, value);
        }

        @Specialization(guards = "obj.isShortType()") // char values fit into short
        protected static final void doNativeShortsChar(final NativeObject obj, final int index, final char value) {
            doNativeShorts(obj, index, value);
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final void doNativeIntsChar(final NativeObject obj, final int index, final char value) {
            doNativeInts(obj, index, value);
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final void doNativeIntsChar(final NativeObject obj, final int index, final CharacterObject value) {
            doNativeInts(obj, index, value.getValue());
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final void doNativeLongsChar(final NativeObject obj, final int index, final char value) {
            doNativeLongs(obj, index, value);
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final void doNativeLongsChar(final NativeObject obj, final int index, final CharacterObject value) {
            doNativeLongs(obj, index, value.getValue());
        }

        @Specialization(guards = {"obj.isByteType()", "value.inRange(0, BYTE_MAX)"})
        protected static final void doNativeBytesLargeInteger(final NativeObject obj, final int index, final LargeIntegerObject value) {
            doNativeBytes(obj, index, value.longValueExact());
        }

        @Specialization(guards = {"obj.isShortType()", "value.inRange(0, SHORT_MAX)"})
        protected static final void doNativeShortsLargeInteger(final NativeObject obj, final int index, final LargeIntegerObject value) {
            doNativeShorts(obj, index, value.longValueExact());
        }

        @Specialization(guards = {"obj.isIntType()", "value.inRange(0, INTEGER_MAX)"})
        protected static final void doNativeIntsLargeInteger(final NativeObject obj, final int index, final LargeIntegerObject value) {
            doNativeInts(obj, index, value.longValueExact());
        }

        @Specialization(guards = {"obj.isLongType()", "value.isZeroOrPositive()", "value.fitsIntoLong()"})
        protected static final void doNativeLongsLargeInteger(final NativeObject obj, final int index, final LargeIntegerObject value) {
            doNativeLongs(obj, index, value.longValueExact());
        }

        @Specialization(guards = {"obj.isLongType()", "value.isZeroOrPositive()", "!value.fitsIntoLong()", "value.lessThanOneShiftedBy64()"})
        protected static final void doNativeLongsLargeIntegerSigned(final NativeObject obj, final int index, final LargeIntegerObject value) {
            doNativeLongs(obj, index, value.toSigned().longValueExact());
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public int instsize() {
        return 0;
    }

    @ExportMessage
    public abstract static class Size {
        @Specialization(guards = "obj.isByteType()")
        protected static final int doNativeBytes(final NativeObject obj) {
            return obj.getByteLength();
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final int doNativeShorts(final NativeObject obj) {
            return obj.getShortLength();
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final int doNativeInts(final NativeObject obj) {
            return obj.getIntLength();
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final int doNativeLongs(final NativeObject obj) {
            return obj.getLongLength();
        }
    }

    @ExportMessage
    public abstract static class ShallowCopy {
        @Specialization(guards = "receiver.isByteType()")
        protected static final NativeObject doNativeBytes(final NativeObject receiver) {
            return NativeObject.newNativeBytes(receiver.image, receiver.getSqueakClass(), receiver.getByteStorage().clone());
        }

        @Specialization(guards = "receiver.isShortType()")
        protected static final NativeObject doNativeShorts(final NativeObject receiver) {
            return NativeObject.newNativeShorts(receiver.image, receiver.getSqueakClass(), receiver.getShortStorage().clone());
        }

        @Specialization(guards = "receiver.isIntType()")
        protected static final NativeObject doNativeInts(final NativeObject receiver) {
            return NativeObject.newNativeInts(receiver.image, receiver.getSqueakClass(), receiver.getIntStorage().clone());
        }

        @Specialization(guards = "receiver.isLongType()")
        protected static final NativeObject doNativeLongs(final NativeObject receiver) {
            return NativeObject.newNativeLongs(receiver.image, receiver.getSqueakClass(), receiver.getLongStorage().clone());
        }
    }
}
