/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes.process;

import static de.hpi.swa.graal.squeak.util.LoggerWrapper.Name.SCHEDULING;

import java.util.logging.Level;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.model.layout.ObjectLayouts.SEMAPHORE;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.graal.squeak.util.LoggerWrapper;

public abstract class SignalSemaphoreNode extends AbstractNode {
    private static final LoggerWrapper LOG = LoggerWrapper.get(SCHEDULING, Level.FINE);

    @Child private ResumeProcessNode resumeProcessNode;

    protected SignalSemaphoreNode(final CompiledCodeObject code) {
        resumeProcessNode = ResumeProcessNode.create(code);
    }

    public static SignalSemaphoreNode create(final CompiledCodeObject code) {
        return SignalSemaphoreNodeGen.create(code);
    }

    public abstract void executeSignal(VirtualFrame frame, Object semaphore);

    @Specialization(guards = {"semaphore.getSqueakClass().isSemaphoreClass()", "semaphore.isEmptyList(readNode)"}, limit = "1")
    public static final void doSignalEmpty(final PointersObject semaphore,
                    @Shared("readNode") @Cached final AbstractPointersObjectReadNode readNode,
                    @Shared("writeNode") @Cached final AbstractPointersObjectWriteNode writeNode) {
        final long excessSignals = readNode.executeLong(semaphore, SEMAPHORE.EXCESS_SIGNALS);
        assert LOG.fine("Signalling empty semaphore @%s with initially %d excessSignals", c -> c.add(Integer.toHexString(semaphore.hashCode())).add(excessSignals));
        writeNode.execute(semaphore, SEMAPHORE.EXCESS_SIGNALS, excessSignals + 1);
    }

    @Specialization(guards = {"semaphore.getSqueakClass().isSemaphoreClass()", "!semaphore.isEmptyList(readNode)"}, limit = "1")
    public final void doSignal(final VirtualFrame frame, final PointersObject semaphore,
                    @Shared("readNode") @Cached final AbstractPointersObjectReadNode readNode,
                    @Shared("writeNode") @Cached final AbstractPointersObjectWriteNode writeNode) {
        assert LOG.fine("Attempting to resume process after non-empty semaphore @%s signal", c -> c.add(Integer.toHexString(semaphore.hashCode())));
        resumeProcessNode.executeResume(frame, semaphore.removeFirstLinkOfList(readNode, writeNode));
    }

    @Specialization
    protected static final void doNothing(@SuppressWarnings("unused") final NilObject nil) {
        // nothing to do
    }

    @Specialization(guards = "object == null")
    protected static final void doNothing(@SuppressWarnings("unused") final Object object) {
        // nothing to do
    }
}
