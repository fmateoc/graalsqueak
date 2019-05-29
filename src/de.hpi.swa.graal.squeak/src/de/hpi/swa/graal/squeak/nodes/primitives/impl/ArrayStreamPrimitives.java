package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.CharacterObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NotProvided;
import de.hpi.swa.graal.squeak.nodes.SqueakGuards;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuaternaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;

public final class ArrayStreamPrimitives extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ArrayStreamPrimitivesFactory.getFactories();
    }

    protected abstract static class AbstractBasicAtOrAtPutNode extends AbstractPrimitiveNode {
        protected AbstractBasicAtOrAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        protected static final boolean inBoundsOfSqueakObject(final long index, final Object target, final SqueakObjectLibrary objectLibrary) {
            return SqueakGuards.inBounds1(index + objectLibrary.instsize(target), objectLibrary.size(target));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 60)
    protected abstract static class PrimBasicAtNode extends AbstractBasicAtOrAtPutNode implements TernaryPrimitive {
        protected PrimBasicAtNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"inBoundsOfSqueakObject(index, receiver, objectLibrary)"})
        protected static final Object doSqueakObject(final Object receiver, final long index, @SuppressWarnings("unused") final NotProvided notProvided,
                        @Shared("objectLibrary") @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
            return objectLibrary.at0(receiver, (int) index - 1 + objectLibrary.instsize(receiver));
        }

        /*
         * Context>>#object:basicAt:
         */

        @Specialization(guards = {"inBoundsOfSqueakObject(index, target, objectLibrary)"})
        protected static final Object doSqueakObject(@SuppressWarnings("unused") final Object receiver, final Object target, final long index,
                        @Shared("objectLibrary") @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
            return objectLibrary.at0(target, (int) index - 1 + objectLibrary.instsize(target));
        }
    }

    @ImportStatic(NativeObject.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 61)
    protected abstract static class PrimBasicAtPutNode extends AbstractBasicAtOrAtPutNode implements QuaternaryPrimitive {
        protected PrimBasicAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"inBounds1(index, objectLibrary.size(receiver))", "objectLibrary.acceptsValue(receiver, value)"}, limit = "1")
        protected static final Object doNative(final NativeObject receiver, final long index, final Object value, @SuppressWarnings("unused") final NotProvided notProvided,
                        @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
            objectLibrary.atput0(receiver, (int) index - 1, value);
            return value;
        }

        @Specialization(guards = "inBounds1(index, receiver.size())")
        protected static final char doLargeIntegerChar(final LargeIntegerObject receiver, final long index, final char value, @SuppressWarnings("unused") final NotProvided notProvided) {
            receiver.setNativeAt0(index - 1, value);
            return value;
        }

        @Specialization(guards = "inBounds1(index, receiver.size())")
        protected static final long doLargeIntegerLong(final LargeIntegerObject receiver, final long index, final long value, @SuppressWarnings("unused") final NotProvided notProvided) {
            receiver.setNativeAt0(index - 1, value);
            return value;
        }

        @Specialization(guards = "inBounds1(index, receiver.size())")
        protected static final Object doLargeInteger(final LargeIntegerObject receiver, final long index, final LargeIntegerObject value, @SuppressWarnings("unused") final NotProvided notProvided) {
            receiver.setNativeAt0(index - 1, value.longValueExact());
            return value;
        }

        @Specialization(guards = "inBounds1(index, objectLibrary.size(receiver))")
        protected static final Object doArray(final ArrayObject receiver, final long index, final Object value, @SuppressWarnings("unused") final NotProvided notProvided,
                        @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
            objectLibrary.atput0(receiver, (int) index - 1, value);
            return value;
        }

        @Specialization(guards = {"inBoundsOfSqueakObject(index, receiver, objectLibrary)",
                        "!isNativeObject(receiver)", "!isEmptyObject(receiver)", "!isArrayObject(receiver)"})
        protected static final Object doSqueakObject(final Object receiver, final long index, final Object value,
                        @SuppressWarnings("unused") final NotProvided notProvided,
                        @Shared("objectLibrary") @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
            objectLibrary.atput0(receiver, (int) index - 1 + objectLibrary.instsize(receiver), value);
            return value;
        }

        /*
         * Context>>#object:basicAt:put:
         */

        @Specialization(guards = {"inBounds1(index, objectLibrary.size(target))", "objectLibrary.acceptsValue(target, value)"}, limit = "1")
        protected Object doNative(@SuppressWarnings("unused") final Object receiver, final NativeObject target, final long index, final Object value,
                        @CachedLibrary("target") final SqueakObjectLibrary objectLibrary) {
            objectLibrary.atput0(target, (int) index - 1, value);
            return value;
        }

        @Specialization(guards = "inBounds1(value, BYTE_MAX)")
        protected char doLargeIntegerChar(@SuppressWarnings("unused") final Object receiver, final LargeIntegerObject target, final long index, final char value) {
            target.setNativeAt0(index - 1, value);
            return value;
        }

        @Specialization(guards = "inBounds1(value, BYTE_MAX)")
        protected long doLargeIntegerLong(@SuppressWarnings("unused") final Object receiver, final LargeIntegerObject target, final long index, final long value) {
            target.setNativeAt0(index - 1, value);
            return value;
        }

        @Specialization(guards = {"value.fitsIntoLong()", "inBounds1(value.longValueExact(), BYTE_MAX)"})
        protected Object doLargeInteger(@SuppressWarnings("unused") final Object receiver, final LargeIntegerObject target, final long index, final LargeIntegerObject value) {
            target.setNativeAt0(index - 1, value.longValueExact());
            return value;
        }

        @Specialization(guards = "inBounds1(index, objectLibrary.size(target))")
        protected static final Object doArray(@SuppressWarnings("unused") final Object receiver, final ArrayObject target, final long index, final Object value,
                        @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
            objectLibrary.atput0(target, (int) index - 1, value);
            return value;
        }

        @Specialization(guards = {"inBoundsOfSqueakObject(index, target, objectLibrary)",
                        "!isNativeObject(target)", "!isEmptyObject(target)", "!isArrayObject(target)"})
        protected Object doSqueakObject(@SuppressWarnings("unused") final Object receiver, final Object target, final long index,
                        final Object value,
                        @Shared("objectLibrary") @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
            objectLibrary.atput0(target, (int) index - 1 + objectLibrary.instsize(target), value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 62)
    protected abstract static class PrimSizeNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"objectLibrary.accepts(receiver)", "objectLibrary.squeakClass(receiver).isVariable()"}, limit = "3")
        protected static final long doObject(final Object receiver, @SuppressWarnings("unused") final NotProvided notProvided,
                        @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
            return objectLibrary.size(receiver) - objectLibrary.instsize(receiver);
        }

        /*
         * Context>>#objectSize:
         */

        @Specialization(guards = {"objectLibrary.accepts(target)", "objectLibrary.squeakClass(target).isVariable()"}, limit = "3")
        protected static final long doObject(@SuppressWarnings("unused") final Object receiver, final Object target,
                        @CachedLibrary("target") final SqueakObjectLibrary objectLibrary) {
            return objectLibrary.size(target) - objectLibrary.instsize(target);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 63)
    protected abstract static class PrimStringAtNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimStringAtNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"inBounds1(index, objectLibrary.size(receiver))"}, limit = "1")
        protected static final Object doNativeObject(final NativeObject receiver, final long index,
                        @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
            return CharacterObject.valueOf((int) (long) objectLibrary.at0(receiver, (int) index - 1));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 64)
    protected abstract static class PrimStringAtPutNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        protected PrimStringAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"inBounds1(index, objectLibrary.size(receiver))", "objectLibrary.acceptsValue(receiver, value)"}, limit = "1")
        protected static final Object doNativeObject(final NativeObject receiver, final long index, final Object value,
                        @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
            objectLibrary.atput0(receiver, (int) index - 1, value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 143)
    protected abstract static class PrimShortAtNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimShortAtNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.isByteType()", "inBounds0(largerOffset(index), receiver.getByteLength())"})
        protected static final long doNativeBytes(final NativeObject receiver, final long index) {
            final int offset = minusOneAndDouble(index);
            final byte[] bytes = receiver.getByteStorage();
            final int byte0 = (byte) Byte.toUnsignedLong(bytes[offset]);
            int byte1 = (int) Byte.toUnsignedLong(bytes[offset + 1]) << 8;
            if ((byte1 & 0x8000) != 0) {
                byte1 = 0xffff0000 | byte1;
            }
            return byte1 | byte0;
        }

        @Specialization(guards = {"receiver.isShortType()", "inBounds1(index, receiver.getShortLength())"})
        protected static final long doNativeShorts(final NativeObject receiver, final long index) {
            return Short.toUnsignedLong(receiver.getShortStorage()[(int) index]);
        }

        @Specialization(guards = {"receiver.isIntType()", "inBounds0(minusOneAndCutInHalf(index), receiver.getIntLength())"})
        protected static final long doNativeInts(final NativeObject receiver, final long index) {
            final int word = receiver.getIntStorage()[minusOneAndCutInHalf(index)];
            int shortValue;
            if ((index - 1) % 2 == 0) {
                shortValue = word & 0xffff;
            } else {
                shortValue = word >> 16 & 0xffff;
            }
            if ((shortValue & 0x8000) != 0) {
                shortValue = 0xffff0000 | shortValue;
            }
            return shortValue;
        }

        protected static final int minusOneAndDouble(final long index) {
            return (int) ((index - 1) * 2);
        }

        protected static final int largerOffset(final long index) {
            return minusOneAndDouble(index) + 1;
        }

        protected static final int minusOneAndCutInHalf(final long index) {
            return ((int) index - 1) / 2;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver.isLongType()")
        protected static final long doNativeLongs(final NativeObject receiver, final long index) {
            throw SqueakException.create("Not yet implemented: shortAtPut0"); // TODO: implement
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 144)
    protected abstract static class PrimShortAtPutNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimShortAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isByteType()"})
        protected static final long doNativeBytes(final NativeObject receiver, final long index, final long value) {
            final int offset = (int) ((index - 1) * 2);
            final byte[] bytes = receiver.getByteStorage();
            bytes[offset] = (byte) value;
            bytes[offset + 1] = (byte) (value >> 8);
            return value;
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isShortType()"})
        protected static final long doNativeShorts(final NativeObject receiver, final long index, final long value) {
            receiver.getShortStorage()[(int) index] = (short) value;
            return value;
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isIntType()", "isEven(index)"})
        protected static final long doNativeIntsEven(final NativeObject receiver, final long index, final long value) {
            final int wordIndex = (int) ((index - 1) / 2);
            final int[] ints = receiver.getIntStorage();
            ints[wordIndex] = ints[wordIndex] & 0xffff0000 | (int) value & 0xffff;
            return value;
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isIntType()", "!isEven(index)"})
        protected static final long doNativeIntsOdd(final NativeObject receiver, final long index, final long value) {
            final int wordIndex = (int) ((index - 1) / 2);
            final int[] ints = receiver.getIntStorage();
            ints[wordIndex] = (int) value << 16 | ints[wordIndex] & 0xffff;
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"inShortRange(value)", "receiver.isLongType()"})
        protected static final long doNativeLongs(final NativeObject receiver, final long index, final long value) {
            throw SqueakException.create("Not yet implemented: shortAtPut0"); // TODO: implement
        }

        protected static final boolean inShortRange(final long value) {
            return -0x8000 <= value && value <= 0x8000;
        }

        protected static final boolean isEven(final long index) {
            return (index - 1) % 2 == 0;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 165)
    protected abstract static class PrimIntegerAtNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        protected PrimIntegerAtNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.isByteType()", "inBounds1(index, receiver.getByteLength())"})
        protected static final long doNativeByte(final NativeObject receiver, final long index) {
            return receiver.getByteStorage()[(int) index - 1];
        }

        @Specialization(guards = {"receiver.isIntType()", "inBounds1(index, receiver.getIntLength())"})
        protected static final long doNativeInt(final NativeObject receiver, final long index) {
            return receiver.getIntStorage()[(int) index - 1];
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 166)
    protected abstract static class PrimIntegerAtPutNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimIntegerAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.isByteType()", "inBounds1(index, receiver.getByteLength())", "fitsIntoByte(value)"})
        protected static final long doNativeByte(final NativeObject receiver, final long index, final long value) {
            receiver.getByteStorage()[(int) index - 1] = (byte) value;
            return value;
        }

        @Specialization(guards = {"receiver.isIntType()", "inBounds1(index, receiver.getIntLength())", "fitsIntoInt(value)"})
        protected static final long doNativeInt(final NativeObject receiver, final long index, final long value) {
            receiver.getIntStorage()[(int) index - 1] = (int) value;
            return value;
        }
    }
}
