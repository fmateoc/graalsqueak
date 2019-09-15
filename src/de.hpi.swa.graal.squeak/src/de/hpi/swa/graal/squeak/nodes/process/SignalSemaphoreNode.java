package de.hpi.swa.graal.squeak.nodes.process;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SEMAPHORE;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.accessing.PointersObjectNodes.PointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.PointersObjectNodes.PointersObjectWriteNode;

public abstract class SignalSemaphoreNode extends AbstractNodeWithCode {
    @Child private PointersObjectReadNode semaReadNode;
    @Child private PointersObjectWriteNode semaWriteNode;
    @Child private PointersObjectWriteNode writeNode;
    @Child private ResumeProcessNode resumeProcessNode;

    protected SignalSemaphoreNode(final CompiledCodeObject code) {
        super(code);
    }

    public static SignalSemaphoreNode create(final CompiledCodeObject code) {
        return SignalSemaphoreNodeGen.create(code);
    }

    public abstract void executeSignal(VirtualFrame frame, Object semaphore);

    @Specialization(guards = {"semaphore.getSqueakClass().isSemaphoreClass()"})
    public final void doSignalEmpty(final VirtualFrame frame, final PointersObject semaphore,
                    @Cached final PointersObjectReadNode readNode,
                    @Cached("createBinaryProfile()") final ConditionProfile isEmptyListProfile) {
        if (isEmptyListProfile.profile(semaphore.isEmptyList(readNode))) {
            if (semaReadNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                semaReadNode = insert(PointersObjectReadNode.create());
                semaWriteNode = insert(PointersObjectWriteNode.create());
            }
            semaWriteNode.executeWrite(semaphore, SEMAPHORE.EXCESS_SIGNALS, (long) semaReadNode.executeRead(semaphore, SEMAPHORE.EXCESS_SIGNALS) + 1);
        } else {
            if (resumeProcessNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resumeProcessNode = insert(ResumeProcessNode.create(code));
                writeNode = insert(PointersObjectWriteNode.create());
            }
            resumeProcessNode.executeResume(frame, semaphore.removeFirstLinkOfList(readNode, writeNode));
        }
    }

    @Specialization
    protected static final void doNothing(@SuppressWarnings("unused") final NilObject nil) {
        // nothing to do
    }
}
