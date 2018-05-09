package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions;
import de.hpi.swa.graal.squeak.exceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.AbstractImageChunk;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.storages.AbstractNativeObjectStorage;
import de.hpi.swa.graal.squeak.model.storages.NativeBytesStorage;
import de.hpi.swa.graal.squeak.model.storages.NativeLongsStorage;
import de.hpi.swa.graal.squeak.model.storages.NativeShortsStorage;
import de.hpi.swa.graal.squeak.model.storages.NativeWordsStorage;

public class NativeObject extends AbstractSqueakObject {
    @CompilationFinal protected AbstractNativeObjectStorage storage;

    public static NativeObject newNativeBytes(final SqueakImageContext img, final ClassObject klass, final int size) {
        return new NativeObject(img, klass, new NativeBytesStorage(size));
    }

    public static NativeObject newNativeBytes(final SqueakImageContext img, final ClassObject klass, final byte[] bytes) {
        return new NativeObject(img, klass, new NativeBytesStorage(bytes));
    }

    public static NativeObject newNativeShorts(final SqueakImageContext img, final ClassObject klass, final int size) {
        return new NativeObject(img, klass, new NativeShortsStorage(size));
    }

    public static NativeObject newNativeShorts(final SqueakImageContext img, final ClassObject klass, final short[] shorts) {
        return new NativeObject(img, klass, new NativeShortsStorage(shorts));
    }

    public static NativeObject newNativeWords(final SqueakImageContext img, final ClassObject klass, final int size) {
        return new NativeObject(img, klass, new NativeWordsStorage(size));
    }

    public static NativeObject newNativeWords(final SqueakImageContext img, final ClassObject klass, final int[] words) {
        return new NativeObject(img, klass, new NativeWordsStorage(words));
    }

    public static NativeObject newNativeLongs(final SqueakImageContext img, final ClassObject klass, final int size) {
        return new NativeObject(img, klass, new NativeLongsStorage(size));
    }

    public static NativeObject newNativeLongs(final SqueakImageContext img, final ClassObject klass, final long[] longs) {
        return new NativeObject(img, klass, new NativeLongsStorage(longs));
    }

    public NativeObject(final SqueakImageContext img) {
        super(img);
    }

    public NativeObject(final SqueakImageContext img, final AbstractNativeObjectStorage storage) {
        super(img);
        this.storage = storage;
    }

    public NativeObject(final SqueakImageContext image, final ClassObject classObject) {
        super(image, classObject);
    }

    protected NativeObject(final SqueakImageContext image, final ClassObject classObject, final AbstractNativeObjectStorage storage) {
        this(image, classObject);
        this.storage = storage;
    }

    protected NativeObject(final NativeObject original) {
        this(original.image, original.getSqClass(), original.storage.shallowCopy());
    }

    @Override
    public void fillin(final AbstractImageChunk chunk) {
        super.fillin(chunk);
        storage.fillin(chunk);
    }

    @Override
    public final boolean become(final AbstractSqueakObject other) {
        if (!(other instanceof NativeObject)) {
            throw new PrimitiveExceptions.PrimitiveFailed();
        }
        if (!super.become(other)) {
            throw new SqueakException("Should not fail");
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        final NativeObject otherNativeObject = (NativeObject) other;
        final AbstractNativeObjectStorage otherStorage = otherNativeObject.storage;
        otherNativeObject.storage = this.storage;
        this.storage = otherStorage;
        return true;
    }

    @TruffleBoundary
    @Override
    public final String toString() {
        return new String(getBytes());
    }

    public final Object at0(final long index) {
        return getNativeAt0(index);
    }

    public final void atput0(final long index, final Object object) {
        if (object instanceof LargeIntegerObject) {
            final long longValue;
            try {
                longValue = ((LargeIntegerObject) object).reduceToLong();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(e.toString());
            }
            storage.setNativeAt0(index, longValue);
        } else {
            storage.setNativeAt0(index, (long) object);
        }
    }

    public final long getNativeAt0(final long index) {
        return storage.getNativeAt0(index);
    }

    public final void setNativeAt0(final long index, final long value) {
        storage.setNativeAt0(index, value);
    }

    public final long shortAt0(final long longIndex) {
        return storage.shortAt0(longIndex);
    }

    public final void shortAtPut0(final long longIndex, final long value) {
        storage.shortAtPut0(longIndex, value);
    }

    public final byte[] getBytes() {
        return storage.getBytes();
    }

    public int[] getWords() {
        return storage.getWords();
    }

    public void fillWith(final Object value) {
        storage.fillWith(value);
    }

    public int size() {
        return storage.size();
    }

    public byte getElementSize() {
        return storage.getElementSize();
    }

    public final LargeIntegerObject normalize() {
        return new LargeIntegerObject(image, getSqClass(), getBytes());
    }

    public AbstractSqueakObject shallowCopy() {
        return new NativeObject(this);
    }

    public final void setByte(final int index, final byte value) {
        storage.setByte(index, value);
    }

    public final int getInt(final int index) {
        return storage.getInt(index);
    }

    public final void setInt(final int index, final int value) {
        storage.setInt(index, value);
    }

    public final void convertStorage(final NativeObject argument) {
        if (getElementSize() == argument.getElementSize()) {
            return; // no need to covert storage
        }
        final byte[] oldBytes = getBytes();
        CompilerDirectives.transferToInterpreterAndInvalidate();
        switch (argument.getElementSize()) {
            case 1:
                storage = new NativeBytesStorage(oldBytes);
                return;
            case 2:
                storage = new NativeShortsStorage(0);
                break;
            case 4:
                storage = new NativeWordsStorage(0);
                break;
            case 8:
                storage = new NativeLongsStorage(0);
                break;
            default:
                throw new SqueakException("Should not happen");
        }
        storage.setBytes(oldBytes);
    }
}
