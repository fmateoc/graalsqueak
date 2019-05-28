package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.SqueakLanguage;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ClassObject;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = Long.class)
final class DefaultLongExports {
    @ExportMessage
    public static ClassObject squeakClass(@SuppressWarnings("unused") final Long receiver, @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
        return image.smallIntegerClass;
    }

    @ExportMessage
    public static long squeakHash(final Long receiver) {
        return receiver;
    }
}
