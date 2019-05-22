package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

public final class ImmutablePointersObject extends AbstractImmutableSqueakObjectWithClassAndHash {
    @CompilationFinal(dimensions = 1) private Object[] pointers;

    public ImmutablePointersObject(final SqueakImageContext image, final ClassObject sqClass, final Object[] pointers) {
        super(image, sqClass);
        this.pointers = pointers;
    }

    public ImmutablePointersObject(final ImmutablePointersObject original) {
        super(original.image, original.getSqueakClass());
        pointers = original.getPointers();
    }

    public ImmutablePointersObject(final PointersObject original) {
        super(original.image, original.getSqueakClass());
        pointers = original.getPointers().clone();
    }

    public Object at0(final long i) {
        return getPointer((int) i);
    }

    public boolean isActiveProcess() {
        return false;
    }

    public boolean isEmptyList() {
        return at0(LINKED_LIST.FIRST_LINK) == NilObject.SINGLETON;
    }

    public boolean isDisplay() {
        return this == image.specialObjectsArray.at0Object(SPECIAL_OBJECT.THE_DISPLAY);
    }

    public boolean isPoint() {
        return getSqueakClass() == image.pointClass;
    }

    public ImmutablePointersObject shallowCopy() {
        return new ImmutablePointersObject(this);
    }

    public final Object getPointer(final int index) {
        return pointers[index];
    }

    public final Object[] getPointers() {
        return pointers;
    }

    @Override
    public final int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @Override
    public final int size() {
        return pointers.length;
    }
}
