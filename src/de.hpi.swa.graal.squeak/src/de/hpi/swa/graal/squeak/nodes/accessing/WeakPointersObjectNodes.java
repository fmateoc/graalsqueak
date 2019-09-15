package de.hpi.swa.graal.squeak.nodes.accessing;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.WeakPointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.accessing.WeakPointersObjectNodesFactory.WeakPointersObjectWriteNodeGen;

public final class WeakPointersObjectNodes {

    @GenerateUncached
    public abstract static class WeakPointersObjectReadNode extends AbstractNode {

        public abstract Object executeRead(WeakPointersObject pointers, long index);

        @Specialization
        protected static final Object doRead(final WeakPointersObject pointers, final long index,
                        @Cached final AbstractPointersObjectReadNode readNode,
                        @Cached("createBinaryProfile()") final ConditionProfile isWeakRefProfile,
                        @Cached("createBinaryProfile()") final ConditionProfile isNullProfile) {
            final Object value = readNode.executeRead(pointers, index);
            if (isWeakRefProfile.profile(value instanceof WeakReference<?>)) {
                return NilObject.nullToNil(((WeakReference<?>) value).get(), isNullProfile);
            } else {
                return value;
            }
        }
    }

    @GenerateUncached
    public abstract static class WeakPointersObjectWriteNode extends AbstractNode {

        public static WeakPointersObjectWriteNode getUncached() {
            return WeakPointersObjectWriteNodeGen.getUncached();
        }

        public abstract void execute(WeakPointersObject pointers, long index, Object value);

        @Specialization
        protected static final void doWeakInVariablePart(final WeakPointersObject pointers, final long index, final AbstractSqueakObject value,
                        @Cached final AbstractPointersObjectWriteNode writeNode,
                        @Cached("createBinaryProfile()") final ConditionProfile inVariablePartProfile) {
            if (inVariablePartProfile.profile(pointers.getSqueakClass().getBasicInstanceSize() <= index)) {
                writeNode.executeWrite(pointers, index, pointers.asWeakRef(value));
            } else {
                writeNode.executeWrite(pointers, index, value);
            }
        }
    }
}
