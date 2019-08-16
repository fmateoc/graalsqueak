package de.hpi.swa.graal.squeak.model;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import sun.misc.Unsafe;

@ExportLibrary(InteropLibrary.class)
public final class PointersObject extends AbstractPointersObject {

    private long nativeStorage;

    private static final Unsafe unsafe = getUnsafe();

    public PointersObject(final SqueakImageContext image) {
        super(image); // for special PointersObjects only
    }

    public PointersObject(final SqueakImageContext image, final long hash, final ClassObject klass) {
        super(image, hash, klass);
    }

    public PointersObject(final SqueakImageContext image, final ClassObject sqClass, final Object[] pointers) {
        super(image, sqClass);
        setPointersUnsafe(pointers);
    }

    public PointersObject(final SqueakImageContext image, final ClassObject classObject, final int size) {
        this(image, classObject, ArrayUtils.withAll(size, NilObject.SINGLETON));
    }

    private PointersObject(final PointersObject original) {
        super(original.image, original.getSqueakClass());
        setPointersUnsafe(original.getPointers().clone());
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        setPointers(chunk.getPointers());
    }

    public Object at0(final int i) {
        return getPointer(i);
    }

    public void atput0(final long i, final Object obj) {
        assert obj != null; // null indicates a problem
        setPointer((int) i, obj);
    }

    public void atputNil0(final long i) {
        setPointer((int) i, NilObject.SINGLETON);
    }

    public void become(final PointersObject other) {
        becomeOtherClass(other);
        final Object[] otherPointers = other.getPointers();
        other.setPointers(getPointers());
        setPointers(otherPointers);
    }

    public boolean isActiveProcess() {
        return this == image.getActiveProcess();
    }

    public boolean isEmptyList() {
        return at0(LINKED_LIST.FIRST_LINK) == NilObject.SINGLETON;
    }

    public boolean isDisplay() {
        return this == image.getSpecialObject(SPECIAL_OBJECT.THE_DISPLAY);
    }

    public boolean isPoint() {
        return getSqueakClass() == image.pointClass;
    }

    public PointersObject removeFirstLinkOfList() {
        // Remove the first process from the given linked list.
        final PointersObject first = (PointersObject) at0(LINKED_LIST.FIRST_LINK);
        final Object last = at0(LINKED_LIST.LAST_LINK);
        if (first == last) {
            atput0(LINKED_LIST.FIRST_LINK, NilObject.SINGLETON);
            atput0(LINKED_LIST.LAST_LINK, NilObject.SINGLETON);
        } else {
            atput0(LINKED_LIST.FIRST_LINK, first.at0(PROCESS.NEXT_LINK));
        }
        first.atput0(PROCESS.NEXT_LINK, NilObject.SINGLETON);
        return first;
    }

    public PointersObject shallowCopy() {
        return new PointersObject(this);
    }

    @SuppressWarnings("restriction")
    private static Unsafe getUnsafe() {
        try {
            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (final Exception e) {
            return null;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public void toNative() {
        if (!isPointer()) { // FIXME: check for external* and avoid ClassCastException
            final byte[] bytes = ((NativeObject) at0(0)).getByteStorage();
            final int length = bytes.length;
            nativeStorage = unsafe.allocateMemory(length);
            // FIXME: something is wrong with the conversion:
            for (int i = 0; i < length; i++) {
                unsafe.putByte(nativeStorage + i, bytes[i]);
            }
        }
    }

    @ExportMessage
    public boolean isPointer() {
        return nativeStorage != 0;
    }

    @ExportMessage
    public long asPointer() {
        return nativeStorage;
    }
}
