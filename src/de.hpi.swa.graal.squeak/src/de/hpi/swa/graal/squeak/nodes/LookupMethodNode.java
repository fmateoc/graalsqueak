package de.hpi.swa.graal.squeak.nodes;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.METHOD_DICT;

public abstract class LookupMethodNode extends AbstractNode {
    protected static final int LOOKUP_CACHE_SIZE = 3;
    protected static final Object DNU_MARKER = new Object();

    public static LookupMethodNode create() {
        return LookupMethodNodeGen.create();
    }

    public abstract Object executeLookup(ClassObject sqClass, NativeObject selector);

    @SuppressWarnings("unused")
    @Specialization(limit = "LOOKUP_CACHE_SIZE", guards = {"classObject == cachedClass",
                    "selector == cachedSelector"}, assumptions = {"classHierarchyStable", "methodDictStable"})
    protected static final Object doCached(final ClassObject classObject, final NativeObject selector,
                    @Cached("classObject") final ClassObject cachedClass,
                    @Cached("selector") final NativeObject cachedSelector,
                    @Cached("cachedClass.getClassHierarchyStable()") final Assumption classHierarchyStable,
                    @Cached("cachedClass.getMethodDictStable()") final Assumption methodDictStable,
                    @Cached("doLookup(cachedClass, cachedSelector)") final Object cachedMethod) {
        return cachedMethod;
    }

    @Specialization(replaces = "doCached")
    @TruffleBoundary
    protected static final Object doUncached(final ClassObject classObject, final NativeObject selector) {
        final EconomicMap<ClassObject, Object> mapping = classObject.image.methodCache.get(selector);
        if (mapping != null) {
            final Object value = mapping.get(classObject);
            if (value != null) {
                return value == DNU_MARKER ? null : value;
            } else {
                final Object lookupResult = doLookup(classObject, selector);
                mapping.put(classObject, lookupResult == null ? DNU_MARKER : lookupResult);
                return lookupResult;
            }
        } else {
            final Object lookupResult = doLookup(classObject, selector);
            final EconomicMap<ClassObject, Object> newMap = EconomicMap.create();
            newMap.put(classObject, lookupResult == null ? DNU_MARKER : lookupResult);
            classObject.image.methodCache.put(selector, newMap);
            return lookupResult;
        }
    }

    protected static final Object doLookup(final ClassObject classObject, final NativeObject selector) {
        ClassObject lookupClass = classObject;
        while (lookupClass != null) {
            final Object[] methodDictPointers = lookupClass.getMethodDict().getPointers();
            for (int i = METHOD_DICT.NAMES; i < methodDictPointers.length; i++) {
                if (selector == methodDictPointers[i]) {
                    return ((ArrayObject) methodDictPointers[METHOD_DICT.VALUES]).getObjectStorage()[i - METHOD_DICT.NAMES];
                }
            }
            lookupClass = lookupClass.getSuperclassOrNull();
        }
        assert !selector.isDoesNotUnderstand() : "Could not find does not understand method";
        return null; // Signals a doesNotUnderstand.
    }
}
