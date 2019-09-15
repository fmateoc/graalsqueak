package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.object.DynamicObject;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;

public abstract class AbstractPointersObject extends AbstractSqueakObjectWithClassAndHash {
    @CompilationFinal private DynamicObject storage;

    protected AbstractPointersObject(final SqueakImageContext image) {
        super(image);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final ClassObject sqClass) {
        super(image, sqClass);
        storage = sqClass.shape.newInstance();
    }

    protected AbstractPointersObject(final SqueakImageContext image, final long hash, final ClassObject sqClass) {
        super(image, hash, sqClass);
        storage = sqClass.shape.newInstance();
    }

    protected AbstractPointersObject(final AbstractPointersObject original) {
        super(original.image, original.getSqueakClass());
        storage = original.storage.copy(original.storage.getShape());
    }

    public final Object getPointer(final long index) {
        return storage.get(index);
    }

    public final Object[] getPointers() {
        final int size = size();
        final Object[] result = new Object[size];
        for (int i = 0; i < size; i++) {
            result[i] = getPointer(i);
        }
        return result;
    }

    public final void setPointer(final long index, final Object value) {
        getStorage().define(index, value);
    }

    public final void setPointersUnsafe(final Object[] pointers) {
        for (int i = 0; i < pointers.length; i++) {
            setPointer(i, pointers[i]);
        }
    }

    public final void setPointers(final Object[] pointers) {
        // TODO: find out if invalidation should be avoided by copying values if pointers != null
        setPointersUnsafe(pointers);
    }

    public DynamicObject getStorage() {
        if (storage == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            storage = getSqueakClass().shape.newInstance();
        }
        return storage;
    }

    @Override
    public final int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @Override
    public final int size() {
        return getStorage().size();
    }
}
