package de.hpi.swa.graal.squeak.nodes.accessing;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ClassObject;

@GenerateUncached
public abstract class SqueakObjectClassNode extends Node {
    public abstract ClassObject executeClass(AbstractSqueakObject object);

    @Specialization(guards = "identical(object, cachedObject)", limit = "2", assumptions = "squeakClassStableAssumption")
    protected static final ClassObject doCached(@SuppressWarnings("unused") final AbstractSqueakObject object,
                    @SuppressWarnings("unused") @Cached(value = "weakReferenceFor(object)", allowUncached = true) final WeakReference<Object> cachedObject,
                    @SuppressWarnings("unused") @Cached(value = "object.getSqueakClassStableAssumption()", allowUncached = true) final Assumption squeakClassStableAssumption,
                    @Cached(value = "object.getSqueakClass()", allowUncached = true) final ClassObject classObject) {
        return classObject;
    }

    @Specialization
    protected static final ClassObject doUncached(final AbstractSqueakObject object) {
        return object.getSqueakClass();
    }

    protected static final boolean identical(final Object object, final WeakReference<Object> cachedObject) {
        return object == cachedObject.get();
    }

    protected static final WeakReference<Object> weakReferenceFor(final Object value) {
        return new WeakReference<>(value);
    }
}
