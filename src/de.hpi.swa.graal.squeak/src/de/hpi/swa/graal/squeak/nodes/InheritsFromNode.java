package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

public abstract class InheritsFromNode extends AbstractNode {
    protected static final int CACHE_SIZE = 3;

    public static InheritsFromNode create() {
        return InheritsFromNodeGen.create();
    }

    public abstract boolean execute(Object receiver, ClassObject classObject);

    @SuppressWarnings("unused")
    @Specialization(limit = "CACHE_SIZE", guards = {"receiver == cachedReceiver", "classObject == cachedClass"}, assumptions = {"classHierarchyStable"})
    protected static final boolean doCached(final Object receiver, final ClassObject classObject,
                    @Cached("receiver") final Object cachedReceiver,
                    @Cached("classObject") final ClassObject cachedClass,
                    @Cached("cachedClass.getClassHierarchyStable()") final Assumption classHierarchyStable,
                    @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary,
                    @Cached("doUncached(receiver, cachedClass, objectLibrary)") final boolean inInheritanceChain) {
        return inInheritanceChain;
    }

    @Specialization(replaces = "doCached", limit = "1")
    protected static final boolean doUncached(final Object receiver, final ClassObject superClass,
                    @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
        ClassObject classObject = objectLibrary.squeakClass(receiver);
        while (classObject != superClass) {
            classObject = classObject.getSuperclassOrNull();
            if (classObject == null) {
                return false;
            }
        }
        return true;
    }
}
