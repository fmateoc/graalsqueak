package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractPointersObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.PointersObjectNodesFactory.PointersObjectReadNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.PointersObjectNodesFactory.PointersObjectSizeNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.PointersObjectNodesFactory.PointersObjectWriteNodeGen;

public final class PointersObjectNodes {
    protected static final int CACHE_LIMIT = 3;

    @GenerateUncached
    @ImportStatic(PointersObjectNodes.class)
    public abstract static class PointersObjectReadNode extends AbstractNode {
        public static PointersObjectReadNode create() {
            return PointersObjectReadNodeGen.create();
        }

        public static PointersObjectReadNode getUncached() {
            return PointersObjectReadNodeGen.getUncached();
        }

        public final Object executeRead(final PointersObject object, final long index) {
            try {
                return executeRead(object.getStorage(), index);
            } catch (final UnknownIdentifierException e) {
                // TODO Auto-generated catch block
                throw SqueakException.create(e);
            }
        }

        protected abstract Object executeRead(DynamicObject storage, long index) throws UnknownIdentifierException;

        /**
         * Polymorphic inline cache for a limited number of distinct property names and shapes.
         */
        @Specialization(limit = "CACHE_LIMIT", //
                        guards = {
                                        "storage.getShape() == cachedShape",
                                        "cachedIndex == index"
                        }, //
                        assumptions = "cachedShape.getValidAssumption()")
        static Object readCached(final DynamicObject storage, @SuppressWarnings("unused") final long index,
                        @SuppressWarnings("unused") @Cached("index") final long cachedIndex,
                        @Cached("storage.getShape()") final Shape cachedShape,
                        @Cached("lookupLocation(cachedShape, cachedIndex)") final Location location) {
            return location.get(storage, cachedShape);
        }

        static Location lookupLocation(final Shape shape, final long index) throws UnknownIdentifierException {
            /* Initialization of cached values always happens in a slow path. */
            CompilerAsserts.neverPartOfCompilation();

            final Property property = shape.getProperty(index);
            if (property == null) {
                /* Property does not exist. */
                throw UnknownIdentifierException.create("#" + index);
            }

            return property.getLocation();
        }

        /**
         * The generic case is used if the number of shapes accessed overflows the limit of the
         * polymorphic inline cache.
         */
        @TruffleBoundary
        @Specialization(replaces = {"readCached"}, guards = "storage.getShape().isValid()")
        static Object readUncached(final DynamicObject storage, final long index) throws UnknownIdentifierException {
            final Object result = storage.get(index);
            if (result == null) {
                /* Property does not exist. */
                throw UnknownIdentifierException.create("#" + index);
            }
            return result;
        }

        @Specialization(guards = "!storage.getShape().isValid()")
        static Object updateShape(final DynamicObject storage, final long index) throws UnknownIdentifierException {
            CompilerDirectives.transferToInterpreter();
            storage.updateShape();
            return readUncached(storage, index);
        }
    }

    @GenerateUncached
    @ImportStatic(PointersObjectNodes.class)
    public abstract static class PointersObjectWriteNode extends AbstractNode {

        public static PointersObjectWriteNode create() {
            return PointersObjectWriteNodeGen.create();
        }

        public final void executeWrite(final PointersObject object, final long index, final Object value) {
            try {
                executeWrite(object.getStorage(), index, value);
            } catch (final UnknownIdentifierException e) {
                // TODO Auto-generated catch block
                throw SqueakException.create(e);
            }
        }

        protected abstract void executeWrite(DynamicObject storage, long index, Object value) throws UnknownIdentifierException;

        /**
         * Polymorphic inline cache for writing a property that already exists (no shape change is
         * necessary).
         */
        @Specialization(limit = "CACHE_LIMIT", //
                        guards = {
                                        "cachedIndex == index",
                                        "shapeCheck(shape, storage)",
                                        "location != null",
                                        "canSet(location, value)"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        static void writeExistingPropertyCached(final DynamicObject storage, @SuppressWarnings("unused") final long index, final Object value,
                        @SuppressWarnings("unused") @Cached("index") final long cachedIndex,
                        @Cached("storage.getShape()") final Shape shape,
                        @Cached("lookupLocation(shape, index, value)") final Location location) {
            try {
                location.set(storage, value, shape);
            } catch (IncompatibleLocationException | FinalLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        static boolean shapeCheck(final Shape shape, final DynamicObject receiver) {
            return shape != null && shape.check(receiver);
        }

        /**
         * Polymorphic inline cache for writing a property that does not exist yet (shape change is
         * necessary).
         */
        @Specialization(limit = "CACHE_LIMIT", //
                        guards = {
                                        "cachedIndex == index",
                                        "storage.getShape() == oldShape",
                                        "oldLocation == null",
                                        "canStore(newLocation, value)"
                        }, //
                        assumptions = {
                                        "oldShape.getValidAssumption()",
                                        "newShape.getValidAssumption()"
                        })
        @SuppressWarnings("unused")
        protected static final void writeNewPropertyCached(final DynamicObject storage, final long index, final Object value,
                        @Cached("index") final long cachedIndex,
                        @Cached("storage.getShape()") final Shape oldShape,
                        @Cached("lookupLocation(oldShape, index, value)") final Location oldLocation,
                        @Cached("defineProperty(oldShape, index, value)") final Shape newShape,
                        @Cached("lookupLocation(newShape, index)") final Location newLocation) {
            try {
                newLocation.set(storage, value, oldShape, newShape);
            } catch (final IncompatibleLocationException ex) {
                /* Our guards ensure that the value can be stored, so this cannot happen. */
                throw new IllegalStateException(ex);
            }
        }

        /** Try to find the given property in the shape. */
        protected static final Location lookupLocation(final Shape shape, final long index) {
            CompilerAsserts.neverPartOfCompilation();

            final Property property = shape.getProperty(index);
            if (property == null) {
                /* Property does not exist yet, so a shape change is necessary. */
                return null;
            }

            return property.getLocation();
        }

        /**
         * Try to find the given property in the shape. Also returns null when the value cannot be
         * store into the location.
         */
        protected static final Location lookupLocation(final Shape shape, final long index, final Object value) {
            final Location location = lookupLocation(shape, index);
            if (location == null || !location.canSet(value)) {
                /* Existing property has an incompatible type, so a shape change is necessary. */
                return null;
            }

            return location;
        }

        protected static final Shape defineProperty(final Shape oldShape, final long index, final Object value) {
            return oldShape.defineProperty(index, value, 0);
        }

        /**
         * There is a subtle difference between {@link Location#canSet} and
         * {@link Location#canStore}. We need {@link Location#canSet} for the guard of
         * {@link #writeExistingPropertyCached} because there we call {@link Location#set}. We use
         * the more relaxed {@link Location#canStore} for the guard of
         * SLWritePropertyCacheNode#writeNewPropertyCached because there we perform a shape
         * transition, i.e., we are not actually setting the value of the new location - we only
         * transition to this location as part of the shape change.
         */
        protected static final boolean canSet(final Location location, final Object value) {
            return location.canSet(value);
        }

        /** See {@link #canSet} for the difference between the two methods. */
        protected static final boolean canStore(final Location location, final Object value) {
            return location.canStore(value);
        }

        /**
         * The generic case is used if the number of shapes accessed overflows the limit of the
         * polymorphic inline cache.
         */
        @TruffleBoundary
        @Specialization(replaces = {"writeExistingPropertyCached", "writeNewPropertyCached"}, guards = {"storage.getShape().isValid()"})
        protected static final void writeUncached(final DynamicObject storage, final long index, final Object value) {
            storage.define(index, value);
        }

        @TruffleBoundary
        @Specialization(guards = {"!storage.getShape().isValid()"})
        protected static final void updateShape(final DynamicObject storage, final long index, final Object value) {
            /*
             * Slow path that we do not handle in compiled code. But no need to invalidate compiled
             * code.
             */
            CompilerDirectives.transferToInterpreter();
            storage.updateShape();
            writeUncached(storage, index, value);
        }
    }

    @GenerateUncached
    @ImportStatic(PointersObjectNodes.class)
    public abstract static class PointersObjectSizeNode extends AbstractNode {

        public static PointersObjectSizeNode getUncached() {
            return PointersObjectSizeNodeGen.getUncached();
        }

        public final int executeSize(final AbstractPointersObject object) {
            return executeSize(object.getStorage());
        }

        protected abstract int executeSize(DynamicObject storage);

        @SuppressWarnings("unused")
        @Specialization(guards = "storage.getShape() == cachedShape")
        static int doCached(final DynamicObject storage, //
                        @Cached("storage.getShape()") final Shape cachedShape, //
                        @Cached(value = "doGeneric(storage)", allowUncached = true) final int cachedSize) {
            return cachedSize;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static int doGeneric(final DynamicObject storage) {
            return storage.size();
        }
    }
}
