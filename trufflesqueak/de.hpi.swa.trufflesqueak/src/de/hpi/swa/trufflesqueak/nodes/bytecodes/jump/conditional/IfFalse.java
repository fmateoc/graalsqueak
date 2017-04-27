package de.hpi.swa.trufflesqueak.nodes.bytecodes.jump.conditional;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.trufflesqueak.model.FalseObject;
import de.hpi.swa.trufflesqueak.model.TrueObject;
import de.hpi.swa.trufflesqueak.nodes.SqueakNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.Pop;

@NodeChildren({@NodeChild(value = "cond", type = Pop.class)})
public abstract class IfFalse extends SqueakNode {
    @Specialization
    public boolean checkCondition(boolean cond) {
        return !cond;
    }

    @Specialization
    public boolean checkCondition(@SuppressWarnings("unused") FalseObject cond) {
        return true;
    }

    @Specialization
    public boolean checkCondition(@SuppressWarnings("unused") TrueObject cond) {
        return false;
    }

    @Fallback
    public Object checkCondition(@SuppressWarnings("unused") Object cond) {
        return null;
    }
}
