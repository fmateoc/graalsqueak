package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

@ExportLibrary(SqueakObjectLibrary.class)
public final class EmptyObject extends AbstractSqueakObjectWithClassAndHash {

    public EmptyObject(final SqueakImageContext image, final ClassObject classObject) {
        super(image, classObject);
    }

    public EmptyObject(final SqueakImageContext image, final long hash, final ClassObject classObject) {
        super(image, hash, classObject);
    }

    private EmptyObject(final EmptyObject original) {
        super(original.image, original.getSqueakClass());
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        // Nothing to do.
    }

    @ExportMessage
    public Object at0(final int index) {
        throw SqueakException.create("Illegal state");
    }

    @ExportMessage
    public void atput0(final int index, final Object value) {
        throw SqueakException.create("Illegal state");
    }

    @ExportMessage
    public int instsize() {
        return 0;
    }

    @ExportMessage
    public int size() {
        return 0;
    }

    public void become(final EmptyObject other) {
        becomeOtherClass(other);
    }

    public EmptyObject shallowCopy() {
        return new EmptyObject(this);
    }
}
