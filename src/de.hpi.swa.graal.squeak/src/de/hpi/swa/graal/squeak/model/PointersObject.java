package de.hpi.swa.graal.squeak.model;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.MUTEX;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.graal.squeak.nodes.accessing.PointersObjectNodes.PointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.PointersObjectNodes.PointersObjectWriteNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

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
        super(original);
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        setPointers(chunk.getPointers());
    }

    public Object at0(final int i) {
        return getPointer(i);
    }

    public void atput0(final long i, final Object obj) {
        assert obj != null : "Unexpected `null` value";
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

    public boolean isActiveProcess(final PointersObjectReadNode readNode) {
        return this == image.getActiveProcess(readNode);
    }

    public long getProcessPriority(final PointersObjectReadNode readNode) {
        return (long) readNode.executeRead(this, PROCESS.PRIORITY);
    }

    public Object getProcessListOrNil(final PointersObjectReadNode readNode) {
        return readNode.executeRead(this, PROCESS.LIST);
    }

    public Object getMutexOwnerOrNil(final PointersObjectReadNode readNode) {
        return readNode.executeRead(this, MUTEX.OWNER);
    }

    public boolean isEmptyList(final PointersObjectReadNode readNode) {
        return readNode.executeRead(this, LINKED_LIST.FIRST_LINK) == NilObject.SINGLETON;
    }

    public boolean isDisplay() {
        return this == image.getSpecialObject(SPECIAL_OBJECT.THE_DISPLAY);
    }

    public boolean isPoint() {
        return getSqueakClass() == image.pointClass;
    }

    public PointersObject removeFirstLinkOfList(final PointersObjectReadNode readNode, final PointersObjectWriteNode writeNode) {
        // Remove the first process from the given linked list.
        final PointersObject first = (PointersObject) readNode.executeRead(this, LINKED_LIST.FIRST_LINK);
        final Object last = readNode.executeRead(this, LINKED_LIST.LAST_LINK);
        if (first == last) {
            writeNode.executeWrite(this, LINKED_LIST.FIRST_LINK, NilObject.SINGLETON);
            writeNode.executeWrite(this, LINKED_LIST.LAST_LINK, NilObject.SINGLETON);
        } else {
            writeNode.executeWrite(this, LINKED_LIST.FIRST_LINK, readNode.executeRead(first, PROCESS.NEXT_LINK));
        }
        writeNode.executeWrite(first, PROCESS.NEXT_LINK, NilObject.SINGLETON);
        return first;
    }

    public PointersObject shallowCopy() {
        return new PointersObject(this);
    }
}
