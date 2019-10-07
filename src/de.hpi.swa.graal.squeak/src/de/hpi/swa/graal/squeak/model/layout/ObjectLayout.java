package de.hpi.swa.graal.squeak.model.layout;

import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

public final class ObjectLayout {
    @CompilationFinal(dimensions = 1) private final Location[] locations;
    private final int numBooleanExtension;
    private final int numPrimitiveExtension;
    private final int numObjectExtension;

    private final Assumption isValidAssumption = Truffle.getRuntime().createAssumption("Latest layout assumption");

    public ObjectLayout(final ClassObject classObject, final int instSize) {
        slowPathOperation();
        classObject.updateLayout(this);
        locations = new Location[instSize];
        Arrays.fill(locations, Location.UNINITIALIZED_LOCATION);
        numBooleanExtension = 0;
        numPrimitiveExtension = 0;
        numObjectExtension = 0;
    }

    public ObjectLayout(final ClassObject classObject, final Location[] locations) {
        slowPathOperation();
        classObject.updateLayout(this);
        this.locations = locations;
        numBooleanExtension = countBooleanExtension(locations);
        numPrimitiveExtension = countPrimitiveExtension(locations);
        numObjectExtension = countObjectExtension(locations);
    }

    public ObjectLayout evolveLocation(final ClassObject classObject, final int index, final Object value) {
        slowPathOperation();
        if (!isValid()) {
            throw SqueakException.create("Only the latest layout should be evolved");
        }
        final Location oldLocation = locations[index];
        assert index < locations.length && !oldLocation.isGeneric();
        invalidate();
        final Location[] newLocations = locations.clone();
        newLocations[index] = Location.UNINITIALIZED_LOCATION;
        if (oldLocation.isUninitialized()) {
            if (value instanceof Boolean) {
                assignPrimitiveLocation(newLocations, index, Location.BOOL_LOCATIONS);
            } else if (value instanceof Character) {
                assignPrimitiveLocation(newLocations, index, Location.CHAR_LOCATIONS);
            } else if (value instanceof Long) {
                assignPrimitiveLocation(newLocations, index, Location.LONG_LOCATIONS);
            } else if (value instanceof Double) {
                assignPrimitiveLocation(newLocations, index, Location.DOUBLE_LOCATIONS);
            } else {
                assignGenericLocation(newLocations, index);
            }
        } else {
            assignGenericLocation(newLocations, index);
        }

        if (oldLocation.isBool()) {
            assert newLocations[index].isGeneric();
            compressBooleansIfPossible(newLocations, oldLocation);
        }

        if (oldLocation.isPrimitive() && !oldLocation.isBool()) {
            assert newLocations[index].isGeneric();
            compressPrimitivesIfPossible(newLocations, oldLocation);
        }

        assert !newLocations[index].isUninitialized();
        assert areConsecutive(newLocations) : "Locations are not consecutive";
        return new ObjectLayout(classObject, newLocations);
    }

    private static void slowPathOperation() {
        CompilerAsserts.neverPartOfCompilation("Should only happen on slow path");
    }

    private static void assignGenericLocation(final Location[] newLocations, final int index) {
        for (final Location possibleLocation : Location.OBJECT_LOCATIONS.getValues()) {
            if (!inUse(newLocations, possibleLocation)) {
                newLocations[index] = possibleLocation;
                return;
            }
        }
        newLocations[index] = Location.getObjectLocation(Location.OBJECT_LOCATIONS.size());
    }

    private static void assignPrimitiveLocation(final Location[] newLocations, final int index, final Location[] possibleLocations) {
        for (final Location possibleLocation : possibleLocations) {
            if (!inUse(newLocations, possibleLocation)) {
                newLocations[index] = possibleLocation;
                return;
            }
        }
        // Primitive locations exhausted, fall back to a generic location.
        assignGenericLocation(newLocations, index);
    }

    private static void compressBooleansIfPossible(final Location[] locations, final Location freeBooleanLocation) {
        final int highestBooleanField = getHighestBooleanField(locations);
        if (highestBooleanField < freeBooleanLocation.getFieldIndex()) {
            return;
        }
        for (int i = 0; i < locations.length; i++) {
            final Location location = locations[i];
            if (location.isBool() && location.getFieldIndex() == highestBooleanField) {
                locations[i] = Location.BOOL_LOCATIONS[freeBooleanLocation.getFieldIndex()];
            }
        }
    }

    private static void compressPrimitivesIfPossible(final Location[] locations, final Location freePrimitiveLocation) {
        final int highestPrimitiveField = getHighestPrimitiveField(locations);
        if (highestPrimitiveField < freePrimitiveLocation.getFieldIndex()) {
            return;
        }
        for (int i = 0; i < locations.length; i++) {
            final Location location = locations[i];
            if (location.isPrimitive() && !location.isBool() && location.getFieldIndex() == highestPrimitiveField) {
                if (location.isChar()) {
                    locations[i] = Location.CHAR_LOCATIONS[freePrimitiveLocation.getFieldIndex()];
                } else if (location.isLong()) {
                    locations[i] = Location.LONG_LOCATIONS[freePrimitiveLocation.getFieldIndex()];
                } else if (location.isDouble()) {
                    locations[i] = Location.DOUBLE_LOCATIONS[freePrimitiveLocation.getFieldIndex()];
                } else {
                    throw SqueakException.create("Unexpected location type");
                }
            }
        }
    }

    private static int getHighestBooleanField(final Location[] locations) {
        int maxPrimitiveField = -1;
        for (int i = 0; i < locations.length; i++) {
            final Location location = locations[i];
            if (location.isBool()) {
                maxPrimitiveField = Math.max(location.getFieldIndex(), maxPrimitiveField);
            }
        }
        return maxPrimitiveField;
    }

    private static int getHighestPrimitiveField(final Location[] locations) {
        int maxPrimitiveField = -1;
        for (int i = 0; i < locations.length; i++) {
            final Location location = locations[i];
            if (location.isPrimitive() && !location.isBool()) {
                maxPrimitiveField = Math.max(location.getFieldIndex(), maxPrimitiveField);
            }
        }
        return maxPrimitiveField;
    }

    private static int getHighestObjectField(final Location[] locations) {
        int maxPrimitiveField = -1;
        for (int i = 0; i < locations.length; i++) {
            final Location location = locations[i];
            if (location.isGeneric()) {
                maxPrimitiveField = Math.max(location.getFieldIndex(), maxPrimitiveField);
            }
        }
        return maxPrimitiveField;
    }

    private static boolean areConsecutive(final Location[] locations) {
        CompilerAsserts.neverPartOfCompilation();
        final int maxBooleanField = getHighestBooleanField(locations);
        final int maxPrimitiveField = getHighestPrimitiveField(locations);
        final int maxObjectField = getHighestObjectField(locations);
        for (int i = 0; i <= maxBooleanField; i++) {
            boolean found = false;
            for (final Location location : locations) {
                if (location.isBool() && location.getFieldIndex() == i) {
                    if (found) {
                        return false;
                    } else {
                        found = true;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        for (int i = 0; i <= maxPrimitiveField; i++) {
            boolean found = false;
            for (final Location location : locations) {
                if (location.isPrimitive() && !location.isBool() && location.getFieldIndex() == i) {
                    if (found) {
                        return false;
                    } else {
                        found = true;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        for (int i = 0; i <= maxObjectField; i++) {
            boolean found = false;
            for (final Location location : locations) {
                if (location.isGeneric() && location.getFieldIndex() == i) {
                    if (found) {
                        return false;
                    } else {
                        found = true;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean inUse(final Location[] locations, final Location targetlocation) {
        for (int i = 0; i < locations.length; i++) {
            final Location location = locations[i];
            if (location.isGeneric() == targetlocation.isGeneric() && location.isBool() == targetlocation.isBool() && location.getFieldIndex() == targetlocation.getFieldIndex()) {
                return true;
            }
        }
        return false;
    }

    public Assumption getValidAssumption() {
        return isValidAssumption;
    }

    public void invalidate() {
        isValidAssumption.invalidate("Layout no longer valid");
    }

    public boolean isValid() {
        return isValidAssumption.isValid();
    }

    public Location getLocation(final int index) {
        return locations[index];
    }

    public int getInstSize() {
        return locations.length;
    }

    public int getNumBooleanExtension() {
        return numBooleanExtension;
    }

    public int getNumPrimitiveExtension() {
        return numPrimitiveExtension;
    }

    public int getNumObjectExtension() {
        return numObjectExtension;
    }

    public boolean[] getFreshBooleanExtension() {
        final int booleanExtUsed = getNumBooleanExtension();
        return booleanExtUsed > 0 ? new boolean[booleanExtUsed] : null;
    }

    public long[] getFreshPrimitiveExtension() {
        final int primitiveExtUsed = getNumPrimitiveExtension();
        return primitiveExtUsed > 0 ? new long[primitiveExtUsed] : null;
    }

    public Object[] getFreshObjectExtension() {
        final int objectExtUsed = getNumObjectExtension();
        return objectExtUsed > 0 ? ArrayUtils.withAll(objectExtUsed, NilObject.SINGLETON) : null;
    }

    private static int countBooleanExtension(final Location[] locations) {
        int count = 0;
        for (final Location location : locations) {
            if (location.isBool() && location.isExtension()) {
                count++;
            }
        }
        return count;
    }

    private static int countPrimitiveExtension(final Location[] locations) {
        int count = 0;
        for (final Location location : locations) {
            if (location.isPrimitive() && !location.isBool() && location.isExtension()) {
                count++;
            }
        }
        return count;
    }

    private static int countObjectExtension(final Location[] locations) {
        int count = 0;
        for (final Location location : locations) {
            if (location.isGeneric() && location.isExtension()) {
                count++;
            }
        }
        return count;
    }
}
