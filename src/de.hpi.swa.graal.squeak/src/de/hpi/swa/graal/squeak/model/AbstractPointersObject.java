package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.util.UnsafeUtils;

public abstract class AbstractPointersObject extends AbstractSqueakObjectWithClassAndHash {
    @CompilationFinal(dimensions = 0) private Object[] pointers;

    protected AbstractPointersObject(final SqueakImageContext image) {
        super(image);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final ClassObject sqClass) {
        super(image, sqClass);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final long hash, final ClassObject sqClass) {
        super(image, hash, sqClass);
    }

    public final Object getPointer(final long index) {
        return UnsafeUtils.getObject(pointers, index);
    }

    public final Object[] getPointers() {
        return pointers;
    }

    public final void setPointer(final long index, final Object value) {
        UnsafeUtils.putObject(pointers, index, value);
    }

    public final void setPointersUnsafe(final Object[] pointers) {
        this.pointers = pointers;
    }

    public final void setPointers(final Object[] pointers) {
        // TODO: find out if invalidation should be avoided by copying values if pointers != null
        CompilerDirectives.transferToInterpreterAndInvalidate();
        setPointersUnsafe(pointers);
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
