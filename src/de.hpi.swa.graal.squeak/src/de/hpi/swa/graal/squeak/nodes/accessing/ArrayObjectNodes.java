package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodesFactory.ArrayObjectToObjectArrayCopyNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodesFactory.ArrayObjectTraceableToObjectArrayNodeGen;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

public final class ArrayObjectNodes {

    @GenerateUncached
    public abstract static class ArrayObjectToObjectArrayCopyNode extends AbstractNode {

        public static ArrayObjectToObjectArrayCopyNode create() {
            return ArrayObjectToObjectArrayCopyNodeGen.create();
        }

        public final Object[] executeWithFirst(final ArrayObject obj, final Object first) {
            return ArrayUtils.copyWithFirst(execute(obj), first);
        }

        public abstract Object[] execute(ArrayObject obj);

        @Specialization(guards = "obj.isObjectType()")
        protected static final Object[] doArrayOfObjects(final ArrayObject obj) {
            return obj.getObjectStorage();
        }

        @Specialization(guards = "obj.isEmptyType()")
        protected static final Object[] doEmptyArray(final ArrayObject obj) {
            return ArrayUtils.withAll(obj.getEmptyStorage(), NilObject.SINGLETON);
        }

        @Specialization(guards = "obj.isBooleanType()")
        protected static final Object[] doArrayOfBooleans(final ArrayObject obj) {
            final byte[] booleans = obj.getBooleanStorage();
            final int length = booleans.length;
            final Object[] objects = new Object[length];
            for (int i = 0; i < length; i++) {
                objects[i] = ArrayObject.toObjectFromBoolean(booleans[i]);
            }
            return objects;
        }

        @Specialization(guards = "obj.isCharType()")
        protected static final Object[] doArrayOfChars(final ArrayObject obj) {
            final char[] chars = obj.getCharStorage();
            final int length = chars.length;
            final Object[] objects = new Object[length];
            for (int i = 0; i < length; i++) {
                final char value = chars[i];
                objects[i] = ArrayObject.toObjectFromChar(value);
            }
            return objects;
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final Object[] doArrayOfLongs(final ArrayObject obj) {
            final long[] longs = obj.getLongStorage();
            final int length = longs.length;
            final Object[] objects = new Object[length];
            for (int i = 0; i < length; i++) {
                objects[i] = ArrayObject.toObjectFromLong(longs[i]);
            }
            return objects;
        }

        @Specialization(guards = "obj.isDoubleType()")
        protected static final Object[] doArrayOfDoubles(final ArrayObject obj) {
            final double[] doubles = obj.getDoubleStorage();
            final int length = doubles.length;
            final Object[] objects = new Object[length];
            for (int i = 0; i < length; i++) {
                objects[i] = ArrayObject.toObjectFromDouble(doubles[i]);
            }
            return objects;
        }

        @Specialization(guards = "obj.isNativeObjectType()")
        protected static final Object[] doArrayOfNatives(final ArrayObject obj) {
            final NativeObject[] nativeObjects = obj.getNativeObjectStorage();
            final int length = nativeObjects.length;
            final Object[] objects = new Object[length];
            for (int i = 0; i < length; i++) {
                objects[i] = ArrayObject.toObjectFromNativeObject(nativeObjects[i]);
            }
            return objects;
        }
    }

    @GenerateUncached
    public abstract static class ArrayObjectTraceableToObjectArrayNode extends AbstractNode {

        public static ArrayObjectTraceableToObjectArrayNode create() {
            return ArrayObjectTraceableToObjectArrayNodeGen.create();
        }

        public abstract Object[] execute(ArrayObject obj);

        @Specialization(guards = "obj.isObjectType()")
        protected static final Object[] doArrayOfObjects(final ArrayObject obj) {
            return obj.getObjectStorage();
        }

        @Specialization(guards = "obj.isNativeObjectType()")
        protected static final Object[] doArrayOfNatives(final ArrayObject obj) {
            return obj.getNativeObjectStorage();
        }
    }
}
