package de.hpi.swa.graal.squeak.math.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.math.BigInteger;

@ImportStatic(BigInteger.class)
public abstract class BigIntegerMultiplyNode extends Node {

    public abstract BigInteger executeMultiply(BigInteger left, Object right);

    @SuppressWarnings("unused")
    @Specialization(guards = {"left.signum == 0 || right.signum ==  0"})
    protected static final BigInteger doMultiplyZero(final BigInteger left, final BigInteger right) {
        return BigInteger.ZERO;
    }

    @Specialization(guards = {"isPowerOfTwo(b)"})
    protected static final BigInteger doLargeIntegerLongShift(final BigInteger a, @SuppressWarnings("unused") final long b) {
        final long shiftBy = Long.numberOfTrailingZeros(b);
        assert 0 < shiftBy && shiftBy <= Integer.MAX_VALUE && b == Long.highestOneBit(b);
        return a.shiftLeft((int) shiftBy);
    }

    @Specialization(guards = {"!isPowerOfTwo(b)"})
    protected static final BigInteger doLargeIntegerLong(final BigInteger a, final long b) {
        return a.multiply(b);
    }

    @Specialization(guards = {"left == right", "left.mag.length > MULTIPLY_SQUARE_THRESHOLD"})
    protected static final BigInteger doMultiplySquare(final BigInteger left, @SuppressWarnings("unused") final BigInteger right) {
        return left.square();
    }

    @Specialization(guards = {"left.mag.length == 1"})
    protected static final BigInteger doMultiplyBigIntegerInt(final BigInteger left, final BigInteger right) {
        return BigInteger.multiplyByInt(left.mag, right.mag[0], left.signum == right.signum ? 1 : -1);
    }

    @Specialization(guards = {"right.mag.length == 1"})
    protected static final BigInteger doMultiplyIntBigInteger(final BigInteger left, final BigInteger right) {
        return BigInteger.multiplyByInt(right.mag, left.mag[0], left.signum == right.signum ? 1 : -1);
    }

    @Specialization(guards = {"left.mag.length < KARATSUBA_THRESHOLD || right.mag.length < KARATSUBA_THRESHOLD", "left.mag.length != 1", "right.mag.length != 1"})
    protected static final BigInteger doMultiplyToLen(final BigInteger left, final BigInteger right) {
        int[] result = BigInteger.multiplyToLen(left.mag, left.mag.length,
                        right.mag, right.mag.length, null);
        result = BigInteger.trustedStripLeadingZeroInts(result);
        return new BigInteger(result, left.signum == right.signum ? 1 : -1);
    }

    @Specialization(guards = {"KARATSUBA_THRESHOLD <= left.mag.length", " left.mag.length< TOOM_COOK_THRESHOLD", "KARATSUBA_THRESHOLD <= right.mag.length", "right.mag.length < TOOM_COOK_THRESHOLD"})
    protected static final BigInteger doMultiplyKaratsuba(final BigInteger left, final BigInteger right) {
        return BigInteger.multiplyKaratsuba(left, right);
    }

    @Specialization(guards = {"left.mag.length >= TOOM_COOK_THRESHOLD || right.mag.length >= TOOM_COOK_THRESHOLD", "!checkOverflow(left, right)"})
    protected static final BigInteger doMultiplyToomCook(final BigInteger left, final BigInteger right) {
        return BigInteger.multiplyToomCook3(left, right);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"left.mag.length >= TOOM_COOK_THRESHOLD || right.mag.length >= TOOM_COOK_THRESHOLD", "checkOverflow(left, right)"})
    protected static final BigInteger doOverflow(final BigInteger left, final BigInteger right) {
        BigInteger.reportOverflow();
        assert false; // Is never reached
        return null;
    }

    protected static final boolean checkOverflow(final BigInteger left, final BigInteger right) {
        return BigInteger.bitLength(left.mag, left.mag.length) + BigInteger.bitLength(right.mag, right.mag.length) > 32L * BigInteger.MAX_MAG_LENGTH;
    }

    protected static final boolean isPowerOfTwo(final long value) {
        return value != 1 && (value & value - 1) == 0;
    }

    @Fallback
    protected static final BigInteger doMultiply(final BigInteger left, final Object right) {
        return left.multiply((BigInteger) right);
    }

}
