package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

/**
 * This node should only be used for stores into associations, receivers, and remote temps as it
 * also marks {@link ContextObject}s as escaped when stored.
 */
@NodeInfo(cost = NodeCost.NONE)
public abstract class SqueakObjectAtPutAndMarkContextsNode extends AbstractNode {
    private final int index;

    protected SqueakObjectAtPutAndMarkContextsNode(final int variableIndex) {
        index = variableIndex;
    }

    public static SqueakObjectAtPutAndMarkContextsNode create(final int index) {
        return SqueakObjectAtPutAndMarkContextsNodeGen.create(index);
    }

    public abstract void executeWrite(Object object, Object value);

    @Specialization
    protected final void doContext(final Object object, final ContextObject value,
                    @Shared("objectLibrary") @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
        value.markEscaped();
        objectLibrary.atput0(object, index, value);
    }

    @Specialization(guards = {"!isContextObject(value)"})
    protected final void doSqueakObject(final Object object, final Object value,
                    @Shared("objectLibrary") @CachedLibrary(limit = "3") final SqueakObjectLibrary objectLibrary) {
        objectLibrary.atput0(object, index, value);
    }
}
