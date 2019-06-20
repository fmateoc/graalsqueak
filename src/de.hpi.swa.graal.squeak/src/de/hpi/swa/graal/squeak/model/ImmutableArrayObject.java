package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;

public final class ImmutableArrayObject extends AbstractImmutableSqueakObjectWithClassAndHash {
    @CompilationFinal(dimensions = 1) private Object[] storage;

    public ImmutableArrayObject(final SqueakImageContext image, final ClassObject sqClass, final Object[] storage) {
        super(image, sqClass);
        this.storage = storage;
    }

    public ImmutableArrayObject(final ImmutableArrayObject original) {
        super(original.image, original.getSqueakClass());
        storage = original.storage;
    }

    public ImmutableArrayObject(final ArrayObject original) {
        super(original.image, original.getSqueakClass());
        storage = original.getObjectStorage().clone();
    }

    public Object at0(final long i) {
        return storage[(int) i];
    }

    public boolean isPoint() {
        return getSqueakClass() == image.pointClass;
    }

    public ImmutableArrayObject shallowCopy() {
        return new ImmutableArrayObject(this);
    }

    @Override
    public final int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @Override
    public final int size() {
        return storage.length;
    }
}
