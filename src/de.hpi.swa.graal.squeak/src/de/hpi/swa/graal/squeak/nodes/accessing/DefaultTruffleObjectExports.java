package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.SqueakLanguage;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ClassObject;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = TruffleObject.class)
final class DefaultTruffleObjectExports {
    @ExportMessage
    public static ClassObject squeakClass(@SuppressWarnings("unused") final TruffleObject receiver, @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
        assert image.supportsTruffleObject();
        return image.truffleObjectClass;
    }

    @ExportMessage
    public static long squeakHash(final TruffleObject receiver) {
        return System.identityHashCode(receiver);
    }
}
