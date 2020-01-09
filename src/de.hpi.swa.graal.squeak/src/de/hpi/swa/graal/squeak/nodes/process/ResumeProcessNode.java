/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes.process;

import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.primitives.impl.ControlPrimitives;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;

public abstract class ResumeProcessNode extends AbstractNodeWithCode {
    protected static final boolean TRUE = true;
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, ControlPrimitives.class);
    private static final boolean isLoggingEnabled = LOG.isLoggable(Level.FINE);

    @Child private PutToSleepNode putToSleepNode;

    protected ResumeProcessNode(final CompiledCodeObject code) {
        super(code);
        putToSleepNode = PutToSleepNode.create(code.image);
    }

    public static ResumeProcessNode create(final CompiledCodeObject code) {
        return ResumeProcessNodeGen.create(code);
    }

    public abstract void executeResume(VirtualFrame frame, PointersObject newProcess);

    @Specialization(guards = "hasHigherPriority(newProcess)")
    protected final void doTransferTo(final VirtualFrame frame, final PointersObject newProcess,
                    @Cached final AbstractPointersObjectWriteNode pointersWriteNode,
                    @Cached("create(code)") final GetOrCreateContextNode contextNode) {
        final PointersObject currentProcess = code.image.getActiveProcess();
        putToSleepNode.executePutToSleep(currentProcess);
        final ContextObject thisContext = contextNode.executeGet(frame, currentProcess);
        if (isLoggingEnabled) {
            LOG.fine(() -> logSwitch(newProcess, currentProcess, thisContext));
        }
        thisContext.transferTo(pointersWriteNode, newProcess);
    }

    private static String logSwitch(final PointersObject newProcess, final PointersObject currentProcess, final ContextObject thisContext) {
        final StringBuilder b = new StringBuilder();
        b.append("Switching from process @");
        b.append(Integer.toHexString(currentProcess.hashCode()));
        b.append(" with priority ");
        b.append(currentProcess.getPriority());
        b.append(" and stack\n");
        thisContext.printSqMaterializedStackTraceOn(b);
        b.append("\n...to process @");
        b.append(Integer.toHexString(newProcess.hashCode()));
        b.append(" with priority ");
        b.append(newProcess.getPriority());
        b.append(" and stack\n");
        final ContextObject newContext = newProcess.getSuspendedContext();
        newContext.printSqMaterializedStackTraceOn(b);
        return b.toString();
    }

    @Specialization(guards = "!hasHigherPriority(newProcess)")
    protected final void doSleep(final PointersObject newProcess) {
        putToSleepNode.executePutToSleep(newProcess);
        if (isLoggingEnabled) {
            LOG.fine(() -> logNoSwitch(newProcess));
        }
    }

    private String logNoSwitch(final PointersObject newProcess) {
        final StringBuilder b = new StringBuilder();
        b.append("\nCannot resume process @");
        b.append(Integer.toHexString(newProcess.hashCode()));
        b.append(" with priority ");
        b.append(newProcess.getPriority());
        b.append(" and stack\n");
        final ContextObject newContext = newProcess.getSuspendedContext();
        newContext.printSqMaterializedStackTraceOn(b);
        b.append("\n...because it hs a lower priority than the currently active process @");
        final PointersObject currentProcess = code.image.getActiveProcess();
        b.append(Integer.toHexString(currentProcess.hashCode()));
        b.append(" with priority ");
        b.append(currentProcess.getPriority());
        return b.toString();
    }

    protected final boolean hasHigherPriority(final PointersObject newProcess) {
        return newProcess.getPriority() > code.image.getActivePriority();
    }
}
