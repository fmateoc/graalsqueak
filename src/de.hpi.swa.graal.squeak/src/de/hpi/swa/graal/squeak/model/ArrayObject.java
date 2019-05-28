package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.SqueakGuards;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

@ExportLibrary(SqueakObjectLibrary.class)
public final class ArrayObject extends AbstractSqueakObjectWithClassAndHash {
    public static final byte BOOLEAN_NIL_TAG = 0;
    public static final byte BOOLEAN_TRUE_TAG = 1;
    public static final byte BOOLEAN_FALSE_TAG = -1;
    public static final char CHAR_NIL_TAG = Character.MAX_VALUE - 1; // Rather unlikely char.
    public static final long LONG_NIL_TAG = Long.MIN_VALUE + 42; // Rather unlikely long.
    public static final double DOUBLE_NIL_TAG = Double.longBitsToDouble(0x7ff8000000000001L); // NaN+1.
    public static final long DOUBLE_NIL_TAG_LONG = Double.doubleToRawLongBits(DOUBLE_NIL_TAG);
    public static final NativeObject NATIVE_OBJECT_NIL_TAG = null;
    public static final boolean ENABLE_STORAGE_STRATEGIES = true;
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, ArrayObject.class);

    private Object storage;

    public ArrayObject(final SqueakImageContext image) {
        super(image); // for special ArrayObjects only
    }

    private ArrayObject(final SqueakImageContext image, final ClassObject classObject, final Object storage) {
        super(image, classObject);
        this.storage = storage;
    }

    public ArrayObject(final SqueakImageContext img, final long hash, final ClassObject klass) {
        super(img, hash, klass);
    }

    public static ArrayObject createEmptyStrategy(final SqueakImageContext image, final ClassObject classObject, final int size) {
        return new ArrayObject(image, classObject, size);
    }

    public static ArrayObject createObjectStrategy(final SqueakImageContext image, final ClassObject classObject, final int size) {
        final Object[] objects = new Object[size];
        Arrays.fill(objects, NilObject.SINGLETON);
        return new ArrayObject(image, classObject, objects);
    }

    public static ArrayObject createWithStorage(final SqueakImageContext image, final ClassObject classObject, final Object storage) {
        return new ArrayObject(image, classObject, storage);
    }

    public static boolean isCharNilTag(final char value) {
        return value == CHAR_NIL_TAG;
    }

    public static boolean isDoubleNilTag(final double value) {
        return Double.doubleToRawLongBits(value) == DOUBLE_NIL_TAG_LONG;
    }

    public static boolean isLongNilTag(final long value) {
        return value == LONG_NIL_TAG;
    }

    public static boolean isNativeObjectNilTag(final NativeObject value) {
        return value == NATIVE_OBJECT_NIL_TAG;
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        final Object[] pointers = chunk.getPointers();
        if (!ENABLE_STORAGE_STRATEGIES) {
            storage = pointers;
            return;
        }
        final int valuesLength = pointers.length;
        storage = valuesLength;
        if (valuesLength > 0) {
            final SqueakObjectLibrary objectLibrary = SqueakObjectLibrary.getUncached();
            for (int i = 0; i < pointers.length; i++) {
                objectLibrary.atput0(this, i, pointers[i]);
            }
        }
    }

    public int getBooleanLength() {
        return getBooleanStorage().length;
    }

    public byte[] getBooleanStorage() {
        assert isBooleanType();
        return (byte[]) storage;
    }

    public int getCharLength() {
        return getCharStorage().length;
    }

    public char[] getCharStorage() {
        assert isCharType();
        return (char[]) storage;
    }

    public int getDoubleLength() {
        return getDoubleStorage().length;
    }

    public double[] getDoubleStorage() {
        assert isDoubleType();
        return (double[]) storage;
    }

    public int getEmptyLength() {
        return getEmptyStorage();
    }

    public int getEmptyStorage() {
        assert isEmptyType();
        return (int) storage;
    }

    public int getLongLength() {
        return getLongStorage().length;
    }

    public long[] getLongStorage() {
        assert isLongType();
        return (long[]) storage;
    }

    public int getNativeObjectLength() {
        return getNativeObjectStorage().length;
    }

    public NativeObject[] getNativeObjectStorage() {
        assert isNativeObjectType();
        return (NativeObject[]) storage;
    }

    public int getObjectLength() {
        return getObjectStorage().length;
    }

    public Object[] getObjectStorage() {
        assert isObjectType();
        return CompilerDirectives.castExact(storage, Object[].class);
    }

    public Class<? extends Object> getStorageType() {
        return storage.getClass();
    }

    public boolean isBooleanType() {
        return storage.getClass() == byte[].class;
    }

    public boolean isCharType() {
        return storage.getClass() == char[].class;
    }

    public boolean isDoubleType() {
        return storage.getClass() == double[].class;
    }

    public boolean isEmptyType() {
        return storage instanceof Integer;
    }

    public boolean isLongType() {
        return storage.getClass() == long[].class;
    }

    public boolean isNativeObjectType() {
        return storage.getClass() == NativeObject[].class;
    }

    public boolean isObjectType() {
        return storage.getClass() == Object[].class;
    }

    public boolean isTraceable() {
        return isObjectType() || isNativeObjectType();
    }

    public boolean hasSameStorageType(final ArrayObject other) {
        return storage.getClass() == other.storage.getClass();
    }

    public void setStorage(final Object newStorage) {
        storage = newStorage;
    }

    public static Object toObjectFromBoolean(final byte value) {
        if (value == BOOLEAN_FALSE_TAG) {
            return BooleanObject.FALSE;
        } else if (value == BOOLEAN_TRUE_TAG) {
            return BooleanObject.TRUE;
        } else {
            assert value == BOOLEAN_NIL_TAG;
            return NilObject.SINGLETON;
        }
    }

    public static Object toObjectFromChar(final char value) {
        return isCharNilTag(value) ? NilObject.SINGLETON : value;
    }

    public static Object toObjectFromLong(final long value) {
        return isLongNilTag(value) ? NilObject.SINGLETON : value;
    }

    public static Object toObjectFromDouble(final double value) {
        return isDoubleNilTag(value) ? NilObject.SINGLETON : value;
    }

    public static Object toObjectFromNativeObject(final NativeObject value) {
        return isNativeObjectNilTag(value) ? NilObject.SINGLETON : value;
    }

    public void transitionFromBooleansToObjects() {
        LOG.finer("transition from Booleans to Objects");
        final byte[] booleans = getBooleanStorage();
        final Object[] objects = new Object[booleans.length];
        for (int i = 0; i < booleans.length; i++) {
            objects[i] = toObjectFromBoolean(booleans[i]);
        }
        storage = objects;
    }

    public void transitionFromCharsToObjects() {
        LOG.finer("transition from Chars to Objects");
        final char[] chars = getCharStorage();
        final Object[] objects = new Object[chars.length];
        for (int i = 0; i < chars.length; i++) {
            objects[i] = toObjectFromChar(chars[i]);
        }
        storage = objects;
    }

    public void transitionFromDoublesToObjects() {
        LOG.finer("transition from Doubles to Objects");
        final double[] doubles = getDoubleStorage();
        final Object[] objects = new Object[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            objects[i] = toObjectFromDouble(doubles[i]);
        }
        storage = objects;
    }

    public void transitionFromEmptyToBooleans() {
        // Zero-initialized, no need to fill with BOOLEAN_NIL_TAG.
        storage = new byte[getEmptyStorage()];
    }

    public void transitionFromEmptyToChars() {
        final char[] chars = new char[getEmptyStorage()];
        Arrays.fill(chars, CHAR_NIL_TAG);
        storage = chars;
    }

    public void transitionFromEmptyToDoubles() {
        final double[] doubles = new double[getEmptyStorage()];
        Arrays.fill(doubles, DOUBLE_NIL_TAG);
        storage = doubles;
    }

    public void transitionFromEmptyToLongs() {
        final long[] longs = new long[getEmptyStorage()];
        Arrays.fill(longs, LONG_NIL_TAG);
        storage = longs;
    }

    public void transitionFromEmptyToNatives() {
        storage = new NativeObject[getEmptyStorage()];
    }

    public void transitionFromEmptyToObjects() {
        storage = ArrayUtils.withAll(getEmptyLength(), NilObject.SINGLETON);
    }

    public void transitionFromLongsToObjects() {
        LOG.finer("transition from Longs to Objects");
        final long[] longs = getLongStorage();
        final Object[] objects = new Object[longs.length];
        for (int i = 0; i < longs.length; i++) {
            objects[i] = toObjectFromLong(longs[i]);
        }
        storage = objects;
    }

    public void transitionFromNativesToObjects() {
        LOG.finer("transition from NativeObjects to Objects");
        final NativeObject[] natives = getNativeObjectStorage();
        final Object[] objects = new Object[natives.length];
        for (int i = 0; i < natives.length; i++) {
            objects[i] = toObjectFromNativeObject(natives[i]);
        }
        storage = objects;
    }

    @ExportMessage
    public static class At0 {
        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()"})
        protected static final NilObject doEmptyArray(final ArrayObject obj, final int index) {
            assert 0 <= index && index < obj.getEmptyLength();
            return NilObject.SINGLETON;
        }

        @Specialization(guards = "obj.isBooleanType()")
        protected static final Object doArrayOfBooleans(final ArrayObject obj, final int index) {
            final byte value = obj.getBooleanStorage()[index];
            if (value == ArrayObject.BOOLEAN_FALSE_TAG) {
                return BooleanObject.FALSE;
            } else if (value == ArrayObject.BOOLEAN_TRUE_TAG) {
                return BooleanObject.TRUE;
            } else {
                assert value == ArrayObject.BOOLEAN_NIL_TAG;
                return NilObject.SINGLETON;
            }
        }

        @Specialization(guards = "obj.isCharType()")
        protected static final Object doArrayOfChars(final ArrayObject obj, final int index) {
            final char value = obj.getCharStorage()[index];
            return value == ArrayObject.CHAR_NIL_TAG ? NilObject.SINGLETON : value;
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final Object doArrayOfLongs(final ArrayObject obj, final int index) {
            final long value = obj.getLongStorage()[index];
            return value == ArrayObject.LONG_NIL_TAG ? NilObject.SINGLETON : value;
        }

        @Specialization(guards = "obj.isDoubleType()")
        protected static final Object doArrayOfDoubles(final ArrayObject obj, final int index) {
            final double value = obj.getDoubleStorage()[index];
            return Double.doubleToRawLongBits(value) == ArrayObject.DOUBLE_NIL_TAG_LONG ? NilObject.SINGLETON : value;
        }

        @Specialization(guards = "obj.isNativeObjectType()")
        protected static final AbstractSqueakObject doArrayOfNativeObjects(final ArrayObject obj, final int index) {
            return NilObject.nullToNil(obj.getNativeObjectStorage()[index]);
        }

        @Specialization(guards = "obj.isObjectType()")
        protected static final Object doArrayOfObjects(final ArrayObject obj, final int index) {
            assert obj.getObjectStorage()[index] != null;
            return obj.getObjectStorage()[index];
        }
    }

    @ImportStatic(SqueakGuards.class)
    @ExportMessage
    public static class Atput0 {
        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()"})
        protected static final void doEmptyArray(final ArrayObject obj, final int index, final NilObject value) {
            assert index < obj.getEmptyLength();
            // Nothing to do.
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()"})
        protected static final void doEmptyArray(final ArrayObject obj, final int index, final NativeObject value) {
            obj.transitionFromEmptyToNatives();
            doArrayOfNativeObjects(obj, index, value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()"})
        protected static final void doEmptyArrayToBoolean(final ArrayObject obj, final int index, final boolean value) {
            obj.transitionFromEmptyToBooleans();
            doArrayOfBooleans(obj, index, value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()"})
        protected static final void doEmptyArrayToChar(final ArrayObject obj, final int index, final char value,
                        @Exclusive @Cached final BranchProfile nilTagProfile) {
            if (ArrayObject.isCharNilTag(value)) {
                nilTagProfile.enter();
                doEmptyArrayToObject(obj, index, value);
                return;
            }
            obj.transitionFromEmptyToChars();
            doArrayOfChars(obj, index, value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()"})
        protected static final void doEmptyArrayToLong(final ArrayObject obj, final int index, final long value,
                        @Exclusive @Cached final BranchProfile nilTagProfile) {
            if (ArrayObject.isLongNilTag(value)) {
                nilTagProfile.enter();
                doEmptyArrayToObject(obj, index, value);
                return;
            }
            obj.transitionFromEmptyToLongs();
            doArrayOfLongs(obj, index, value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()"})
        protected static final void doEmptyArrayToDouble(final ArrayObject obj, final int index, final double value,
                        @Exclusive @Cached final BranchProfile nilTagProfile) {
            if (ArrayObject.isDoubleNilTag(value)) {
                nilTagProfile.enter();
                doEmptyArrayToObject(obj, index, value);
                return;
            }
            obj.transitionFromEmptyToDoubles();
            doArrayOfDoubles(obj, index, value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isEmptyType()", "!isBoolean(value)", "!isCharacter(value)", "!isLong(value)", "!isDouble(value)", "!isNativeObject(value)"})
        protected static final void doEmptyArrayToObject(final ArrayObject obj, final int index, final Object value) {
            obj.transitionFromEmptyToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = "obj.isBooleanType()")
        protected static final void doArrayOfBooleans(final ArrayObject obj, final int index, final boolean value) {
            obj.getBooleanStorage()[index] = value ? ArrayObject.BOOLEAN_TRUE_TAG : ArrayObject.BOOLEAN_FALSE_TAG;
        }

        @Specialization(guards = "obj.isBooleanType()")
        protected static final void doArrayOfBooleans(final ArrayObject obj, final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.getBooleanStorage()[index] = ArrayObject.BOOLEAN_NIL_TAG;
        }

        @Specialization(guards = {"obj.isBooleanType()", "!isBoolean(value)", "!isNil(value)"})
        protected static final void doArrayOfBooleans(final ArrayObject obj, final int index, final Object value) {
            obj.transitionFromBooleansToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = {"obj.isCharType()", "!isCharNilTag(value)"})
        protected static final void doArrayOfChars(final ArrayObject obj, final int index, final char value) {
            obj.getCharStorage()[index] = value;
        }

        @Specialization(guards = {"obj.isCharType()", "isCharNilTag(value)"})
        protected static final void doArrayOfCharsNilTagClash(final ArrayObject obj, final int index, final char value) {
            /** `value` happens to be char nil tag, need to despecialize to be able store it. */
            obj.transitionFromCharsToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = "obj.isCharType()")
        protected static final void doArrayOfChars(final ArrayObject obj, final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.getCharStorage()[index] = ArrayObject.CHAR_NIL_TAG;
        }

        @Specialization(guards = {"obj.isCharType()", "!isCharacter(value)", "!isNil(value)"})
        protected static final void doArrayOfChars(final ArrayObject obj, final int index, final Object value) {
            obj.transitionFromCharsToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = {"obj.isLongType()", "!isLongNilTag(value)"})
        protected static final void doArrayOfLongs(final ArrayObject obj, final int index, final long value) {
            obj.getLongStorage()[index] = value;
        }

        @Specialization(guards = {"obj.isLongType()", "isLongNilTag(value)"})
        protected static final void doArrayOfLongsNilTagClash(final ArrayObject obj, final int index, final long value) {
            /** `value` happens to be long nil tag, need to despecialize to be able store it. */
            obj.transitionFromLongsToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final void doArrayOfLongs(final ArrayObject obj, final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.getLongStorage()[index] = ArrayObject.LONG_NIL_TAG;
        }

        @Specialization(guards = {"obj.isLongType()", "!isLong(value)", "!isNil(value)"})
        protected static final void doArrayOfLongs(final ArrayObject obj, final int index, final Object value) {
            obj.transitionFromLongsToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = {"obj.isDoubleType()", "!isDoubleNilTag(value)"})
        protected static final void doArrayOfDoubles(final ArrayObject obj, final int index, final double value) {
            obj.getDoubleStorage()[index] = value;
        }

        @Specialization(guards = {"obj.isDoubleType()", "isDoubleNilTag(value)"})
        protected static final void doArrayOfDoublesNilTagClash(final ArrayObject obj, final int index, final double value) {
            // `value` happens to be double nil tag, need to despecialize to be able store it.
            obj.transitionFromDoublesToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = "obj.isDoubleType()")
        protected static final void doArrayOfDoubles(final ArrayObject obj, final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.getDoubleStorage()[index] = ArrayObject.DOUBLE_NIL_TAG;
        }

        @Specialization(guards = {"obj.isDoubleType()", "!isDouble(value)", "!isNil(value)"})
        protected static final void doArrayOfDoubles(final ArrayObject obj, final int index, final Object value) {
            obj.transitionFromDoublesToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = "obj.isNativeObjectType()")
        protected static final void doArrayOfNativeObjects(final ArrayObject obj, final int index, final NativeObject value) {
            obj.getNativeObjectStorage()[index] = value;
        }

        @Specialization(guards = "obj.isNativeObjectType()")
        protected static final void doArrayOfNativeObjects(final ArrayObject obj, final int index, @SuppressWarnings("unused") final NilObject value) {
            obj.getNativeObjectStorage()[index] = ArrayObject.NATIVE_OBJECT_NIL_TAG;
        }

        @Specialization(guards = {"obj.isNativeObjectType()", "!isNativeObject(value)", "!isNil(value)"})
        protected static final void doArrayOfNativeObjects(final ArrayObject obj, final int index, final Object value) {
            obj.transitionFromNativesToObjects();
            doArrayOfObjects(obj, index, value);
        }

        @Specialization(guards = "obj.isObjectType()")
        protected static final void doArrayOfObjects(final ArrayObject obj, final int index, final Object value) {
            obj.getObjectStorage()[index] = value;
        }
    }

    @ExportMessage
    public static final class Become {
        @Specialization(guards = "receiver != other")
        protected static boolean doBecome(final ArrayObject receiver, final ArrayObject other) {
            receiver.becomeOtherClass(other);
            final Object otherStorage = other.storage;
            other.setStorage(receiver.storage);
            receiver.setStorage(otherStorage);
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static boolean doFail(final ArrayObject receiver, final Object other) {
            return false;
        }
    }

    @ExportMessage
    public static final class ChangeClassOfTo {
        @Specialization(guards = {"receiver.getSqueakClass().getFormat() == argument.getFormat()"})
        protected static boolean doChangeClassOfTo(final ArrayObject receiver, final ClassObject argument) {
            receiver.setSqueakClass(argument);
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static boolean doFail(final ArrayObject receiver, final ClassObject argument) {
            return false;
        }
    }

    @ExportMessage
    public int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @ImportStatic(SqueakGuards.class)
    @ExportMessage
    public static class ReplaceFromToWithStartingAt {
        @SuppressWarnings("unused")
        @Specialization(guards = {"rcvr.isEmptyType()", "repl.isEmptyType()", "inBounds(rcvr.instsize(), rcvr.getEmptyLength(), start, stop, repl.instsize(), repl.getEmptyLength(), replStart)"})
        protected static final boolean doEmptyArrays(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart) {
            return true; // Nothing to do.
        }

        @Specialization(guards = {"rcvr.isBooleanType()", "repl.isBooleanType()",
                        "inBounds(rcvr.instsize(), rcvr.getBooleanLength(), start, stop, repl.instsize(), repl.getBooleanLength(), replStart)"})
        protected static final boolean doArraysOfBooleans(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart) {
            System.arraycopy(repl.getBooleanStorage(), replStart - 1, rcvr.getBooleanStorage(), start - 1, 1 + stop - start);
            return true;
        }

        @Specialization(guards = {"rcvr.isCharType()", "repl.isCharType()", "inBounds(rcvr.instsize(), rcvr.getCharLength(), start, stop, repl.instsize(), repl.getCharLength(), replStart)"})
        protected static final boolean doArraysOfChars(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart) {
            System.arraycopy(repl.getCharStorage(), replStart - 1, rcvr.getCharStorage(), start - 1, 1 + stop - start);
            return true;
        }

        @Specialization(guards = {"rcvr.isLongType()", "repl.isLongType()", "inBounds(rcvr.instsize(), rcvr.getLongLength(), start, stop, repl.instsize(), repl.getLongLength(), replStart)"})
        protected static final boolean doArraysOfLongs(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart) {
            System.arraycopy(repl.getLongStorage(), replStart - 1, rcvr.getLongStorage(), start - 1, 1 + stop - start);
            return true;
        }

        @Specialization(guards = {"rcvr.isDoubleType()", "repl.isDoubleType()", "inBounds(rcvr.instsize(), rcvr.getDoubleLength(), start, stop, repl.instsize(), repl.getDoubleLength(), replStart)"})
        protected static final boolean doArraysOfDoubles(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart) {
            System.arraycopy(repl.getDoubleStorage(), replStart - 1, rcvr.getDoubleStorage(), start - 1, 1 + stop - start);
            return true;
        }

        @Specialization(guards = {"rcvr.isNativeObjectType()", "repl.isNativeObjectType()",
                        "inBounds(rcvr.instsize(), rcvr.getNativeObjectLength(), start, stop, repl.instsize(), repl.getNativeObjectLength(), replStart)"})
        protected static final boolean doArraysOfNatives(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart) {
            System.arraycopy(repl.getNativeObjectStorage(), replStart - 1, rcvr.getNativeObjectStorage(), start - 1, 1 + stop - start);
            return true;
        }

        @Specialization(guards = {"rcvr.isObjectType()", "repl.isObjectType()", "inBounds(rcvr.instsize(), rcvr.getObjectLength(), start, stop, repl.instsize(), repl.getObjectLength(), replStart)"})
        protected static final boolean doArraysOfObjects(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart) {
            System.arraycopy(repl.getObjectStorage(), replStart - 1, rcvr.getObjectStorage(), start - 1, 1 + stop - start);
            return true;
        }

        @Specialization(guards = {"!rcvr.hasSameStorageType(repl)", "inBounds(rcvrLib.instsize(rcvr), rcvrLib.size(rcvr), start, stop, replLib.instsize(repl), replLib.size(repl), replStart)"})
        protected static final boolean doArraysWithDifferenStorageTypes(final ArrayObject rcvr, final int start, final int stop, final ArrayObject repl, final int replStart,
                        @CachedLibrary("rcvr") final SqueakObjectLibrary rcvrLib,
                        @CachedLibrary(limit = "3") final SqueakObjectLibrary replLib) {
            final int repOff = replStart - start;
            for (int i = start - 1; i < stop; i++) {
                rcvrLib.atput0(rcvr, i, replLib.at0(repl, repOff + i));
            }
            return true;
        }

        @Specialization(guards = {"!isArrayObject(repl)", "inBounds(rcvr.instsize(), rcvrLib.size(rcvr), start, stop, replLib.instsize(repl), replLib.size(repl), replStart)"})
        protected static final boolean doArrayGeneric(final ArrayObject rcvr, final int start, final int stop, final Object repl, final int replStart,
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
        protected static final boolean doFail(final ArrayObject rcvr, final int start, final int stop, final Object repl, final int replStart) {
            return false;
        }
    }

    @ExportMessage
    public static class Size {
        @Specialization(guards = "obj.isEmptyType()")
        protected static final int doEmptyArrayObject(final ArrayObject obj) {
            return obj.getEmptyStorage();
        }

        @Specialization(guards = "obj.isBooleanType()")
        protected static final int doArrayObjectOfBooleans(final ArrayObject obj) {
            return obj.getBooleanLength();
        }

        @Specialization(guards = "obj.isCharType()")
        protected static final int doArrayObjectOfChars(final ArrayObject obj) {
            return obj.getCharLength();
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final int doArrayObjectOfLongs(final ArrayObject obj) {
            return obj.getLongLength();
        }

        @Specialization(guards = "obj.isDoubleType()")
        protected static final int doArrayObjectOfDoubles(final ArrayObject obj) {
            return obj.getDoubleLength();
        }

        @Specialization(guards = "obj.isNativeObjectType()")
        protected static final int doArrayObjectOfNatives(final ArrayObject obj) {
            return obj.getNativeObjectLength();
        }

        @Specialization(guards = "obj.isObjectType()")
        protected static final int doArrayObjectOfObjects(final ArrayObject obj) {
            return obj.getObjectLength();
        }
    }

    @ExportMessage
    public static class ShallowCopy {
        @Specialization(guards = "obj.isEmptyType()")
        protected static final ArrayObject doEmptyArray(final ArrayObject obj) {
            return ArrayObject.createWithStorage(obj.image, obj.getSqueakClass(), obj.getEmptyStorage());
        }

        @Specialization(guards = "obj.isBooleanType()")
        protected static final ArrayObject doArrayOfBooleans(final ArrayObject obj) {
            return ArrayObject.createWithStorage(obj.image, obj.getSqueakClass(), obj.getBooleanStorage().clone());
        }

        @Specialization(guards = "obj.isCharType()")
        protected static final ArrayObject doArrayOfChars(final ArrayObject obj) {
            return ArrayObject.createWithStorage(obj.image, obj.getSqueakClass(), obj.getCharStorage().clone());
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final ArrayObject doArrayOfLongs(final ArrayObject obj) {
            return ArrayObject.createWithStorage(obj.image, obj.getSqueakClass(), obj.getLongStorage().clone());
        }

        @Specialization(guards = "obj.isDoubleType()")
        protected static final ArrayObject doArrayOfDoubles(final ArrayObject obj) {
            return ArrayObject.createWithStorage(obj.image, obj.getSqueakClass(), obj.getDoubleStorage().clone());
        }

        @Specialization(guards = "obj.isNativeObjectType()")
        protected static final ArrayObject doArrayOfNatives(final ArrayObject obj) {
            return ArrayObject.createWithStorage(obj.image, obj.getSqueakClass(), obj.getNativeObjectStorage().clone());
        }

        @Specialization(guards = "obj.isObjectType()")
        protected static final ArrayObject doArrayOfObjects(final ArrayObject obj) {
            return ArrayObject.createWithStorage(obj.image, obj.getSqueakClass(), obj.getObjectStorage().clone());
        }
    }
}
