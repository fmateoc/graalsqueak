/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.util;

import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class ArrayConversionUtils {
    public static byte[] bytesFromInts(final int[] ints) {
        final int intsLength = ints.length;
        final byte[] bytes = new byte[intsLength * Integer.BYTES];
        for (int i = 0; i < intsLength; i++) {
            UnsafeUtils.putInt(bytes, i, ints[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromIntsReversed(final int[] ints) {
        final int intsLength = ints.length;
        final byte[] bytes = new byte[intsLength * Integer.BYTES];
        for (int i = 0; i < intsLength; i++) {
            UnsafeUtils.putIntReversed(bytes, i, ints[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromLongs(final long[] longs) {
        final int longsLength = longs.length;
        final byte[] bytes = new byte[longsLength * Long.BYTES];
        for (int i = 0; i < longsLength; i++) {
            UnsafeUtils.putLong(bytes, i, longs[i]);
        }
        return bytes;
    }

    public static byte[] bytesFromShorts(final short[] shorts) {
        final int shortLength = shorts.length;
        final byte[] bytes = new byte[shortLength * Short.BYTES];
        for (int i = 0; i < shortLength; i++) {
            UnsafeUtils.putShort(bytes, i, shorts[i]);
        }
        return bytes;
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
        final int size = bytes.length / Integer.BYTES;
        final int[] ints = new int[size];
        for (int i = 0; i < size; i++) {
            ints[i] = UnsafeUtils.getInt(bytes, i);
        }
        return ints;
    }

    public static int[] intsFromBytesExact(final byte[] bytes) {
        final int byteSize = bytes.length;
        final int intSize = byteSize / Integer.BYTES;
        final int size = Math.floorDiv(byteSize + 3, Integer.BYTES);
        final int[] ints = new int[size];
        for (int i = 0; i < intSize; i++) {
            ints[i] = UnsafeUtils.getInt(bytes, i);
        }
        for (int i = intSize; i < size; i++) {
            final int offset = i * Integer.BYTES;
            if (offset < byteSize) {
                ints[i] |= bytes[offset + 0] & 0xFF;
            }
            if (offset + 1 < byteSize) {
                ints[i] |= (bytes[offset + 1] & 0xFF) << 8;
            }
            if (offset + 2 < byteSize) {
                ints[i] |= (bytes[offset + 2] & 0xFF) << 16;
            }
        }
        return ints;
    }

    public static int[] intsFromBytesReversed(final byte[] bytes) {
        final int size = bytes.length / Integer.BYTES;
        final int[] ints = new int[size];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = UnsafeUtils.getIntReversed(bytes, i);
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

    public static byte[] largeIntegerBytesFromLong(final long longValue) {
        assert longValue != Long.MIN_VALUE : "Cannot convert long to byte[] (Math.abs(Long.MIN_VALUE) overflows).";
        final long longValuePositive = Math.abs(longValue);
        final int numBytes = largeIntegerByteSizeForLong(longValuePositive);
        final byte[] bytes = new byte[numBytes];
        for (int i = 0; i < numBytes; i++) {
            bytes[i] = (byte) (longValuePositive >> Long.BYTES * i);
        }
        return bytes;
    }

    public static int largeIntegerByteSizeForLong(final long longValue) {
        return Long.BYTES - Long.numberOfLeadingZeros(longValue) / Long.BYTES;
    }

    public static long[] longsFromBytes(final byte[] bytes) {
        final int size = bytes.length / Long.BYTES;
        final long[] longs = new long[size];
        for (int i = 0; i < size; i++) {
            longs[i] = UnsafeUtils.getLong(bytes, i);
        }
        return longs;
    }

    public static long[] longsFromBytesReversed(final byte[] bytes) {
        final int size = bytes.length / Long.BYTES;
        final long[] longs = new long[size];
        for (int i = 0; i < size; i++) {
            longs[i] = UnsafeUtils.getLongReversed(bytes, i);
        }
        return longs;
    }

    public static short[] shortsFromBytes(final byte[] bytes) {
        final int size = bytes.length / Short.BYTES;
        final short[] shorts = new short[size];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = UnsafeUtils.getShort(bytes, i);
        }
        return shorts;
    }

    public static short[] shortsFromBytesReversed(final byte[] bytes) {
        final int size = bytes.length / Short.BYTES;
        final short[] shorts = new short[size];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = UnsafeUtils.getShortReversed(bytes, i);
        }
        return shorts;
    }

    @TruffleBoundary
    public static byte[] stringToBytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @TruffleBoundary
    public static int[] stringToCodePointsArray(final String value) {
        return value.codePoints().toArray();
    }
}
