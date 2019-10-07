package de.hpi.swa.graal.squeak.model;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.IntValueProfile;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.LocationFactory.ReadLocationNodeGen;
import de.hpi.swa.graal.squeak.model.LocationFactory.WriteLocationNodeGen;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.UnsafeUtils;

public abstract class Location {
    public static final int NUM_PRIMITIVE_INLINE_LOCATIONS = 3;
    public static final int NUM_PRIMITIVE_EXT_LOCATIONS = Integer.SIZE - NUM_PRIMITIVE_INLINE_LOCATIONS;
    public static final int NUM_OBJECT_INLINE_LOCATIONS = 3;

    private static final long PRIMITIVE_USED_MAP_ADDRESS;

    @CompilationFinal(dimensions = 1) private static final long[] PRIMITIVE_ADDRESSES = new long[NUM_PRIMITIVE_INLINE_LOCATIONS];
    @CompilationFinal(dimensions = 1) private static final long[] OBJECT_ADDRESSES = new long[NUM_OBJECT_INLINE_LOCATIONS];

    public static final UninitializedLocation UNINITIALIZED_LOCATION = new UninitializedLocation();
    public static final Location[] BOOL_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final Location[] CHAR_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final Location[] LONG_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final Location[] DOUBLE_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final EconomicMap<Integer, Location> OBJECT_LOCATIONS = EconomicMap.create();

    static {
        PRIMITIVE_USED_MAP_ADDRESS = UnsafeUtils.getAddress(AbstractPointersObject.class, "primitiveUsedMap");

        for (int i = 0; i < NUM_PRIMITIVE_INLINE_LOCATIONS; i++) {
            PRIMITIVE_ADDRESSES[i] = UnsafeUtils.getAddress(AbstractPointersObject.class, "primitive" + i);
            BOOL_LOCATIONS[i] = new BoolInlineLocation(i);
            CHAR_LOCATIONS[i] = new CharInlineLocation(i);
            LONG_LOCATIONS[i] = new LongInlineLocation(i);
            DOUBLE_LOCATIONS[i] = new DoubleInlineLocation(i);
        }

        for (int i = NUM_PRIMITIVE_INLINE_LOCATIONS; i < NUM_PRIMITIVE_EXT_LOCATIONS; i++) {
            BOOL_LOCATIONS[i] = new BoolExtLocation(i);
            CHAR_LOCATIONS[i] = new CharExtLocation(i);
            LONG_LOCATIONS[i] = new LongExtLocation(i);
            DOUBLE_LOCATIONS[i] = new DoubleExtLocation(i);
        }

        for (int i = 0; i < NUM_OBJECT_INLINE_LOCATIONS; i++) {
            OBJECT_ADDRESSES[i] = UnsafeUtils.getAddress(AbstractPointersObject.class, "object" + i);
            OBJECT_LOCATIONS.put(i, new ObjectInlineLocation(i));
        }
    }

    public static Location getObjectLocation(final int index) {
        Location location = OBJECT_LOCATIONS.get(index);
        if (location == null) {
            location = new ObjectExtLocation(index);
            OBJECT_LOCATIONS.put(index, location);
        }
        return location;
    }

    public static final class IllegalWriteException extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        public static final IllegalWriteException SINGLETON = new IllegalWriteException();

        private IllegalWriteException() {
        }
    }

    public abstract Object read(AbstractPointersObject obj);

    public abstract void write(AbstractPointersObject obj, Object value);

    public final void writeMustSucceed(final AbstractPointersObject obj, final Object value) {
        try {
            write(obj, value);
        } catch (final IllegalWriteException e) {
            throw SqueakException.illegalState(e);
        }
    }

    public abstract boolean canStore(Object value);

    public abstract boolean isSet(AbstractPointersObject object);

    public boolean isUninitialized() {
        return false;
    }

    public boolean isPrimitive() {
        return false;
    }

    public boolean isGeneric() {
        return false;
    }

    public boolean isBool() {
        return false;
    }

    public boolean isChar() {
        return false;
    }

    public boolean isLong() {
        return false;
    }

    public boolean isDouble() {
        return false;
    }

    protected boolean isExtension() {
        return false;
    }

    public int getFieldIndex() {
        return -1;
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    public abstract static class ReadLocationNode extends Node {
        public abstract Object execute(Location location, AbstractPointersObject object);

        public static ReadLocationNode getUncached() {
            return ReadLocationNodeGen.getUncached();
        }

        @Specialization
        protected static final Object doPrimitive(final PrimitiveLocation location, final AbstractPointersObject object,
                        @Cached("createIdentityProfile()") final IntValueProfile primitiveUsedMapProfile) {
            return location.readProfiled(object, primitiveUsedMapProfile);
        }

        @Fallback
        protected static final Object doGeneric(final Location location, final AbstractPointersObject object) {
            return location.read(object);
        }
    }

    @GenerateUncached
    public abstract static class WriteLocationNode extends Node {
        public abstract void execute(Location location, AbstractPointersObject object, Object value);

        public static WriteLocationNode getUncached() {
            return WriteLocationNodeGen.getUncached();
        }

        @Specialization
        protected static final void doPrimitive(final PrimitiveLocation location, final AbstractPointersObject object, final Object value,
                        @Cached("createIdentityProfile()") final IntValueProfile primitiveUsedMapProfile) {
            location.writeProfiled(object, value, primitiveUsedMapProfile);
        }

        @Fallback
        protected static final void doGeneric(final Location location, final AbstractPointersObject object, final Object value) {
            location.write(object, value);
        }
    }

    private static final class UninitializedLocation extends Location {
        @Override
        public Object read(final AbstractPointersObject obj) {
            return NilObject.SINGLETON;
        }

        @Override
        public void write(final AbstractPointersObject obj, final Object value) {
            if (value != NilObject.SINGLETON) {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public boolean isUninitialized() {
            return true;
        }

        @Override
        public boolean isSet(final AbstractPointersObject obj) {
            return false;
        }

        @Override
        public boolean canStore(final Object value) {
            return false;
        }
    }

    protected abstract static class PrimitiveLocation extends Location {
        public abstract Object readProfiled(AbstractPointersObject object, IntValueProfile primitiveUsedMapProfile);

        public abstract void writeProfiled(AbstractPointersObject object, Object value, IntValueProfile primitiveUsedMapProfile);

        @Override
        public final boolean isPrimitive() {
            return true;
        }

    }

    private abstract static class BoolLocation extends PrimitiveLocation {
        @Override
        public final boolean isBool() {
            return true;
        }

        @Override
        public final boolean canStore(final Object value) {
            return value instanceof Boolean;
        }
    }

    private abstract static class CharLocation extends PrimitiveLocation {
        @Override
        public final boolean isChar() {
            return true;
        }

        @Override
        public final boolean canStore(final Object value) {
            return value instanceof Character;
        }
    }

    private abstract static class LongLocation extends PrimitiveLocation {
        @Override
        public final boolean isLong() {
            return true;
        }

        @Override
        public final boolean canStore(final Object value) {
            return value instanceof Long;
        }
    }

    private abstract static class DoubleLocation extends PrimitiveLocation {
        @Override
        public final boolean isDouble() {
            return true;
        }

        @Override
        public final boolean canStore(final Object value) {
            return value instanceof Double;
        }
    }

    private abstract static class GenericLocation extends Location {
        @Override
        public final boolean isGeneric() {
            return true;
        }

        @Override
        public final boolean canStore(final Object value) {
            return true;
        }
    }

    public static int getPrimitiveUsedMask(final int index) {
        assert 0 <= index && index < Integer.SIZE;
        return 1 << index;
    }

    private static int getPrimitiveUsedMap(final AbstractPointersObject object) {
        return UnsafeUtils.getIntAt(object, PRIMITIVE_USED_MAP_ADDRESS);
    }

    private static void putPrimitiveUsedMap(final AbstractPointersObject object, final int value) {
        UnsafeUtils.putIntAt(object, PRIMITIVE_USED_MAP_ADDRESS, value);
    }

    private static final class BoolInlineLocation extends BoolLocation {
        private final long address;
        private final int usedMask;

        private BoolInlineLocation(final int index) {
            address = PRIMITIVE_ADDRESSES[index];
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getBoolAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getBoolAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                UnsafeUtils.putBoolAt(object, address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                UnsafeUtils.putBoolAt(object, address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return ArrayUtils.indexOf(PRIMITIVE_ADDRESSES, address);
        }
    }

    private static final class BoolExtLocation extends BoolLocation {
        private final int index;
        private final int usedMask;

        private BoolExtLocation(final int index) {
            this.index = index - NUM_PRIMITIVE_INLINE_LOCATIONS;
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return BooleanObject.wrap(object.primitiveExtension[index] == 1);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return BooleanObject.wrap(object.primitiveExtension[index] == 1);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                object.primitiveExtension[index] = (boolean) value ? 1 : 0;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                object.primitiveExtension[index] = (boolean) value ? 1 : 0;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        protected boolean isExtension() {
            return true;
        }

        @Override
        public int getFieldIndex() {
            return Location.NUM_PRIMITIVE_INLINE_LOCATIONS + index;
        }
    }

    private static final class CharInlineLocation extends CharLocation {
        private final long address;
        private final int usedMask;

        private CharInlineLocation(final int index) {
            address = PRIMITIVE_ADDRESSES[index];
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getCharAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getCharAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                UnsafeUtils.putCharAt(object, address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                UnsafeUtils.putCharAt(object, address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return ArrayUtils.indexOf(PRIMITIVE_ADDRESSES, address);
        }
    }

    private static final class CharExtLocation extends CharLocation {
        private final int index;
        private final int usedMask;

        private CharExtLocation(final int index) {
            this.index = index - NUM_PRIMITIVE_INLINE_LOCATIONS;
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return (char) object.primitiveExtension[index];
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return (char) object.primitiveExtension[index];
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                object.primitiveExtension[index] = (char) value;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                object.primitiveExtension[index] = (char) value;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        protected boolean isExtension() {
            return true;
        }

        @Override
        public int getFieldIndex() {
            return Location.NUM_PRIMITIVE_INLINE_LOCATIONS + index;
        }
    }

    private static final class LongInlineLocation extends LongLocation {
        private final long address;
        private final int usedMask;

        private LongInlineLocation(final int index) {
            address = PRIMITIVE_ADDRESSES[index];
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getLongAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getLongAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                UnsafeUtils.putLongAt(object, address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                UnsafeUtils.putLongAt(object, address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return ArrayUtils.indexOf(PRIMITIVE_ADDRESSES, address);
        }
    }

    private static final class LongExtLocation extends LongLocation {
        private final int index;
        private final int usedMask;

        private LongExtLocation(final int index) {
            this.index = index - NUM_PRIMITIVE_INLINE_LOCATIONS;
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return object.primitiveExtension[index];
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return object.primitiveExtension[index];
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                object.primitiveExtension[index] = (long) value;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                object.primitiveExtension[index] = (long) value;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        protected boolean isExtension() {
            return true;
        }

        @Override
        public int getFieldIndex() {
            return Location.NUM_PRIMITIVE_INLINE_LOCATIONS + index;
        }
    }

    private static final class DoubleInlineLocation extends DoubleLocation {
        private final long address;
        private final int usedMask;

        private DoubleInlineLocation(final int index) {
            address = PRIMITIVE_ADDRESSES[index];
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getDoubleAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getDoubleAt(object, address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                UnsafeUtils.putDoubleAt(object, address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                UnsafeUtils.putDoubleAt(object, address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return ArrayUtils.indexOf(PRIMITIVE_ADDRESSES, address);
        }
    }

    private static final class DoubleExtLocation extends DoubleLocation {
        private final int index;
        private final int usedMask;

        private DoubleExtLocation(final int index) {
            this.index = index - NUM_PRIMITIVE_INLINE_LOCATIONS;
            usedMask = getPrimitiveUsedMask(index);
        }

        @Override
        public Object readProfiled(final AbstractPointersObject obj, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(obj, primitiveUsedMapProfile)) {
                return Double.longBitsToDouble(obj.primitiveExtension[index]);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private boolean isSet(final AbstractPointersObject obj, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(obj)) & usedMask) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject obj) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(obj)) {
                return Double.longBitsToDouble(obj.primitiveExtension[index]);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject obj) {
            return (getPrimitiveUsedMap(obj) & usedMask) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | usedMask);
                object.primitiveExtension[index] = Double.doubleToRawLongBits((double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | usedMask);
                object.primitiveExtension[index] = Double.doubleToRawLongBits((double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        protected boolean isExtension() {
            return true;
        }

        @Override
        public int getFieldIndex() {
            return Location.NUM_PRIMITIVE_INLINE_LOCATIONS + index;
        }
    }

    private static final class ObjectInlineLocation extends GenericLocation {
        private final int index;

        private ObjectInlineLocation(final int index) {
            this.index = index;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            assert isSet(object);
            return UnsafeUtils.getObjectAt(object, OBJECT_ADDRESSES[index]);
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return UnsafeUtils.getObjectAt(object, OBJECT_ADDRESSES[index]) != null;
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            UnsafeUtils.putObjectAt(object, OBJECT_ADDRESSES[index], value);
            assert isSet(object);
        }

        @Override
        public int getFieldIndex() {
            return index;
        }
    }

    private static final class ObjectExtLocation extends GenericLocation {
        private final int index;

        private ObjectExtLocation(final int index) {
            this.index = index - NUM_OBJECT_INLINE_LOCATIONS;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            assert isSet(object);
            return object.objectExtension[index];
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return object.objectExtension != null && object.objectExtension[index] != null;
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            object.objectExtension[index] = value;
            assert isSet(object);
        }

        @Override
        protected boolean isExtension() {
            return true;
        }

        @Override
        public int getFieldIndex() {
            return Location.NUM_OBJECT_INLINE_LOCATIONS + index;
        }
    }
}
