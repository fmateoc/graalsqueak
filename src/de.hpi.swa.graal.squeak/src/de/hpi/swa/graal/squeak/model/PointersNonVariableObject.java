package de.hpi.swa.graal.squeak.model;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.graal.squeak.nodes.ObjectGraphNode.ObjectTracer;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;

public final class PointersNonVariableObject extends AbstractPointersObject {

    public PointersNonVariableObject(final SqueakImageContext image) {
        super(image); // for special PointersObjects only
    }

    public PointersNonVariableObject(final SqueakImageContext image, final long hash, final ClassObject klass) {
        super(image, hash, klass);
    }

    public PointersNonVariableObject(final SqueakImageContext image, final ClassObject classObject) {
        super(image, classObject);
    }

    private PointersNonVariableObject(final PointersNonVariableObject original) {
        super(original);
    }

    public static PointersNonVariableObject create(final AbstractPointersObjectWriteNode writeNode, final ClassObject squeakClass, final Object... pointers) {
        final PointersNonVariableObject object = new PointersNonVariableObject(squeakClass.image, squeakClass);
        for (int i = 0; i < pointers.length; i++) {
            writeNode.execute(object, i, pointers[i]);
        }
        return object;
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        final AbstractPointersObjectWriteNode writeNode = AbstractPointersObjectWriteNode.getUncached();
        final Object[] pointersObject = chunk.getPointers();
        initializeLayoutAndExtensionsUnsafe();
        for (int i = 0; i < pointersObject.length; i++) {
            writeNode.execute(this, i, pointersObject[i]);
        }
        assert size() == pointersObject.length;
    }

    public Object at0(final int i) {
        if (!getLayout().isValid()) {
            updateLayout();
        }
        return getLayout().getLocation(i).read(this);
    }

    public void atput0(final int i, final Object value) {
        assert value != null : "Unexpected `null` value";
        if (!getLayout().getLocation(i).canStore(value)) {
            updateLayout(i, value);
        }
        getLayout().getLocation(i).writeMustSucceed(this, value);
    }

    @Override
    public int size() {
        return instsize();
    }

    public void atputNil0(final int i) {
        atput0(i, NilObject.SINGLETON);
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

    public PointersNonVariableObject removeFirstLinkOfList() {
        // Remove the first process from the given linked list.
        final PointersNonVariableObject first = (PointersNonVariableObject) at0(LINKED_LIST.FIRST_LINK);
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

    public PointersNonVariableObject shallowCopy() {
        return new PointersNonVariableObject(this);
    }

    public void traceObjects(final ObjectTracer tracer) {
        super.traceLayoutObjects(tracer);
    }
}
