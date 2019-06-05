package de.hpi.swa.graal.squeak.model;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;

public final class ImmutableConsCharObject extends AbstractImmutableSqueakObjectWithClassAndHash {
    Object next;
    char value;

    public ImmutableConsCharObject(final SqueakImageContext image, final ClassObject sqClass, final char value, final Object next) {
        super(image, sqClass);
        this.next = next;
        this.value = value;
    }

    public ImmutableConsCharObject(final ImmutableConsCharObject original) {
        super(original.image, original.getSqueakClass());
        this.next = original.next;
        this.value = original.value;
    }

    public ImmutableConsCharObject(final PointersObject original) {
        super(original.image, original.getSqueakClass());
        /*if (original.getPointers().length != 2) {
            throw new Exception("Trying to create ImmutableConsCharObject from incompatible pointers objects");
        }
        if (!(original.getPointers()[0] instanceof Character)) {
            throw new Exception("Trying to create ImmutableConsCharObject from incompatible pointers objects");
        }*/
        this.value = (Character)original.getPointers()[0];
        this.next = original.getPointers()[1];
    }

    public Object at0(final long i) throws IndexOutOfBoundsException {
        if (i == 0) {
            return this.value;
        } else if (i == 1) {
            return this.next;
        } else {
            throw new IndexOutOfBoundsException("Trying to access pointer at index " + i + " for immutable cons char object");
        }
    }

    public ImmutableConsCharObject shallowCopy() {
        return new ImmutableConsCharObject(this);
    }

    @Override
    public final int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @Override
    public final int size() {
        return 2;
    }
}
