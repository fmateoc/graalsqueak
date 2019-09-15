package de.hpi.swa.graal.squeak.nodes.process;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;

public final class ResumeProcessNode extends AbstractNodeWithCode {
    @Child private AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();
    @Child private PutToSleepNode putToSleepNode;
    @Child private GetOrCreateContextNode contextNode;

    private ConditionProfile hasHigherPriorityProfile = ConditionProfile.createBinaryProfile();

    protected ResumeProcessNode(final CompiledCodeObject code) {
        super(code);
        putToSleepNode = PutToSleepNode.create(code.image);
    }

    public static ResumeProcessNode create(final CompiledCodeObject code) {
        return new ResumeProcessNode(code);
    }

    public void executeResume(final VirtualFrame frame, final PointersObject newProcess) {
        final long processPriority = newProcess.getProcessPriority(readNode);
        final PointersObject activeProcess = code.image.getActiveProcess(readNode);
        final long activeProcessPriority = activeProcess.getProcessPriority(readNode);
        if (hasHigherPriorityProfile.profile(processPriority > activeProcessPriority)) {
            putToSleepNode.executePutToSleep(activeProcess);
            if (contextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextNode = insert(GetOrCreateContextNode.create(code));
            }
            contextNode.executeGet(frame).transferTo(newProcess, activeProcess);
        } else {
            putToSleepNode.executePutToSleep(newProcess);
        }
    }
}
