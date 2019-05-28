package de.hpi.swa.graal.squeak.nodes.process;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

public abstract class YieldProcessNode extends AbstractNodeWithCode {
    @Child private LinkProcessToListNode linkProcessToListNode;
    @Child private WakeHighestPriorityNode wakeHighestPriorityNode;

    protected YieldProcessNode(final CompiledCodeObject code) {
        super(code);
    }

    public static YieldProcessNode create(final CompiledCodeObject image) {
        return YieldProcessNodeGen.create(image);
    }

    public abstract void executeYield(VirtualFrame frame, PointersObject scheduler);

    @Specialization
    protected final void doYield(final VirtualFrame frame, final PointersObject scheduler,
                    @CachedLibrary(limit = "1") final SqueakObjectLibrary objectLibrary) {
        final PointersObject activeProcess = code.image.getActiveProcess();
        final int priority = (int) (long) activeProcess.at0(PROCESS.PRIORITY);
        final ArrayObject processLists = (ArrayObject) scheduler.at0(PROCESS_SCHEDULER.PROCESS_LISTS);
        final PointersObject processList = (PointersObject) objectLibrary.at0(processLists, priority - 1);
        if (!processList.isEmptyList()) {
            getLinkProcessToListNode().executeLink(activeProcess, processList);
            getWakeHighestPriorityNode().executeWake(frame);
        }
    }

    private LinkProcessToListNode getLinkProcessToListNode() {
        if (linkProcessToListNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            linkProcessToListNode = insert(LinkProcessToListNode.create());
        }
        return linkProcessToListNode;
    }

    private WakeHighestPriorityNode getWakeHighestPriorityNode() {
        if (wakeHighestPriorityNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            wakeHighestPriorityNode = insert(WakeHighestPriorityNode.create(code));
        }
        return wakeHighestPriorityNode;
    }
}
