package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
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

    @ExportMessage
    protected static final class Become {
        @Specialization(guards = "receiver != other")
        protected static boolean doBecome(final EmptyObject receiver, final EmptyObject other) {
            receiver.becomeOtherClass(other);
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static boolean doFail(final EmptyObject receiver, final Object other) {
            return false;
        }
    }

    @ExportMessage
    protected static final class ChangeClassOfTo {
        @Specialization(guards = {"receiver.getSqueakClass().getFormat() == argument.getFormat()"})
        protected static boolean doChangeClassOfTo(final EmptyObject receiver, final ClassObject argument) {
            receiver.setSqueakClass(argument);
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static boolean doFail(final EmptyObject receiver, final ClassObject argument) {
            return false;
        }
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

    @ExportMessage
    public EmptyObject shallowCopy() {
        return new EmptyObject(this);
    }
}
