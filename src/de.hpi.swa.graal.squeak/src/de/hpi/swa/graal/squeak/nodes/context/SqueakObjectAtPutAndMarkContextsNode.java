package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectAtPut0Node;

/**
 * This node should only be used for stores into associations, receivers, and remote temps as it
 * also marks {@link ContextObject}s as escaped when stored.
 */
@NodeInfo(cost = NodeCost.NONE)
public abstract class SqueakObjectAtPutAndMarkContextsNode extends AbstractNode {
    @Child private SqueakObjectAtPut0Node atPut0Node = SqueakObjectAtPut0Node.create();

    protected SqueakObjectAtPutAndMarkContextsNode() {
    }

    public static SqueakObjectAtPutAndMarkContextsNode create() {
        return SqueakObjectAtPutAndMarkContextsNodeGen.create();
    }

    public abstract void executeWrite(Object object, int index, Object value);

    @Specialization(guards = {"!isNativeObject(object)"})
    protected final void doContext(final AbstractSqueakObject object, final int index, final ContextObject value) {
        value.markEscaped();
        atPut0Node.execute(object, index, value);
    }

    @Specialization(guards = {"!isNativeObject(object)", "!isContextObject(value)"})
    protected final void doSqueakObject(final AbstractSqueakObject object, final int index, final Object value) {
        atPut0Node.execute(object, index, value);
    }

    @Fallback
    protected static final void doFail(final Object object, final int index, final Object value) {
        throw SqueakException.create(object, "at:", index, "put:", value, "failed");
    }
}
