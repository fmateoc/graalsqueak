package de.hpi.swa.graal.squeak.util;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import sun.misc.Unsafe;

public final class ArrayConversionUtils {
    public static final int SHORT_BYTE_SIZE = 2;
    public static final int INTEGER_BYTE_SIZE = 4;
    public static final int LONG_BYTE_SIZE = 8;

    public static byte[] bytesFromInts(final int[] ints) {
        final int intsLength = ints.length;
        final byte[] bytes = new byte[intsLength * INTEGER_BYTE_SIZE];
        for (int i = 0; i < intsLength; i++) {
            putIntReversed(bytes, i, ints[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromIntsReversed(final int[] ints) {
        final int intsLength = ints.length;
        final byte[] bytes = new byte[intsLength * INTEGER_BYTE_SIZE];
        for (int i = 0; i < intsLength; i++) {
            putInt(bytes, i, ints[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromLongs(final long[] longs) {
        final int longsLength = longs.length;
        final byte[] bytes = new byte[longsLength * LONG_BYTE_SIZE];
        for (int i = 0; i < longsLength; i++) {
            putLongReversed(bytes, i, longs[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromLongsReversed(final long[] longs) {
        final int longsLength = longs.length;
        final byte[] bytes = new byte[longsLength * LONG_BYTE_SIZE];
        for (int i = 0; i < longsLength; i++) {
            putLong(bytes, i, longs[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromShorts(final short[] shorts) {
        final int shortLength = shorts.length;
        final byte[] bytes = new byte[shortLength * SHORT_BYTE_SIZE];
        for (int i = 0; i < shortLength; i++) {
            putShortReversed(bytes, i, shorts[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromShortsReversed(final short[] shorts) {
        final int shortLength = shorts.length;
        final byte[] bytes = new byte[shortLength * SHORT_BYTE_SIZE];
        for (int i = 0; i < shortLength; i++) {
            putShort(bytes, i, shorts[i]);
        }
        return bytes;
    }

    public static int[] bytesToInts(final byte[] bytes) {
        final int length = bytes.length;
        final int[] ints = new int[length];
        for (int i = 0; i < length; i++) {
            ints[i] = bytes[i];
        }
        return ints;
    }

    @TruffleBoundary
    public static String bytesToString(final byte[] bytes) {
        return new String(bytes);
    }

    public static long[] bytesToUnsignedLongs(final byte[] bytes) {
        final int length = bytes.length;
        final long[] longs = new long[length];
        for (int i = 0; i < length; i++) {
            longs[i] = Byte.toUnsignedLong(bytes[i]);
        }
        return longs;
    }

    public static int[] intsFromBytes(final byte[] bytes) {
        final int size = bytes.length / INTEGER_BYTE_SIZE;
        final int[] ints = new int[size];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = getIntReversed(bytes, i);
        }
        return ints;
    }

    public static int[] intsFromBytesExact(final byte[] bytes) {
        final int byteSize = bytes.length;
        final int size = Math.floorDiv(byteSize + 3, INTEGER_BYTE_SIZE);
        final int[] ints = new int[size];
        for (int i = 0; i < size; i++) {
            ints[i] = getIntReversed(bytes, i);
        }
        return ints;
    }

    public static int[] intsFromBytesReversed(final byte[] bytes) {
        final int size = bytes.length / INTEGER_BYTE_SIZE;
        final int[] ints = new int[size];
        for (int i = 0; i < size; i++) {
            ints[i] = getInt(bytes, i);
        }
        return ints;
    }

    public static int[] intsFromBytesReversedExact(final byte[] bytes) {
        final int byteSize = bytes.length;
        final int size = Math.floorDiv(byteSize + 3, INTEGER_BYTE_SIZE);
        final int[] ints = new int[size];
        for (int i = 0; i < size; i++) {
            ints[i] = getInt(bytes, i);
        }
        return ints;
    }

    public static long[] intsToUnsignedLongs(final int[] ints) {
        final int length = ints.length;
        final long[] longs = new long[length];
        for (int i = 0; i < length; i++) {
            longs[i] = Integer.toUnsignedLong(ints[i]);
        }
        return longs;
    }

    public static int largeIntegerByteSizeForLong(final long longValue) {
        return LONG_BYTE_SIZE - Long.numberOfLeadingZeros(longValue) / LONG_BYTE_SIZE;
    }

    public static byte[] largeIntegerBytesFromLong(final long longValue) {
        assert longValue != Long.MIN_VALUE : "Cannot convert long to byte[] (Math.abs(Long.MIN_VALUE) overflows).";
        final long longValuePositive = Math.abs(longValue);
        final int numBytes = largeIntegerByteSizeForLong(longValuePositive);
        final byte[] bytes = new byte[numBytes];
        for (int i = 0; i < numBytes; i++) {
            bytes[i] = (byte) (longValuePositive >> LONG_BYTE_SIZE * i);
        }
        return bytes;
    }

    public static long[] longsFromBytes(final byte[] bytes) {
        final int size = bytes.length / LONG_BYTE_SIZE;
        final long[] longs = new long[size];
        for (int i = 0; i < size; i++) {
            longs[i] = getLongReversed(bytes, i);
        }
        return longs;
    }

    public static long[] longsFromBytesReversed(final byte[] bytes) {
        final int size = bytes.length / LONG_BYTE_SIZE;
        final long[] longs = new long[size];
        for (int i = 0; i < size; i++) {
            longs[i] = getLong(bytes, i);
        }
        return longs;
    }

    public static short[] shortsFromBytes(final byte[] bytes) {
        final int size = bytes.length / SHORT_BYTE_SIZE;
        final short[] shorts = new short[size];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = getShortReversed(bytes, i);
        }
        return shorts;
    }

    public static short[] shortsFromBytesReversed(final byte[] bytes) {
        final int size = bytes.length / SHORT_BYTE_SIZE;
        final short[] shorts = new short[size];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = getShort(bytes, i);
        }
        return shorts;
    }

    private static void putLong(final byte[] bytes, final long i, final long value) {
        assert i * INTEGER_BYTE_SIZE < bytes.length;
        MiscUtils.UNSAFE.putLong(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * LONG_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    private static void putLongReversed(final byte[] bytes, final long i, final long value) {
        putLong(bytes, i, Long.reverseBytes(value));
    }

    public static void putInt(final byte[] bytes, final long i, final int value) {
        assert i * SHORT_BYTE_SIZE < bytes.length;
        MiscUtils.UNSAFE.putInt(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * INTEGER_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    private static void putIntReversed(final byte[] bytes, final long i, final int value) {
        putInt(bytes, i, Integer.reverseBytes(value));
    }

    private static void putShort(final byte[] bytes, final long i, final short value) {
        assert i * SHORT_BYTE_SIZE < bytes.length;
        MiscUtils.UNSAFE.putShort(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * SHORT_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    private static void putShortReversed(final byte[] bytes, final long i, final short value) {
        putShort(bytes, i, Short.reverseBytes(value));
    }

    public static int getInt(final byte[] bytes, final long i) {
        assert i * INTEGER_BYTE_SIZE < bytes.length;
        return MiscUtils.UNSAFE.getInt(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * INTEGER_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static int getIntReversed(final byte[] bytes, final long i) {
        return Integer.reverseBytes(getInt(bytes, i));
    }

    public static long getLong(final byte[] bytes, final long i) {
        assert i * LONG_BYTE_SIZE < bytes.length;
        return MiscUtils.UNSAFE.getLong(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * LONG_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static long getLongReversed(final byte[] bytes, final long i) {
        return Long.reverseBytes(getLong(bytes, i));
    }

    public static short getShort(final byte[] bytes, final long i) {
        assert i * SHORT_BYTE_SIZE < bytes.length;
        return MiscUtils.UNSAFE.getShort(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * SHORT_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static short getShortReversed(final byte[] bytes, final long i) {
        return Short.reverseBytes(getShort(bytes, i));
    }

    @TruffleBoundary
    public static byte[] stringToBytes(final String value) {
        return value.getBytes();
    }

    @TruffleBoundary
    public static int[] stringToCodePointsArray(final String value) {
        return value.codePoints().toArray();
    }
}
