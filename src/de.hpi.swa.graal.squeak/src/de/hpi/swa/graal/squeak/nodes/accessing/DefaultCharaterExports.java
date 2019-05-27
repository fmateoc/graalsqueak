package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = Character.class)
final class DefaultCharaterExports {
    @ExportMessage
    public static long squeakHash(final Character receiver) {
        return receiver;
    }
}
