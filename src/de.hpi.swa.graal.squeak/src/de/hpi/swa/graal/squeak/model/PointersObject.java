package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.nodes.accessing.UpdateSqueakObjectHashNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

@ExportLibrary(SqueakObjectLibrary.class)
public final class PointersObject extends AbstractPointersObject {

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

    public void atputNil0(final long i) {
        setPointer((int) i, NilObject.SINGLETON);
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

    @ExportMessage
    public Object at0(final int i) {
        return getPointer(i);
    }

    @ExportMessage
    public void atput0(final int i, final Object obj) {
        assert obj != null; // null indicates a problem
        setPointer(i, obj);
    }

// @ExportMessage
    public boolean become(final Object otherObject) {
        if (!(otherObject instanceof PointersObject)) {
            return false;
        }
        final PointersObject other = (PointersObject) otherObject;
        becomeOtherClass(other);
        final Object[] otherPointers = other.getPointers();
        other.setPointers(getPointers());
        setPointers(otherPointers);
        return true;
    }

// @ExportMessage
    public void pointersBecomeOneWay(final Object[] from, final Object[] to, final boolean copyHash,
                    @Cached final UpdateSqueakObjectHashNode updateHashNode) {
        for (int i = 0; i < from.length; i++) {
            final Object fromPointer = from[i];
            for (int j = 0; j < pointers.length; j++) {
                final Object newPointer = pointers[j];
                if (newPointer == fromPointer) {
                    final Object toPointer = to[i];
                    pointers[j] = toPointer;
                    updateHashNode.executeUpdate(fromPointer, toPointer, copyHash);
                }
            }
        }
    }

    @ExportMessage
    public int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @ExportMessage
    public int size() {
        return pointers.length;
    }

// @ExportMessage
    public PointersObject shallowCopy() {
        return new PointersObject(this);
    }
}
