package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.ContextObject;

@NodeInfo(cost = NodeCost.NONE)
public abstract class EscapeMarkerNode extends Node {

    public static EscapeMarkerNode create() {
        return EscapeMarkerNodeGen.create();
    }

    public abstract Object execute(Object object);

    @Specialization
    protected static final ContextObject doContext(final ContextObject object) {
        object.markEscaped();
        return object;
    }

    @Specialization
    protected static final BlockClosureObject doBlockClosureObject(final BlockClosureObject object) {
        final ContextObject outerContext = object.getOuterContextOrNull();
        if (outerContext != null) {
            outerContext.markEscaped();
        }
        return object;
    }

    @Fallback
    protected static final Object doNothing(final Object object) {
        return object;
    }
}
