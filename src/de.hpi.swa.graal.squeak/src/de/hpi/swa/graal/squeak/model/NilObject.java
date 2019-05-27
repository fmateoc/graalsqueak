package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

@ExportLibrary(SqueakObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class NilObject extends AbstractSqueakObject {
    public static final NilObject SINGLETON = new NilObject();

    private NilObject() {
    }

    public static AbstractSqueakObject nullToNil(final AbstractSqueakObject object) {
        return object == null ? SINGLETON : object;
    }

    public static Object nullToNil(final Object object) {
        return object == null ? SINGLETON : object;
    }

    public static long getSqueakHash() {
        return 1L;
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

    @Override
    public String toString() {
        return "nil";
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isNull() {
        return true;
    }
}
