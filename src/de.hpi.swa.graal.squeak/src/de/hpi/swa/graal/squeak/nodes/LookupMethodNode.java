package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.NativeObject;

public abstract class LookupMethodNode extends AbstractNode {
    protected static final int LOOKUP_CACHE_SIZE = 3;

    public static LookupMethodNode create() {
        return LookupMethodNodeGen.create();
    }

    public abstract Object executeLookup(ClassObject sqClass, NativeObject selector);

    @SuppressWarnings("unused")
    @Specialization(limit = "LOOKUP_CACHE_SIZE", guards = {"classObject == cachedClass", "selector == cachedSelector"}, //
                    assumptions = {"cachedClass.getClassHierarchyStable()", "cachedClass.getMethodDictStable()"})
    protected static final Object doCached(final ClassObject classObject, final NativeObject selector,
                    @Cached("classObject") final ClassObject cachedClass,
                    @Cached("selector") final NativeObject cachedSelector,
                    @Cached("cachedClass.lookupSelectorInHierachy(cachedSelector)") final Object cachedMethod) {
        return cachedMethod;
    }

    @Specialization(replaces = "doCached")
    protected static final Object doUncached(final ClassObject classObject, final NativeObject selector) {
        return classObject.lookupSelector(selector);
    }
}
