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

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    public Object at0(final int index) {
        throw SqueakException.create("Illegal state");
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    public void atput0(final int index, final Object value) {
        throw SqueakException.create("Illegal state");
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public int instsize() {
        return 0;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public int size() {
        return 0;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public NilObject shallowCopy() {
        return SINGLETON;
    }

    @ExportMessage
    public static long squeakHash(@SuppressWarnings("unused") final NilObject receiver) {
        return 1L;
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
