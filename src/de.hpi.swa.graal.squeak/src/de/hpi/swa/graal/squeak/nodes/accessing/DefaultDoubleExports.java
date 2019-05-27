package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = SqueakObjectLibrary.class, receiverType = Double.class)
final class DefaultDoubleExports {
    @ExportMessage
    public static long squeakHash(final Double receiver) {
        return Double.doubleToLongBits(receiver);
    }
}
