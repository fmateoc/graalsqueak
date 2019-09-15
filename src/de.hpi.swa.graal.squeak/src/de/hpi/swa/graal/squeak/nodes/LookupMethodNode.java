package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.METHOD_DICT;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectSizeNode;

@ReportPolymorphism
public abstract class LookupMethodNode extends AbstractNode {
    protected static final int LOOKUP_CACHE_SIZE = 6;

    public static LookupMethodNode create() {
        return LookupMethodNodeGen.create();
    }

    public abstract Object executeLookup(ClassObject sqClass, NativeObject selector);

    @SuppressWarnings("unused")
    @Specialization(limit = "LOOKUP_CACHE_SIZE", guards = {"classObject == cachedClass",
                    "selector == cachedSelector"}, assumptions = {"cachedClass.getClassHierarchyStable()", "cachedClass.getMethodDictStable()"})
    protected static final Object doCached(final ClassObject classObject, final NativeObject selector,
                    @Cached("classObject") final ClassObject cachedClass,
                    @Cached("selector") final NativeObject cachedSelector,
                    @Cached("lookupUncached(cachedClass, cachedSelector)") final Object cachedMethod) {
        return cachedMethod;
    }

    protected static final Object lookupUncached(final ClassObject classObject, final NativeObject selector) {
        return doGeneric(classObject, selector, AbstractPointersObjectSizeNode.getUncached(), AbstractPointersObjectReadNode.getUncached());
    }

    @Specialization(replaces = "doCached")
    protected static final Object doGeneric(final ClassObject classObject, final NativeObject selector,
                    @Cached final AbstractPointersObjectSizeNode sizeNode,
                    @Cached final AbstractPointersObjectReadNode readNode) {
        ClassObject lookupClass = classObject;
        while (lookupClass != null) {
            final PointersObject methodDict = lookupClass.getMethodDict();
            for (int i = METHOD_DICT.NAMES; i < sizeNode.executeSize(methodDict); i++) {
                if (selector == readNode.executeRead(methodDict, i)) {
                    return ((ArrayObject) readNode.executeRead(methodDict, METHOD_DICT.VALUES)).getObjectStorage()[i - METHOD_DICT.NAMES];
                }
            }
            lookupClass = lookupClass.getSuperclassOrNull();
        }
        assert !selector.isDoesNotUnderstand() : "Could not find does not understand method";
        return null; // Signals a doesNotUnderstand.
    }
}
