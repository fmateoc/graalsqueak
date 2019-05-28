package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.SqueakLanguage;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ClassObject;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = Double.class)
final class DefaultDoubleExports {
    @ExportMessage
    public static ClassObject squeakClass(@SuppressWarnings("unused") final Double receiver, @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
        return image.smallFloatClass;
    }

    @ExportMessage
    public static long squeakHash(final Double receiver) {
        return Double.doubleToLongBits(receiver);
    }
}
