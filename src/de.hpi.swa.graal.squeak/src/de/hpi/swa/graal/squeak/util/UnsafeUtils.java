package de.hpi.swa.graal.squeak.util;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public final class UnsafeUtils {

    public static final Unsafe UNSAFE = initUnsafe();

    private UnsafeUtils() {
    }

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (final SecurityException se) {
            // Slow path when we are not trusted.
            try {
                final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (final Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    public static byte getByte(final Object bytes, final long index) {
        assert bytes.getClass() == byte[].class;
        return UNSAFE.getByte(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static char getChar(final Object chars, final long index) {
        assert chars.getClass() == char[].class;
        return UNSAFE.getChar(chars, Unsafe.ARRAY_CHAR_BASE_OFFSET + index * Unsafe.ARRAY_CHAR_INDEX_SCALE);
    }

    public static double getDouble(final Object doubles, final long index) {
        assert doubles.getClass() == double[].class;
        return UNSAFE.getDouble(doubles, Unsafe.ARRAY_DOUBLE_BASE_OFFSET + index * Unsafe.ARRAY_DOUBLE_INDEX_SCALE);
    }

    public static int getInt(final byte[] bytes, final long i) {
        assert i * ArrayConversionUtils.INTEGER_BYTE_SIZE < bytes.length;
        return UnsafeUtils.UNSAFE.getInt(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * ArrayConversionUtils.INTEGER_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static int getInt(final Object ints, final long index) {
        assert ints.getClass() == int[].class;
        return UNSAFE.getInt(ints, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE);
    }

    public static int getIntReversed(final byte[] bytes, final long i) {
        return Integer.reverseBytes(getInt(bytes, i));
    }

    public static long getLong(final byte[] bytes, final long i) {
        assert i * ArrayConversionUtils.LONG_BYTE_SIZE < bytes.length;
        return UnsafeUtils.UNSAFE.getLong(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * ArrayConversionUtils.LONG_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static long getLong(final Object longs, final long index) {
        assert longs.getClass() == long[].class;
        return UNSAFE.getLong(longs, Unsafe.ARRAY_LONG_BASE_OFFSET + index * Unsafe.ARRAY_LONG_INDEX_SCALE);
    }

    public static long getLongReversed(final byte[] bytes, final long i) {
        return Long.reverseBytes(getLong(bytes, i));
    }

    public static Object getObject(final Object objects, final long index) {
        assert objects.getClass() == Object[].class;
        return UNSAFE.getObject(objects, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
    }

    public static short getShort(final byte[] bytes, final long i) {
        assert i * ArrayConversionUtils.SHORT_BYTE_SIZE < bytes.length;
        return UnsafeUtils.UNSAFE.getShort(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * ArrayConversionUtils.SHORT_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    public static short getShort(final Object shorts, final long index) {
        assert shorts.getClass() == short[].class;
        return UNSAFE.getShort(shorts, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE);
    }

    public static short getShortReversed(final byte[] bytes, final long i) {
        return Short.reverseBytes(getShort(bytes, i));
    }

    public static void putByte(final Object bytes, final long index, final byte value) {
        assert bytes.getClass() == byte[].class;
        UNSAFE.putByte(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    public static void putChar(final Object chars, final long index, final char value) {
        assert chars.getClass() == char[].class;
        UNSAFE.putChar(chars, Unsafe.ARRAY_CHAR_BASE_OFFSET + index * Unsafe.ARRAY_CHAR_INDEX_SCALE, value);
    }

    public static void putDouble(final Object doubles, final long index, final double value) {
        assert doubles.getClass() == double[].class;
        UNSAFE.putDouble(doubles, Unsafe.ARRAY_DOUBLE_BASE_OFFSET + index * Unsafe.ARRAY_DOUBLE_INDEX_SCALE, value);
    }

    public static void putInt(final byte[] bytes, final long i, final int value) {
        assert i * ArrayConversionUtils.SHORT_BYTE_SIZE < bytes.length;
        UnsafeUtils.UNSAFE.putInt(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * ArrayConversionUtils.INTEGER_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    public static void putInt(final Object ints, final long index, final int value) {
        assert ints.getClass() == int[].class;
        UNSAFE.putInt(ints, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE, value);
    }

    public static void putIntReversed(final byte[] bytes, final long i, final int value) {
        putInt(bytes, i, Integer.reverseBytes(value));
    }

    public static void putLong(final byte[] bytes, final long i, final long value) {
        assert i * ArrayConversionUtils.INTEGER_BYTE_SIZE < bytes.length;
        UnsafeUtils.UNSAFE.putLong(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * ArrayConversionUtils.LONG_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    public static void putLong(final Object longs, final long index, final long value) {
        assert longs.getClass() == long[].class;
        UNSAFE.putLong(longs, Unsafe.ARRAY_LONG_BASE_OFFSET + index * Unsafe.ARRAY_LONG_INDEX_SCALE, value);
    }

    public static void putLongReversed(final byte[] bytes, final long i, final long value) {
        putLong(bytes, i, Long.reverseBytes(value));
    }

    public static void putObject(final Object objects, final long index, final Object value) {
        assert objects.getClass() == Object[].class;
        UNSAFE.putObject(objects, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE, value);
    }

    public static void putShort(final byte[] bytes, final long i, final short value) {
        assert i * ArrayConversionUtils.SHORT_BYTE_SIZE < bytes.length;
        UnsafeUtils.UNSAFE.putShort(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * ArrayConversionUtils.SHORT_BYTE_SIZE * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
    }

    public static void putShort(final Object shorts, final long index, final short value) {
        assert shorts.getClass() == short[].class;
        UNSAFE.putShort(shorts, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE, value);
    }

    public static void putShortReversed(final byte[] bytes, final long i, final short value) {
        putShort(bytes, i, Short.reverseBytes(value));
    }
}
