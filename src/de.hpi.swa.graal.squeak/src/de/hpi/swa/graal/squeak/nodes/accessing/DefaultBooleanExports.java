package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = Boolean.class)
final class DefaultBooleanExports {
    @ExportMessage
    public static long squeakHash(final Boolean receiver) {
        return receiver ? 2L : 3L;
    }
}
