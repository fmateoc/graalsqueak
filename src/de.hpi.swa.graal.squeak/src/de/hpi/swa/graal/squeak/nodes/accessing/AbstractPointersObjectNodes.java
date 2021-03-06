/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractPointersObject;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.model.VariablePointersObject;
import de.hpi.swa.graal.squeak.model.WeakVariablePointersObject;
import de.hpi.swa.graal.squeak.model.layout.ObjectLayout;
import de.hpi.swa.graal.squeak.model.layout.SlotLocation;
import de.hpi.swa.graal.squeak.model.layout.SlotLocation.IllegalWriteException;
import de.hpi.swa.graal.squeak.model.layout.SlotLocation.ReadSlotLocationNode;
import de.hpi.swa.graal.squeak.model.layout.SlotLocation.WriteSlotLocationNode;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodesFactory.AbstractPointersObjectReadNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodesFactory.AbstractPointersObjectWriteNodeGen;

public class AbstractPointersObjectNodes {
    protected static final int CACHE_LIMIT = 6;
    protected static final int VARIABLE_PART_INDEX_CACHE_LIMIT = 3;
    protected static final int VARIABLE_PART_LAYOUT_CACHE_LIMIT = 1;

    @GenerateUncached
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class AbstractPointersObjectReadNode extends AbstractNode {

        public static AbstractPointersObjectReadNode create() {
            return AbstractPointersObjectReadNodeGen.create();
        }

        public static AbstractPointersObjectReadNode getUncached() {
            return AbstractPointersObjectReadNodeGen.getUncached();
        }

        public abstract Object execute(AbstractPointersObject obj, int index);

        public abstract long executeLong(AbstractPointersObject obj, int index);

        public abstract ArrayObject executeArray(AbstractPointersObject obj, int index);

        public abstract NativeObject executeNative(AbstractPointersObject obj, int index);

        public abstract PointersObject executePointers(AbstractPointersObject obj, int index);

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout"}, //
                        assumptions = "cachedLayout.getValidAssumption()", limit = "CACHE_LIMIT")
        protected static final Object doReadCached(final AbstractPointersObject object, final int index,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached("cachedLayout.getLocation(index)") final SlotLocation cachedLocation,
                        @Cached final ReadSlotLocationNode readNode) {
            return readNode.execute(cachedLocation, object);
        }

        @TruffleBoundary
        @Specialization(guards = "object.getLayout().isValid()", replaces = "doReadCached")
        protected static final Object doReadUncached(final AbstractPointersObject object, final int index,
                        @Cached final ReadSlotLocationNode readNode) {
            return readNode.execute(object.getLayout().getLocation(index), object);
        }

        @Specialization(guards = "!object.getLayout().isValid()")
        protected static final Object doUpdateLayoutAndRead(final AbstractPointersObject object, final int index) {
            /* Note that this specialization does not replace the cached specialization. */
            CompilerDirectives.transferToInterpreter();
            object.updateLayout();
            return doReadUncached(object, index, ReadSlotLocationNode.getUncached());
        }
    }

    @GenerateUncached
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class AbstractPointersObjectWriteNode extends AbstractNode {

        public static AbstractPointersObjectWriteNode create() {
            return AbstractPointersObjectWriteNodeGen.create();
        }

        public static AbstractPointersObjectWriteNode getUncached() {
            return AbstractPointersObjectWriteNodeGen.getUncached();
        }

        public abstract void execute(AbstractPointersObject obj, int index, Object value);

        public final void executeNil(final AbstractPointersObject obj, final int index) {
            execute(obj, index, NilObject.SINGLETON);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout"}, //
                        assumptions = "cachedLayout.getValidAssumption()", limit = "CACHE_LIMIT")
        protected static final void doWriteCached(final AbstractPointersObject object, final int index,
                        final Object value,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached("cachedLayout.getLocation(index)") final SlotLocation cachedLocation,
                        @Cached final WriteSlotLocationNode writeNode) {
            if (!cachedLocation.canStore(value)) {
                /*
                 * Update layout in interpreter if it is not stable yet. This will also invalidate
                 * the assumption and therefore this particular instance of the specialization will
                 * be removed from the cache and replaced by an updated version.
                 */
                CompilerDirectives.transferToInterpreter();
                object.updateLayout(index, value);
                writeNode.execute(object.getLayout().getLocation(index), object, value);
                return;
            }
            try {
                writeNode.execute(cachedLocation, object, value);
            } catch (final IllegalWriteException e) {
                throw SqueakException.illegalState(e);
            }
        }

        @TruffleBoundary
        @Specialization(guards = "object.getLayout().isValid()", replaces = "doWriteCached")
        protected static final void doWriteUncached(final AbstractPointersObject object, final int index, final Object value,
                        @Cached final WriteSlotLocationNode writeNode) {
            try {
                writeNode.execute(object.getLayout().getLocation(index), object, value);
            } catch (final IllegalWriteException e) {
                /*
                 * Although the layout was valid, it is possible that the location cannot store the
                 * value. Generialize location in the interpreter.
                 */
                CompilerDirectives.transferToInterpreter();
                object.updateLayout(index, value);
                writeNode.execute(object.getLayout().getLocation(index), object, value);
            }
        }

        @Specialization(guards = "!object.getLayout().isValid()")
        protected static final void doUpdateLayoutAndWrite(final AbstractPointersObject object, final int index, final Object value) {
            /* Note that this specialization does not replace the cached specialization. */
            CompilerDirectives.transferToInterpreter();
            object.updateLayout();
            doWriteUncached(object, index, value, WriteSlotLocationNode.getUncached());
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class VariablePointersObjectReadNode extends Node {

        public abstract Object execute(VariablePointersObject object, int index);

        public abstract ArrayObject executeArray(VariablePointersObject object, int index);

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex < cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final Object doReadCached(final VariablePointersObject object, @SuppressWarnings("unused") final int index,
                        @Cached("index") final int cachedIndex,
                        @SuppressWarnings("unused") @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached final AbstractPointersObjectReadNode readNode) {
            return readNode.execute(object, cachedIndex);
        }

        @Specialization(guards = "index < object.instsize()", replaces = "doReadCached")
        protected static final Object doRead(final VariablePointersObject object, final int index,
                        @Cached final AbstractPointersObjectReadNode readNode) {
            return readNode.execute(object, index);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "VARIABLE_PART_INDEX_CACHE_LIMIT")
        protected static final Object doReadFromVariablePartCachedIndex(final VariablePointersObject object, @SuppressWarnings("unused") final int index,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout) {
            return object.getFromVariablePart(cachedIndex - cachedLayout.getInstSize());
        }

        @Specialization(guards = {"object.getLayout() == cachedLayout", "index >= cachedLayout.getInstSize()"}, //
                        replaces = "doReadFromVariablePartCachedIndex", limit = "VARIABLE_PART_LAYOUT_CACHE_LIMIT")
        protected static final Object doReadFromVariablePartCachedLayout(final VariablePointersObject object, final int index,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout) {
            return object.getFromVariablePart(index - cachedLayout.getInstSize());
        }

        @Specialization(guards = "index >= object.instsize()", replaces = {"doReadFromVariablePartCachedIndex", "doReadFromVariablePartCachedLayout"})
        protected static final Object doReadFromVariablePart(final VariablePointersObject object, final int index) {
            return object.getFromVariablePart(index - object.instsize());
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class VariablePointersObjectWriteNode extends Node {

        public abstract void execute(VariablePointersObject object, int index, Object value);

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex < cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final void doWriteCached(final VariablePointersObject object, @SuppressWarnings("unused") final int index, final Object value,
                        @Cached("index") final int cachedIndex,
                        @SuppressWarnings("unused") @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(object, cachedIndex, value);
        }

        @Specialization(guards = "index < object.instsize()", replaces = "doWriteCached")
        protected static final void doWrite(final VariablePointersObject object, final int index, final Object value,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(object, index, value);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "VARIABLE_PART_INDEX_CACHE_LIMIT")
        protected static final void doWriteIntoVariablePartCachedIndex(final VariablePointersObject object, @SuppressWarnings("unused") final int index, final Object value,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout) {
            object.putIntoVariablePart(cachedIndex - cachedLayout.getInstSize(), value);
        }

        @Specialization(guards = {"object.getLayout() == cachedLayout", "index >= cachedLayout.getInstSize()"}, //
                        replaces = "doWriteIntoVariablePartCachedIndex", limit = "VARIABLE_PART_LAYOUT_CACHE_LIMIT")
        protected static final void doWriteIntoVariablePartCachedLayout(final VariablePointersObject object, final int index, final Object value,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout) {
            object.putIntoVariablePart(index - cachedLayout.getInstSize(), value);
        }

        @Specialization(guards = "index >= object.instsize()", replaces = {"doWriteIntoVariablePartCachedIndex", "doWriteIntoVariablePartCachedLayout"})
        protected static final void doWriteIntoVariablePart(final VariablePointersObject object, final int index, final Object value) {
            object.putIntoVariablePart(index - object.instsize(), value);
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class WeakVariablePointersObjectReadNode extends Node {

        public abstract Object execute(WeakVariablePointersObject object, int index);

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex < cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final Object doReadCached(final WeakVariablePointersObject object, @SuppressWarnings("unused") final int index,
                        @Cached("index") final int cachedIndex,
                        @SuppressWarnings("unused") @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached final AbstractPointersObjectReadNode readNode) {
            return readNode.execute(object, cachedIndex);
        }

        @Specialization(guards = "index < object.instsize()", replaces = "doReadCached")
        protected static final Object doRead(final WeakVariablePointersObject object, final int index,
                        @Cached final AbstractPointersObjectReadNode readNode) {
            return readNode.execute(object, index);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "VARIABLE_PART_INDEX_CACHE_LIMIT")
        protected static final Object doReadFromVariablePartCachedIndex(final WeakVariablePointersObject object, @SuppressWarnings("unused") final int index,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached("createBinaryProfile()") final ConditionProfile nilProfile) {
            return object.getFromVariablePart(cachedIndex - cachedLayout.getInstSize(), nilProfile);
        }

        @Specialization(guards = {"object.getLayout() == cachedLayout", "index >= cachedLayout.getInstSize()"}, //
                        replaces = "doReadFromVariablePartCachedIndex", limit = "VARIABLE_PART_LAYOUT_CACHE_LIMIT")
        protected static final Object doReadFromVariablePartCachedLayout(final WeakVariablePointersObject object, final int index,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached("createBinaryProfile()") final ConditionProfile nilProfile) {
            return object.getFromVariablePart(index - cachedLayout.getInstSize(), nilProfile);
        }

        @Specialization(guards = "index >= object.instsize()", replaces = {"doReadFromVariablePartCachedIndex", "doReadFromVariablePartCachedLayout"})
        protected static final Object doReadFromVariablePart(final WeakVariablePointersObject object, final int index,
                        @Cached("createBinaryProfile()") final ConditionProfile nilProfile) {
            return object.getFromVariablePart(index - object.instsize(), nilProfile);
        }
    }

    @GenerateUncached
    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(AbstractPointersObjectNodes.class)
    public abstract static class WeakVariablePointersObjectWriteNode extends Node {

        public abstract void execute(WeakVariablePointersObject object, int index, Object value);

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex < cachedLayout.getInstSize()"}, limit = "CACHE_LIMIT")
        protected static final void doWriteCached(final WeakVariablePointersObject object, @SuppressWarnings("unused") final int index, final Object value,
                        @Cached("index") final int cachedIndex,
                        @SuppressWarnings("unused") @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(object, cachedIndex, value);
        }

        @Specialization(guards = "index < object.instsize()", replaces = "doWriteCached")
        protected static final void doWrite(final WeakVariablePointersObject object, final int index, final Object value,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(object, index, value);
        }

        @Specialization(guards = {"cachedIndex == index", "object.getLayout() == cachedLayout", "cachedIndex >= cachedLayout.getInstSize()"}, limit = "VARIABLE_PART_INDEX_CACHE_LIMIT")
        protected static final void doWriteIntoVariablePartCachedIndex(final WeakVariablePointersObject object, @SuppressWarnings("unused") final int index, final Object value,
                        @Cached("index") final int cachedIndex,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached final BranchProfile nilProfile,
                        @Cached("createBinaryProfile()") final ConditionProfile primitiveProfile) {
            object.putIntoVariablePart(cachedIndex - cachedLayout.getInstSize(), value, nilProfile, primitiveProfile);
        }

        @Specialization(guards = {"object.getLayout() == cachedLayout", "index >= cachedLayout.getInstSize()"}, //
                        replaces = "doWriteIntoVariablePartCachedIndex", limit = "VARIABLE_PART_LAYOUT_CACHE_LIMIT")
        protected static final void doWriteIntoVariablePartCachedLayout(final WeakVariablePointersObject object, final int index, final Object value,
                        @Cached("object.getLayout()") final ObjectLayout cachedLayout,
                        @Cached final BranchProfile nilProfile,
                        @Cached("createBinaryProfile()") final ConditionProfile primitiveProfile) {
            object.putIntoVariablePart(index - cachedLayout.getInstSize(), value, nilProfile, primitiveProfile);
        }

        @Specialization(guards = "index >= object.instsize()", replaces = {"doWriteIntoVariablePartCachedIndex", "doWriteIntoVariablePartCachedLayout"})
        protected static final void doWriteIntoVariablePart(final WeakVariablePointersObject object, final int index, final Object value,
                        @Cached final BranchProfile nilProfile,
                        @Cached("createBinaryProfile()") final ConditionProfile primitiveProfile) {
            object.putIntoVariablePart(index - object.instsize(), value, nilProfile, primitiveProfile);
        }
    }
}
