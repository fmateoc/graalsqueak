package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.SqueakLanguage;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

@ExportLibrary(SqueakObjectLibrary.class)
public final class CharacterObject extends AbstractSqueakObject {
    private final int value;

    private CharacterObject(final int value) {
        assert value > Character.MAX_VALUE : "CharacterObject should only be used for non-primitive chars.";
        this.value = value;
    }

    public static Object valueOf(final int value) {
        if (value <= Character.MAX_VALUE) {
            return (char) value;
        } else {
            return new CharacterObject(value);
        }
    }

    public long getValue() {
        return Integer.toUnsignedLong(value);
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    public Object at0(final int index) {
        throw SqueakException.create("Illegal state");
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    public void atput0(final int index, final Object object) {
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

    @ExportMessage
    public CharacterObject shallowCopy() {
        return new CharacterObject(value);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public ClassObject squeakClass(@CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
        return image.characterClass;
    }

    @ExportMessage
    public long squeakHash() {
        return value;
    }
}
