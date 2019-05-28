package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.model.CharacterObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodesFactory.NativeAcceptsValueNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodesFactory.NativeGetBytesNodeGen;
import de.hpi.swa.graal.squeak.util.ArrayConversionUtils;

public final class NativeObjectNodes {

    @GenerateUncached
    public abstract static class NativeAcceptsValueNode extends AbstractNode {

        public static NativeAcceptsValueNode create() {
            return NativeAcceptsValueNodeGen.create();
        }

        public abstract boolean execute(NativeObject obj, Object value);

        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(@SuppressWarnings("unused") final NativeObject obj, final char value) {
            return value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final char value) {
            return value <= NativeObject.SHORT_MAX;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isIntType() || obj.isLongType()")
        protected static final boolean doNativeInts(final NativeObject obj, final char value) {
            return true;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(final NativeObject obj, final CharacterObject value) {
            return false; // Value of CharacterObjects is always larger than `Character.MAX_VALUE`.
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final CharacterObject value) {
            return value.getValue() <= NativeObject.SHORT_MAX;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isIntType() || obj.isLongType()")
        protected static final boolean doNativeInts(final NativeObject obj, final CharacterObject value) {
            return true;
        }

        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.SHORT_MAX;
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final boolean doNativeInts(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.INTEGER_MAX;
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final boolean doNativeLongs(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value;
        }

        @Specialization(guards = {"obj.isByteType()"})
        protected static final boolean doNativeBytesLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.BYTE_MAX);
        }

        @Specialization(guards = {"obj.isShortType()"})
        protected static final boolean doNativeShortsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.SHORT_MAX);
        }

        @Specialization(guards = {"obj.isIntType()"})
        protected static final boolean doNativeIntsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.INTEGER_MAX);
        }

        @Specialization(guards = {"obj.isLongType()"})
        protected static final boolean doNativeLongsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.isZeroOrPositive() && value.lessThanOneShiftedBy64();
        }
    }

    @GenerateUncached
    public abstract static class NativeGetBytesNode extends AbstractNode {

        public static NativeGetBytesNode create() {
            return NativeGetBytesNodeGen.create();
        }

        @TruffleBoundary
        public final String executeAsString(final NativeObject obj) {
            return new String(execute(obj));
        }

        public abstract byte[] execute(NativeObject obj);

        @Specialization(guards = "obj.isByteType()")
        protected static final byte[] doNativeBytes(final NativeObject obj) {
            return obj.getByteStorage();
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final byte[] doNativeShorts(final NativeObject obj) {
            return ArrayConversionUtils.bytesFromShortsReversed(obj.getShortStorage());
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final byte[] doNativeInts(final NativeObject obj) {
            return ArrayConversionUtils.bytesFromIntsReversed(obj.getIntStorage());
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final byte[] doNativeLongs(final NativeObject obj) {
            return ArrayConversionUtils.bytesFromLongsReversed(obj.getLongStorage());
        }
    }
}
