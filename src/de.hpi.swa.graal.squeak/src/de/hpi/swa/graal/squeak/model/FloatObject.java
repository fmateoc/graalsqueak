package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

@ExportLibrary(SqueakObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class FloatObject extends AbstractSqueakObjectWithClassAndHash {
    public static final int PRECISION = 53;
    public static final int EMIN = -1022;
    public static final int EMAX = 1023;
    public static final int WORD_LENGTH = 2;

    private double doubleValue;

    public FloatObject(final SqueakImageContext image) {
        super(image, image.floatClass);
    }

    private FloatObject(final FloatObject original) {
        super(original.image, original.getSqueakClass());
        doubleValue = original.doubleValue;
    }

    private FloatObject(final SqueakImageContext image, final double doubleValue) {
        this(image);
        this.doubleValue = doubleValue;
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        // Nothing to do.
    }

    public static Object boxIfNecessary(final SqueakImageContext image, final double value) {
        return Double.isFinite(value) ? value : new FloatObject(image, value);
    }

    public static FloatObject valueOf(final SqueakImageContext image, final double value) {
        return new FloatObject(image, value);
    }

    public static Object newFromChunkWords(final SqueakImageContext image, final int[] ints) {
        assert ints.length == 2 : "Unexpected number of int values for double conversion";
        final double value = Double.longBitsToDouble(Integer.toUnsignedLong(ints[1]) << 32 | Integer.toUnsignedLong(ints[0]));
        return boxIfNecessary(image, value);
    }

    public long getHigh() {
        return Integer.toUnsignedLong((int) (Double.doubleToRawLongBits(doubleValue) >> 32));
    }

    public long getLow() {
        return Integer.toUnsignedLong((int) Double.doubleToRawLongBits(doubleValue));
    }

    public void setHigh(final long value) {
        assert 0 <= value && value <= NativeObject.INTEGER_MAX;
        setWords(value, getLow());
    }

    public void setLow(final long value) {
        assert 0 <= value && value <= NativeObject.INTEGER_MAX;
        setWords(getHigh(), value);
    }

    private void setWords(final int high, final int low) {
        setWords(Integer.toUnsignedLong(high), Integer.toUnsignedLong(low));
    }

    private void setWords(final long high, final long low) {
        doubleValue = Double.longBitsToDouble(high << 32 | low);
    }

    public void setBytes(final byte[] bytes) {
        assert bytes.length == WORD_LENGTH * 4;
        final int high = (bytes[3] & 0xff) << 24 | (bytes[2] & 0xff) << 16 | (bytes[1] & 0xff) << 8 | bytes[0] & 0xff;
        final int low = (bytes[7] & 0xff) << 24 | (bytes[6] & 0xff) << 16 | (bytes[5] & 0xff) << 8 | bytes[4] & 0xff;
        setWords(high, low);
    }

    public byte[] getBytes() {
        return getBytes(doubleValue);
    }

    public static byte[] getBytes(final double value) {
        final long bits = Double.doubleToRawLongBits(value);
        return new byte[]{(byte) (bits >> 56), (byte) (bits >> 48), (byte) (bits >> 40), (byte) (bits >> 32),
                        (byte) (bits >> 24), (byte) (bits >> 16), (byte) (bits >> 8), (byte) bits};
    }

    public boolean isFinite() {
        return Double.isFinite(doubleValue);
    }

    public boolean isNaN() {
        return Double.isNaN(doubleValue);
    }

    public double getValue() {
        return doubleValue;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "" + doubleValue;
    }

    /*
     * SQUEAK OBJECT ACCESS
     */

    @ExportMessage
    public static class At0 {
        @Specialization(guards = "index == 0")
        protected static final long doFloatHigh(final FloatObject obj, @SuppressWarnings("unused") final int index) {
            return obj.getHigh();
        }

        @Specialization(guards = "index == 1")
        protected static final long doFloatLow(final FloatObject obj, @SuppressWarnings("unused") final int index) {
            return obj.getLow();
        }
    }

    @ExportMessage
    @ImportStatic(NativeObject.class)
    public static final class Atput0 {
        @Specialization(guards = {"index == 0", "value >= 0", "value <= INTEGER_MAX"})
        protected static void doFloatHigh(final FloatObject obj, @SuppressWarnings("unused") final int index, final long value) {
            obj.setHigh(value);
        }

        @Specialization(guards = {"index == 1", "value >= 0", "value <= INTEGER_MAX"})
        protected static void doFloatLow(final FloatObject obj, @SuppressWarnings("unused") final int index, final long value) {
            obj.setLow(value);
        }
    }

    @ExportMessage
    public static final class ChangeClassOfTo {
        @Specialization(guards = {"argument.isWords()"})
        protected static boolean doChangeClassOfTo(final FloatObject receiver, final ClassObject argument) {
            receiver.setSqueakClass(argument);
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static boolean doFail(final FloatObject receiver, final ClassObject argument) {
            return false;
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public int instsize() {
        return 0;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public int size() {
        return WORD_LENGTH;
    }

    @ExportMessage
    public FloatObject shallowCopy() {
        return new FloatObject(this);
    }

    @ImportStatic(NativeObject.class)
    @GenerateUncached
    protected abstract static class FloatObjectWriteNode extends Node {
        public abstract void execute(Object obj, int index, Object value);

        @Specialization(guards = {"index == 0", "value >= 0", "value <= INTEGER_MAX"})
        protected static final void doFloatHigh(final FloatObject obj, @SuppressWarnings("unused") final int index, final long value) {
            obj.setHigh(value);
        }

        @Specialization(guards = {"index == 1", "value >= 0", "value <= INTEGER_MAX"})
        protected static final void doFloatLow(final FloatObject obj, @SuppressWarnings("unused") final int index, final long value) {
            obj.setLow(value);
        }
    }

    /*
     * INTEROPERABILITY
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isNumber() {
        return true;
    }

    @ExportMessage
    public boolean fitsInByte() {
        return (byte) doubleValue == doubleValue;
    }

    @ExportMessage
    public boolean fitsInShort() {
        return (short) doubleValue == doubleValue;
    }

    @ExportMessage
    public boolean fitsInInt() {
        return (int) doubleValue == doubleValue;
    }

    @ExportMessage
    public boolean fitsInLong() {
        return (long) doubleValue == doubleValue;
    }

    @ExportMessage
    public boolean fitsInFloat() {
        return (float) doubleValue == doubleValue;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean fitsInDouble() {
        return true;
    }

    @ExportMessage
    public byte asByte() {
        return (byte) doubleValue;
    }

    @ExportMessage
    public short asShort() {
        return (short) doubleValue;
    }

    @ExportMessage
    public int asInt() {
        return (int) doubleValue;
    }

    @ExportMessage
    public long asLong() {
        return (long) doubleValue;
    }

    @ExportMessage
    public float asFloat() {
        return (float) doubleValue;
    }

    @ExportMessage
    public double asDouble() {
        return doubleValue;
    }

}
