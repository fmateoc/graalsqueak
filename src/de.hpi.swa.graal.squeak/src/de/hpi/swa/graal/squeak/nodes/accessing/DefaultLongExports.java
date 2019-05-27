package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = Long.class)
final class DefaultLongExports {
    @ExportMessage
    public static long squeakHash(final Long receiver) {
        return receiver;
    }
}
