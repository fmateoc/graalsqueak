/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes.process;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithImage;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.graal.squeak.util.DebugUtils;
import de.hpi.swa.graal.squeak.util.LogUtils;

public final class WakeHighestPriorityNode extends AbstractNodeWithImage {

    @Child private ArrayObjectReadNode arrayReadNode = ArrayObjectReadNode.create();
    @Child private ArrayObjectSizeNode arraySizeNode = ArrayObjectSizeNode.create();
    @Child private AbstractPointersObjectWriteNode pointersWriteNode = AbstractPointersObjectWriteNode.create();
    private final BranchProfile errorProfile = BranchProfile.create();
    @Child private GetOrCreateContextNode contextNode;

    private WakeHighestPriorityNode(final CompiledCodeObject code) {
        super(code.image);
        contextNode = GetOrCreateContextNode.create(code);
    }

    public static WakeHighestPriorityNode create(final CompiledCodeObject code) {
        return new WakeHighestPriorityNode(code);
    }

    public void executeWake(final VirtualFrame frame) {
        // Return the highest priority process that is ready to run.
        // Note: It is a fatal VM error if there is no runnable process.
        final ArrayObject schedLists = image.getProcessLists();
        for (long p = arraySizeNode.execute(schedLists) - 1; p >= 0; p--) {
            final PointersObject processList = (PointersObject) arrayReadNode.execute(schedLists, p);
            while (!processList.isEmptyList()) {
                final PointersObject newProcess = processList.removeFirstLinkOfList(pointersWriteNode);
                final AbstractSqueakObject newContext = newProcess.getSuspendedContext();
                final int priority = (int) (p + 1);
                if (newContext instanceof ContextObject) {
                    final ContextObject thisContext = contextNode.executeGet(frame, NilObject.SINGLETON);
                    LogUtils.SCHEDULING.fine(() -> DebugUtils.logSwitch(newProcess, priority, image.getActiveProcess(), thisContext, (ContextObject) newContext));
                    thisContext.transferTo(pointersWriteNode, newProcess);
                    throw SqueakException.create("Should not be reached");
                } else {
                    LogUtils.SCHEDULING.severe(() -> "evicted zombie process from run queue " + priority);
                }
            }
        }
        errorProfile.enter();
        throw SqueakException.create("scheduler could not find a runnable process");
    }

}
