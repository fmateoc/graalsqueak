/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.util;

import static de.hpi.swa.graal.squeak.util.LoggerWrapper.Name.INTERRUPTS;

import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.layout.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.graal.squeak.nodes.process.SignalSemaphoreNode;

public final class InterruptHandlerNode extends Node {
    private static final LoggerWrapper LOG = LoggerWrapper.get(INTERRUPTS, Level.FINE);

    @Child private SignalSemaphoreNode signalSemaporeNode;
    private final Object[] specialObjects;
    private final InterruptHandlerState istate;
    private final boolean enableTimerInterrupts;

    private final BranchProfile nextWakeupTickProfile;
    private final BranchProfile pendingFinalizationSignalsProfile = BranchProfile.create();
    private final BranchProfile hasSemaphoresToSignalProfile = BranchProfile.create();

    protected InterruptHandlerNode(final CompiledCodeObject code, final boolean enableTimerInterrupts) {
        specialObjects = code.image.specialObjectsArray.getObjectStorage();
        istate = code.image.interrupt;
        signalSemaporeNode = SignalSemaphoreNode.create(code);
        this.enableTimerInterrupts = enableTimerInterrupts;
        nextWakeupTickProfile = enableTimerInterrupts ? BranchProfile.create() : null;
    }

    public static InterruptHandlerNode create(final CompiledCodeObject code, final boolean enableTimerInterrupts) {
        return new InterruptHandlerNode(code, enableTimerInterrupts);
    }

    public void executeTrigger(final VirtualFrame frame) {
        if (istate.interruptPending()) {
            /* Exclude user interrupt case from compilation. */
            CompilerDirectives.transferToInterpreter();
            istate.interruptPending = false; // reset interrupt flag
            assert LOG.fine("Signalling interrupt semaphore @%s in interrupt handler", c -> c.add(Integer.toHexString(istate.getInterruptSemaphore().hashCode())));
            signalSemaporeNode.executeSignal(frame, istate.getInterruptSemaphore());
        }
        if (enableTimerInterrupts && istate.nextWakeUpTickTrigger()) {
            nextWakeupTickProfile.enter();
            istate.nextWakeupTick = 0; // reset timer interrupt
            assert LOG.fine("Signalling timer semaphore @%s in interrupt handler", c -> c.add(Integer.toHexString(istate.getTimerSemaphore().hashCode())));
            signalSemaporeNode.executeSignal(frame, istate.getTimerSemaphore());
        }
        if (istate.pendingFinalizationSignals()) { // signal any pending finalizations
            pendingFinalizationSignalsProfile.enter();
            istate.setPendingFinalizations(false);
            assert LOG.fine("Signalling finalization semaphore @%s in interrupt handler", c -> c.add(Integer.toHexString(specialObjects[SPECIAL_OBJECT.THE_FINALIZATION_SEMAPHORE].hashCode())));
            signalSemaporeNode.executeSignal(frame, specialObjects[SPECIAL_OBJECT.THE_FINALIZATION_SEMAPHORE]);
        }
        if (istate.hasSemaphoresToSignal()) {
            hasSemaphoresToSignalProfile.enter();
            final ArrayObject externalObjects = (ArrayObject) specialObjects[SPECIAL_OBJECT.EXTERNAL_OBJECTS_ARRAY];
            if (!externalObjects.isEmptyType()) { // signal external semaphores
                final Object[] semaphores = externalObjects.getObjectStorage();
                Integer semaIndex;
                while ((semaIndex = istate.nextSemaphoreToSignal()) != null) {
                    final Object semaphore = semaphores[semaIndex - 1];
                    assert LOG.fine("Signalling external semaphore @%s in interrupt handler", c -> c.add(Integer.toHexString(semaphore.hashCode())));
                    signalSemaporeNode.executeSignal(frame, semaphore);
                }
            }
        }
    }
}
