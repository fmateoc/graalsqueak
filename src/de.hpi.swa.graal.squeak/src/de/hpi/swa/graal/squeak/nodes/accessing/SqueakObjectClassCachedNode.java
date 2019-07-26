package de.hpi.swa.graal.squeak.nodes.accessing;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObjectWithClassAndHash;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;

@NodeInfo(cost = NodeCost.NONE)
public abstract class SqueakObjectClassCachedNode extends AbstractNode {
    protected final int cacheLimit;

    public SqueakObjectClassCachedNode(final int limit) {
        cacheLimit = limit;
    }

    public static SqueakObjectClassCachedNode create() {
        return create(1);
    }

    public static SqueakObjectClassCachedNode create(final int limit) {
        return SqueakObjectClassCachedNodeGen.create(limit);
    }

    public abstract ClassObject executeLookup(Object receiver);

    @Specialization(guards = "receiver == unpackWeakReference(cachedReceiverWeak)", assumptions = "cachedSqueakClassStableAssumption", limit = "cacheLimit")
    protected static final ClassObject doCached(@SuppressWarnings("unused") final AbstractSqueakObjectWithClassAndHash receiver,
                    @SuppressWarnings("unused") @Cached("asWeakReference(receiver)") final WeakReference<AbstractSqueakObjectWithClassAndHash> cachedReceiverWeak,
                    @SuppressWarnings("unused") @Cached("receiver.getSqueakClassStableAssumption()") final Assumption cachedSqueakClassStableAssumption,
                    @Cached("receiver.getSqueakClass()") final ClassObject cachedSqueakClass) {
        return cachedSqueakClass;
    }

    public static AbstractSqueakObjectWithClassAndHash unpackWeakReference(final WeakReference<AbstractSqueakObjectWithClassAndHash> weakRef) {
        return weakRef.get();
    }

    @Specialization(replaces = "doCached")
    protected static final ClassObject doUncached(final Object receiver,
                    @Cached final SqueakObjectClassNode classNode) {
        return classNode.executeLookup(receiver);
    }
}
