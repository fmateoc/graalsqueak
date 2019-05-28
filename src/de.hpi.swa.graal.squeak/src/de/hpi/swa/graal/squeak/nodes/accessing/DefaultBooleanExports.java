package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.SqueakLanguage;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ClassObject;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = Boolean.class)
final class DefaultBooleanExports {
    @ExportMessage
    public static ClassObject squeakClass(final Boolean receiver, @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
        return receiver ? image.trueClass : image.falseClass;
    }

    @ExportMessage
    public static long squeakHash(final Boolean receiver) {
        return receiver ? 3L : 2L;
    }
}
