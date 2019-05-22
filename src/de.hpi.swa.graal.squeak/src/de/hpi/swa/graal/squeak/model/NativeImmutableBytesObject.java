package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.util.ArrayConversionUtils;
import java.util.Arrays;

@ExportLibrary(InteropLibrary.class)
public final class NativeImmutableBytesObject extends AbstractImmutableSqueakObjectWithClassAndHash {
    public static final short BYTE_MAX = (short) (Math.pow(2, Byte.SIZE) - 1);

    @CompilationFinal(dimensions = 1) private final byte[] storage;

    public NativeImmutableBytesObject(final NativeObject original){
        super(original.image, original.getSqueakClass());
        assert(original.isByteType());
        byte [] originalBytes = original.getByteStorage();
        storage = Arrays.copyOf(originalBytes, originalBytes.length);
    }

    public NativeImmutableBytesObject(final NativeImmutableBytesObject original){
        super(original.image, original.getSqueakClass());
        storage = original.getByteStorage();
    }

    @Override
    public int instsize() {
        return 0;
    }

    @Override
    public int size() {
        throw SqueakException.create("Use NativeImmutableBytesObjectSizeNode");
    }


    public int getByteLength() {
        return getByteStorage().length;
    }

    public byte[] getByteStorage() {
        return storage;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return asStringUnsafe();
    }

    /*
     * INTEROPERABILITY
     */

    public String asStringUnsafe() {
        return ArrayConversionUtils.bytesToString(getByteStorage());
    }

    @ExportMessage
    public boolean isString() {
        return getSqueakClass().isStringOrSymbolClass();
    }

    @ExportMessage
    public String asString() throws UnsupportedMessageException {
        if (isString()) {
            return asStringUnsafe();
        } else {
            throw UnsupportedMessageException.create();
        }
    }
}
