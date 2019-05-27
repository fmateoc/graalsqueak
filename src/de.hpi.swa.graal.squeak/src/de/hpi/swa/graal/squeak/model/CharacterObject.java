package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

@ExportLibrary(SqueakObjectLibrary.class)
public final class CharacterObject extends AbstractSqueakObjectWithClassAndHash {
    private final int value;

    private CharacterObject(final SqueakImageContext image, final int value) {
        super(image, image.characterClass);
        assert value > Character.MAX_VALUE : "CharacterObject should only be used for non-primitive chars.";
        this.value = value;
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        // Nothing to do.
    }

    public static Object valueOf(final SqueakImageContext image, final int value) {
        if (value <= Character.MAX_VALUE) {
            return (char) value;
        } else {
            return new CharacterObject(image, value);
        }
    }

    public long getValue() {
        return Integer.toUnsignedLong(value);
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
}
