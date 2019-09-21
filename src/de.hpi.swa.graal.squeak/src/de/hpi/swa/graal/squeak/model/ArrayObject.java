package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.interop.WrapToSqueakNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectWriteNode;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

public final class ArrayObject extends AbstractSqueakObjectWithClassAndHash {
    public static final boolean ENABLE_STORAGE_STRATEGIES = true;
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, ArrayObject.class);

    private Object storage;
    private boolean[] isPrimitiveSetMap;

    public ArrayObject(final SqueakImageContext image) {
        super(image); // for special ArrayObjects only
    }

    private ArrayObject(final SqueakImageContext image, final ClassObject classObject, final Object storage) {
        super(image, classObject);
        this.storage = storage;
        assert isFilledCorrectly();
    }

    private ArrayObject(final SqueakImageContext image, final ClassObject classObject, final Object storage, final boolean[] isPrimitiveSetMap) {
        super(image, classObject);
        this.storage = storage;
        this.isPrimitiveSetMap = isPrimitiveSetMap;
    }

    public ArrayObject(final SqueakImageContext image, final long hash, final ClassObject klass) {
        super(image, hash, klass);
    }

    private ArrayObject(final ArrayObject original, final Object storageCopy) {
        super(original.image, original.getSqueakClass());
        assert storage != storageCopy;
        storage = storageCopy;
        if (original.isPrimitiveSetMap != null) {
            isPrimitiveSetMap = original.isPrimitiveSetMap.clone();
        }
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

    public static ArrayObject createWithStorage(final SqueakImageContext image, final ClassObject classObject, final long[] storage) {
        return new ArrayObject(image, classObject, storage, ArrayUtils.allTrueArray(storage.length));
    }

    public static ArrayObject createWithStorage(final SqueakImageContext image, final ClassObject classObject, final Object storage, final boolean[] isPrimitiveSetMap) {
        return new ArrayObject(image, classObject, storage, isPrimitiveSetMap);
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
            final ArrayObjectWriteNode writeNode = ArrayObjectWriteNode.getUncached();
            for (int i = 0; i < pointers.length; i++) {
                writeNode.execute(this, i, pointers[i]);
            }
        }
        assert isPrimitiveSetMap == null && (isEmptyType() || isNativeObjectType() || isObjectType()) || isPrimitiveSetMap != null;
    }

    public void become(final ArrayObject other) {
        becomeOtherClass(other);
        final Object otherStorage = other.storage;
        final boolean[] otherisPrimitiveSetMap = other.isPrimitiveSetMap;
        other.setStorage(storage);
        setStorage(otherStorage);
        isPrimitiveSetMap = otherisPrimitiveSetMap;
    }

    public boolean containsNil() {
        assert isBooleanType() || isCharType() || isDoubleType() || isLongType();
        for (final boolean isSet : isPrimitiveSetMap) {
            if (!isSet) {
                return true;
            }
        }
        return false;
    }

    public int getBooleanLength() {
        return getBooleanStorage().length;
    }

    public boolean[] getBooleanStorage() {
        assert isBooleanType();
        return (boolean[]) storage;
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
        return (Object[]) storage;
    }

    public Class<? extends Object> getStorageType() {
        return storage.getClass();
    }

    @Override
    public int instsize() {
        return 0;
    }

    @Override
    public int size() {
        throw SqueakException.create("Use ArrayObjectSizeNode");
    }

    public void setPrimitive(final int index) {
        isPrimitiveSetMap[index] = true;
    }

    public void unsetPrimitive(final int index) {
        isPrimitiveSetMap[index] = false;
    }

    public boolean[] getIsPrimitiveSetMap() {
        return isPrimitiveSetMap;
    }

    public boolean isPrimitiveSet(final int index) {
        return isPrimitiveSetMap[index];
    }

    public boolean isBooleanType() {
        return storage instanceof boolean[];
    }

    public boolean isCharType() {
        return storage instanceof char[];
    }

    public boolean isDoubleType() {
        return storage instanceof double[];
    }

    public boolean isEmptyType() {
        return storage instanceof Integer;
    }

    public boolean isLongType() {
        return storage instanceof long[];
    }

    public boolean isNativeObjectType() {
        return storage instanceof NativeObject[];
    }

    public boolean isObjectType() {
        // Cannot use instanceof here (NativeObject[] inherits from Object[]).
        return storage.getClass() == Object[].class;
    }

    public boolean isTraceable() {
        return isObjectType() || isNativeObjectType();
    }

    private boolean isFilledCorrectly() {
        return !(isPrimitiveSetMap != null ^ (isBooleanType() || isCharType() || isDoubleType() || isLongType()));
    }

    public boolean hasSameStorageType(final ArrayObject other) {
        return storage.getClass() == other.storage.getClass();
    }

    public void setStorage(final long[] newStorage, final boolean[] newIsPrimitiveSetMap) {
        storage = newStorage;
        isPrimitiveSetMap = newIsPrimitiveSetMap;
    }

    public void setStorage(final Object newStorage) {
        storage = newStorage;
        assert isFilledCorrectly();
    }

    public ArrayObject shallowCopy(final Object storageCopy) {
        return new ArrayObject(this, storageCopy);
    }

    public void transitionFromBooleansToObjects() {
        LOG.finer("transition from Booleans to Objects");
        final boolean[] booleans = getBooleanStorage();
        final Object[] objects = new Object[booleans.length];
        for (int i = 0; i < booleans.length; i++) {
            objects[i] = isPrimitiveSet(i) ? booleans[i] : NilObject.SINGLETON;
        }
        storage = objects;
        isPrimitiveSetMap = null;
    }

    public void transitionFromCharsToObjects() {
        LOG.finer("transition from Chars to Objects");
        final char[] chars = getCharStorage();
        final Object[] objects = new Object[chars.length];
        for (int i = 0; i < chars.length; i++) {
            objects[i] = isPrimitiveSet(i) ? chars[i] : NilObject.SINGLETON;
        }
        storage = objects;
        isPrimitiveSetMap = null;
    }

    public void transitionFromDoublesToObjects() {
        LOG.finer("transition from Doubles to Objects");
        final double[] doubles = getDoubleStorage();
        final Object[] objects = new Object[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            objects[i] = isPrimitiveSet(i) ? doubles[i] : NilObject.SINGLETON;
        }
        storage = objects;
        isPrimitiveSetMap = null;
    }

    public void transitionFromEmptyToBooleans() {
        final int size = getEmptyLength();
        storage = new boolean[size];
        isPrimitiveSetMap = new boolean[size];
    }

    public void transitionFromEmptyToChars() {
        final int size = getEmptyLength();
        final char[] chars = new char[size];
        storage = chars;
        isPrimitiveSetMap = new boolean[size];
    }

    public void transitionFromEmptyToDoubles() {
        final int size = getEmptyLength();
        final double[] doubles = new double[size];
        storage = doubles;
        isPrimitiveSetMap = new boolean[size];
    }

    public void transitionFromEmptyToLongs() {
        final int size = getEmptyLength();
        final long[] longs = new long[size];
        storage = longs;
        isPrimitiveSetMap = new boolean[size];
    }

    public void transitionFromEmptyToNatives() {
        storage = new NativeObject[getEmptyLength()];
    }

    public void transitionFromEmptyToObjects() {
        storage = ArrayUtils.withAll(getEmptyLength(), NilObject.SINGLETON);
    }

    public void transitionFromLongsToObjects() {
        LOG.finer("transition from Longs to Objects");
        final long[] longs = getLongStorage();
        final Object[] objects = new Object[longs.length];
        for (int i = 0; i < longs.length; i++) {
            objects[i] = isPrimitiveSet(i) ? longs[i] : NilObject.SINGLETON;
        }
        storage = objects;
    }

    public void transitionFromNativesToObjects() {
        LOG.finer("transition from NativeObjects to Objects");
        final NativeObject[] natives = getNativeObjectStorage();
        final Object[] objects = new Object[natives.length];
        for (int i = 0; i < natives.length; i++) {
            objects[i] = natives[i] != null ? natives[i] : NilObject.SINGLETON;
        }
        storage = objects;
    }

    /*
     * INTEROPERABILITY
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    protected boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    protected long getArraySize(@Shared("sizeNode") @Cached final ArrayObjectSizeNode sizeNode) {
        return sizeNode.execute(this);
    }

    @SuppressWarnings("static-method")
    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    @ExportMessage(name = "isArrayElementInsertable")
    protected boolean isArrayElementReadable(final long index, @Shared("sizeNode") @Cached final ArrayObjectSizeNode sizeNode) {
        return 0 <= index && index < sizeNode.execute(this);
    }

    @ExportMessage
    protected Object readArrayElement(final long index, @Cached final ArrayObjectReadNode readNode) {
        return readNode.execute(this, (int) index);
    }

    @ExportMessage
    protected void writeArrayElement(final long index, final Object value,
                    @Exclusive @Cached final WrapToSqueakNode wrapNode,
                    @Cached final ArrayObjectWriteNode writeNode) throws InvalidArrayIndexException {
        try {
            writeNode.execute(this, (int) index, wrapNode.executeWrap(value));
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw InvalidArrayIndexException.create(index);
        }
    }
}
