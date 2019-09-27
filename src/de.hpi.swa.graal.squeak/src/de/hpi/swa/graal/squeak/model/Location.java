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
import de.hpi.swa.graal.squeak.util.UnsafeUtils;

public abstract class Location {
    public static final int NUM_PRIMITIVE_INLINE_LOCATIONS = 3;
    public static final int NUM_PRIMITIVE_EXT_LOCATIONS = Integer.SIZE - NUM_PRIMITIVE_INLINE_LOCATIONS;
    public static final int NUM_OBJECT_INLINE_LOCATIONS = 3;

    public static final int PRIM0_LOCATION_USED_MASK = 1 << 0;
    public static final int PRIM1_LOCATION_USED_MASK = 1 << 1;
    public static final int PRIM2_LOCATION_USED_MASK = 1 << 2;
    @CompilationFinal(dimensions = 1) public static final int[] PRIM_LOCATION_USED_MASKS = new int[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];

    private static final long primitiveUsedMapAddress;
    private static final long primitive0Address;
    private static final long primitive1Address;
    private static final long primitive2Address;

    public static final UninitializedLocation UNINITIALIZED_LOCATION = new UninitializedLocation();
    public static final Location[] BOOL_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final Location[] CHAR_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final Location[] LONG_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final Location[] DOUBLE_LOCATIONS = new Location[NUM_PRIMITIVE_INLINE_LOCATIONS + NUM_PRIMITIVE_EXT_LOCATIONS];
    public static final EconomicMap<Integer, Location> OBJECT_LOCATIONS = EconomicMap.create();

    static {
        primitiveUsedMapAddress = UnsafeUtils.getAddress(AbstractPointersObject.class, "primitiveUsedMap");
        primitive0Address = UnsafeUtils.getAddress(AbstractPointersObject.class, "primitive0");
        primitive1Address = UnsafeUtils.getAddress(AbstractPointersObject.class, "primitive1");
        primitive2Address = UnsafeUtils.getAddress(AbstractPointersObject.class, "primitive2");

        for (int i = 0; i < PRIM_LOCATION_USED_MASKS.length; i++) {
            PRIM_LOCATION_USED_MASKS[i] = 1 << 0;
        }

        BOOL_LOCATIONS[0] = new Bool0Location();
        BOOL_LOCATIONS[1] = new Bool1Location();
        BOOL_LOCATIONS[2] = new Bool2Location();
        for (int i = 0; i < NUM_PRIMITIVE_EXT_LOCATIONS; i++) {
            BOOL_LOCATIONS[NUM_PRIMITIVE_INLINE_LOCATIONS + i] = new BoolExtLocation(i);
        }
        CHAR_LOCATIONS[0] = new Char0Location();
        CHAR_LOCATIONS[1] = new Char1Location();
        CHAR_LOCATIONS[2] = new Char2Location();
        for (int i = 0; i < NUM_PRIMITIVE_EXT_LOCATIONS; i++) {
            CHAR_LOCATIONS[NUM_PRIMITIVE_INLINE_LOCATIONS + i] = new CharExtLocation(i);
        }
        LONG_LOCATIONS[0] = new Long0Location();
        LONG_LOCATIONS[1] = new Long1Location();
        LONG_LOCATIONS[2] = new Long2Location();
        for (int i = 0; i < NUM_PRIMITIVE_EXT_LOCATIONS; i++) {
            LONG_LOCATIONS[NUM_PRIMITIVE_INLINE_LOCATIONS + i] = new LongExtLocation(i);
        }
        DOUBLE_LOCATIONS[0] = new Double0Location();
        DOUBLE_LOCATIONS[1] = new Double1Location();
        DOUBLE_LOCATIONS[2] = new Double2Location();
        for (int i = 0; i < NUM_PRIMITIVE_EXT_LOCATIONS; i++) {
            DOUBLE_LOCATIONS[NUM_PRIMITIVE_INLINE_LOCATIONS + i] = new DoubleExtLocation(i);
        }
        OBJECT_LOCATIONS.put(0, new Object0Location());
        OBJECT_LOCATIONS.put(1, new Object1Location());
        OBJECT_LOCATIONS.put(2, new Object2Location());
    }

    public static Location getObjectLocation(final int index) {
        Location location = OBJECT_LOCATIONS.get(index);
        if (location == null) {
            location = new ObjectExtLocation(index - NUM_OBJECT_INLINE_LOCATIONS);
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

    private static int getPrimitiveUsedMap(final AbstractPointersObject object) {
        return UnsafeUtils.getIntAt(object, primitiveUsedMapAddress);
    }

    private static void putPrimitiveUsedMap(final AbstractPointersObject object, final int value) {
        UnsafeUtils.putIntAt(object, primitiveUsedMapAddress, value);
    }

    private static final class Bool0Location extends BoolLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getBoolAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getBoolAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putBoolAt(object, primitive0Address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putBoolAt(object, primitive0Address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 0;
        }
    }

    private static final class Bool1Location extends BoolLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getBoolAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject obj, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(obj)) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getBoolAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putBoolAt(object, primitive1Address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putBoolAt(object, primitive1Address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 1;
        }
    }

    private static final class Bool2Location extends BoolLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getBoolAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getBoolAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putBoolAt(object, primitive2Address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putBoolAt(object, primitive2Address, (boolean) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 2;
        }
    }

    private static final class BoolExtLocation extends BoolLocation {
        private final int index;

        private BoolExtLocation(final int index) {
            assert index < 64 : "primitiveUsedMap screwed (1 << 64 overflows)";
            this.index = index;
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
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & 1 << getFieldIndex()) != 0;
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
            return (getPrimitiveUsedMap(object) & 1 << getFieldIndex()) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | 1 << getFieldIndex());
                object.primitiveExtension[index] = (boolean) value ? 1 : 0;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Boolean) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | 1 << getFieldIndex());
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

    private static final class Char0Location extends CharLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getCharAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getCharAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putCharAt(object, primitive0Address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putCharAt(object, primitive0Address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 0;
        }
    }

    private static final class Char1Location extends CharLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getCharAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getCharAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putCharAt(object, primitive1Address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putCharAt(object, primitive1Address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 1;
        }
    }

    private static final class Char2Location extends CharLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getCharAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getCharAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putCharAt(object, primitive2Address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putCharAt(object, primitive2Address, (char) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 2;
        }
    }

    private static final class CharExtLocation extends CharLocation {
        private final int index;

        private CharExtLocation(final int index) {
            assert index < 64 : "primitiveUsedMap screwed (1 << 64 overflows)";
            this.index = index;
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
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & 1 << getFieldIndex()) != 0;
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
            return (getPrimitiveUsedMap(object) & 1 << getFieldIndex()) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | 1 << getFieldIndex());
                object.primitiveExtension[index] = (char) value;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Character) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | 1 << getFieldIndex());
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

    private static final class Long0Location extends LongLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getLongAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getLongAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putLongAt(object, primitive0Address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putLongAt(object, primitive0Address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 0;
        }
    }

    private static final class Long1Location extends LongLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getLongAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject obj, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(obj)) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getLongAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putLongAt(object, primitive1Address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putLongAt(object, primitive1Address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 1;
        }
    }

    private static final class Long2Location extends LongLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getLongAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getLongAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putLongAt(object, primitive2Address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putLongAt(object, primitive2Address, (long) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 2;
        }
    }

    private static final class LongExtLocation extends LongLocation {
        private final int index;

        private LongExtLocation(final int index) {
            assert index < 64 : "primitiveUsedMap screwed (1 << 64 overflows)";
            this.index = index;
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
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & 1 << getFieldIndex()) != 0;
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
            return (getPrimitiveUsedMap(object) & 1 << getFieldIndex()) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | 1 << getFieldIndex());
                object.primitiveExtension[index] = (long) value;
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Long) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | 1 << getFieldIndex());
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

    private static final class Double0Location extends DoubleLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getDoubleAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getDoubleAt(object, primitive0Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM0_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putDoubleAt(object, primitive0Address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM0_LOCATION_USED_MASK);
                UnsafeUtils.putDoubleAt(object, primitive0Address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 0;
        }
    }

    private static final class Double1Location extends DoubleLocation {
        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getDoubleAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject obj, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(obj)) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getDoubleAt(object, primitive1Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM1_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putDoubleAt(object, primitive1Address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM1_LOCATION_USED_MASK);
                UnsafeUtils.putDoubleAt(object, primitive1Address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 1;
        }
    }

    private static final class Double2Location extends DoubleLocation {

        @Override
        public Object readProfiled(final AbstractPointersObject object, final IntValueProfile primitiveUsedMapProfile) {
            if (isSet(object, primitiveUsedMapProfile)) {
                return UnsafeUtils.getDoubleAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        private static boolean isSet(final AbstractPointersObject obj, final IntValueProfile primitiveUsedMapProfile) {
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(obj)) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public Object read(final AbstractPointersObject object) {
            CompilerAsserts.neverPartOfCompilation();
            if (isSet(object)) {
                return UnsafeUtils.getDoubleAt(object, primitive2Address);
            } else {
                return NilObject.SINGLETON;
            }
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return (getPrimitiveUsedMap(object) & PRIM2_LOCATION_USED_MASK) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putDoubleAt(object, primitive2Address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | PRIM2_LOCATION_USED_MASK);
                UnsafeUtils.putDoubleAt(object, primitive2Address, (double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public int getFieldIndex() {
            return 2;
        }
    }

    private static final class DoubleExtLocation extends DoubleLocation {
        private final int index;

        private DoubleExtLocation(final int index) {
            assert index < 64 : "primitiveUsedMap screwed (1 << 64 overflows)";
            this.index = index;
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
            return (primitiveUsedMapProfile.profile(getPrimitiveUsedMap(obj)) & 1 << getFieldIndex()) != 0;
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
            return (getPrimitiveUsedMap(obj) & 1 << getFieldIndex()) != 0;
        }

        @Override
        public void writeProfiled(final AbstractPointersObject object, final Object value, final IntValueProfile primitiveUsedMapProfile) {
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, primitiveUsedMapProfile.profile(getPrimitiveUsedMap(object)) | 1 << getFieldIndex());
                object.primitiveExtension[index] = Double.doubleToRawLongBits((double) value);
            } else {
                throw IllegalWriteException.SINGLETON;
            }
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            CompilerAsserts.neverPartOfCompilation();
            if (value instanceof Double) {
                putPrimitiveUsedMap(object, getPrimitiveUsedMap(object) | 1 << getFieldIndex());
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

    private static final class Object0Location extends GenericLocation {
        @Override
        public Object read(final AbstractPointersObject object) {
            assert isSet(object);
            return object.object0;
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return object.object0 != null;
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            object.object0 = value;
            assert isSet(object);
        }

        @Override
        public int getFieldIndex() {
            return 0;
        }
    }

    private static final class Object1Location extends GenericLocation {
        @Override
        public Object read(final AbstractPointersObject object) {
            assert isSet(object);
            return object.object1;
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return object.object1 != null;
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            object.object1 = value;
            assert isSet(object);
        }

        @Override
        public int getFieldIndex() {
            return 1;
        }
    }

    private static final class Object2Location extends GenericLocation {
        @Override
        public Object read(final AbstractPointersObject object) {
            assert isSet(object);
            return object.object2;
        }

        @Override
        public boolean isSet(final AbstractPointersObject object) {
            return object.object2 != null;
        }

        @Override
        public void write(final AbstractPointersObject object, final Object value) {
            object.object2 = value;
            assert isSet(object);
        }

        @Override
        public int getFieldIndex() {
            return 2;
        }
    }

    private static final class ObjectExtLocation extends GenericLocation {
        private final int index;

        private ObjectExtLocation(final int index) {
            this.index = index;
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
