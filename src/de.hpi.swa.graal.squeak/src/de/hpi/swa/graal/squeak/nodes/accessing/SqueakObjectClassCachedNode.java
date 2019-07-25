package de.hpi.swa.graal.squeak.nodes.accessing;

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

    @Specialization(guards = "receiver == cachedReceiver", assumptions = "cachedReceiver.getSqueakClassStableAssumption()", limit = "cacheLimit")
    protected static final ClassObject doCached(@SuppressWarnings("unused") final AbstractSqueakObjectWithClassAndHash receiver,
                    @SuppressWarnings("unused") @Cached("receiver") final AbstractSqueakObjectWithClassAndHash cachedReceiver,
                    @Cached("cachedReceiver.getSqueakClass()") final ClassObject cachedSqueakClass) {
        return cachedSqueakClass;
    }

    @Specialization(replaces = "doCached")
    protected static final ClassObject doUncached(final Object receiver,
                    @Cached final SqueakObjectClassNode classNode) {
        return classNode.executeLookup(receiver);
    }
}
