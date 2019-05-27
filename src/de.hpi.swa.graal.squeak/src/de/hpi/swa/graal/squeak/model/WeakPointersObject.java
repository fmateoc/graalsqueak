package de.hpi.swa.graal.squeak.model;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.nodes.accessing.UpdateSqueakObjectHashNode;
import de.hpi.swa.graal.squeak.nodes.accessing.WeakPointersObjectNodes.WeakPointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.WeakPointersObjectNodes.WeakPointersObjectWriteNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

@ExportLibrary(SqueakObjectLibrary.class)
public final class WeakPointersObject extends AbstractPointersObject {
    public WeakPointersObject(final SqueakImageContext image, final long hash, final ClassObject sqClass) {
        super(image, hash, sqClass);
    }

    public WeakPointersObject(final SqueakImageContext image, final ClassObject classObject, final int size) {
        super(image, classObject);
        setPointers(ArrayUtils.withAll(size, NilObject.SINGLETON));
    }

    private WeakPointersObject(final WeakPointersObject original) {
        super(original.image, original.getSqueakClass());
        setPointersUnsafe(original.getPointers().clone());
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        final Object[] pointersValues = chunk.getPointers();
        final int length = pointersValues.length;
        setPointers(new Object[length]);
        final WeakPointersObjectWriteNode writeNode = WeakPointersObjectWriteNode.getUncached();
        for (int i = 0; i < length; i++) {
            writeNode.execute(this, i, pointersValues[i]);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "WeakPointersObject: " + getSqueakClass();
    }

    public void setWeakPointer(final int index, final Object value) {
        setPointer(index, new WeakReference<>(value, image.weakPointersQueue));
    }

    @ExportMessage
    public Object at0(final int index,
                    @Cached final WeakPointersObjectReadNode readNode) {
        return readNode.executeRead(this, index);
    }

    @ExportMessage
    public void atput0(final int index, final Object value,
                    @Cached final WeakPointersObjectWriteNode writeNode) {
        writeNode.execute(this, index, value);
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

    @ExportMessage
    public WeakPointersObject shallowCopy() {
        return new WeakPointersObject(this);
    }
}
