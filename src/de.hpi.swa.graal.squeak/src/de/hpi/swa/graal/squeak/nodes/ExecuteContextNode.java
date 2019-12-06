/*
 * Copyright (c) 2017-2019 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes;

import java.util.logging.Level;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.exceptions.ProcessSwitch;
import de.hpi.swa.graal.squeak.exceptions.Returns.NonLocalReturn;
import de.hpi.swa.graal.squeak.exceptions.Returns.NonVirtualReturn;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.nodes.ExecuteContextNodeFactory.TriggerInterruptHandlerNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.AbstractBytecodeNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.JumpBytecodes.ConditionalJumpNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.JumpBytecodes.UnconditionalJumpNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushClosureNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushLiteralConstantNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushLiteralVariableNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.ReturnBytecodes.AbstractReturnNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.SendBytecodes.AbstractSendNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackInitializationNode;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.InterruptHandlerNode;
import de.hpi.swa.graal.squeak.util.SqueakBytecodeDecoder;

@GenerateWrapper
public class ExecuteContextNode extends AbstractNodeWithCode implements InstrumentableNode {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, ExecuteContextNode.class);
    private static final boolean DECODE_BYTECODE_ON_DEMAND = true;
    private static final int STACK_DEPTH_LIMIT = 25000;
    private static final int LOCAL_RETURN_PC = -1;

    @Children private AbstractBytecodeNode[] bytecodeNodes;
    @Child private HandleNonLocalReturnNode handleNonLocalReturnNode;
    @Child private GetOrCreateContextNode getOrCreateContextNode;

    @Child private FrameStackInitializationNode frameInitializationNode;
    @Child private HandlePrimitiveFailedNode handlePrimitiveFailedNode;
    @Child private MaterializeContextOnMethodExitNode materializeContextOnMethodExitNode;

    private SourceSection section;
    private final String toString;
    private static final boolean isLoggingEnabled = LOG.isLoggable(Level.FINER);

    protected ExecuteContextNode(final CompiledCodeObject code, final boolean resume) {
        super(code);
        if (DECODE_BYTECODE_ON_DEMAND) {
            bytecodeNodes = new AbstractBytecodeNode[SqueakBytecodeDecoder.trailerPosition(code)];
        } else {
            bytecodeNodes = SqueakBytecodeDecoder.decode(code);
        }
        frameInitializationNode = resume ? null : FrameStackInitializationNode.create(code);
        materializeContextOnMethodExitNode = resume ? null : MaterializeContextOnMethodExitNode.create(code);
        toString = code.toString();
    }

    protected ExecuteContextNode(final ExecuteContextNode executeContextNode) {
        this(executeContextNode.code, executeContextNode.frameInitializationNode == null);
    }

    public static ExecuteContextNode create(final CompiledCodeObject code, final boolean resume) {
        return new ExecuteContextNode(code, resume);
    }

    @Override
    public final String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return code.toString();
    }

    public Object executeFresh(final VirtualFrame frame) {
        FrameAccess.setInstructionPointer(frame, code, 0);
        final boolean enableStackDepthProtection = enableStackDepthProtection();
        try {
            if (enableStackDepthProtection && code.image.stackDepth++ > STACK_DEPTH_LIMIT) {
                final ContextObject context = getGetOrCreateContextNode().executeGet(frame);
                context.setProcess(code.image.getActiveProcess(AbstractPointersObjectReadNode.getUncached()));
                throw ProcessSwitch.createWithBoundary(context);
            }
            frameInitializationNode.executeInitialize(frame);
            return startBytecode(frame);
        } catch (final NonLocalReturn nlr) {
            if (!isLoggingEnabled) {
                LOG.finer("Exited " + toString + " through a non-local return");
            }
            /** {@link getHandleNonLocalReturnNode()} acts as {@link BranchProfile} */
            return getHandleNonLocalReturnNode().executeHandle(frame, nlr);
        } catch (final NonVirtualReturn nvr) {
            if (!isLoggingEnabled) {
                LOG.finer("Exited " + toString + " through a non-virtual return");
            }
            /** {@link getGetOrCreateContextNode()} acts as {@link BranchProfile} */
            getGetOrCreateContextNode().executeGet(frame).markEscaped();
            throw nvr;
        } catch (final ProcessSwitch ps) {
            if (!isLoggingEnabled) {
                LOG.finer("Exited " + toString + " through a process switch");
            }
            /** {@link getGetOrCreateContextNode()} acts as {@link BranchProfile} */
            getGetOrCreateContextNode().executeGet(frame).markEscaped();
            throw ps;
        } finally {
            if (enableStackDepthProtection) {
                code.image.stackDepth--;
            }
            materializeContextOnMethodExitNode.execute(frame);
        }
    }

    public final Object executeResumeAtStart(final VirtualFrame frame) {
        try {
            return startBytecode(frame);
        } catch (final NonLocalReturn nlr) {
            if (!isLoggingEnabled) {
                LOG.finer("Exited " + toString + " through a non-local return");
            }
            /** {@link getHandleNonLocalReturnNode()} acts as {@link BranchProfile} */
            return getHandleNonLocalReturnNode().executeHandle(frame, nlr);
        } finally {
            code.image.lastSeenContext = null; // Stop materialization here.
        }
    }

    public final Object executeResumeInMiddle(final VirtualFrame frame, final long initialPC) {
        try {
            return resumeBytecode(frame, initialPC);
        } catch (final NonLocalReturn nlr) {
            if (!isLoggingEnabled) {
                LOG.finer("Exited " + toString + " through a non-local return");
            }
            /** {@link getHandleNonLocalReturnNode()} acts as {@link BranchProfile} */
            return getHandleNonLocalReturnNode().executeHandle(frame, nlr);
        } finally {
            code.image.lastSeenContext = null; // Stop materialization here.
        }
    }

    private GetOrCreateContextNode getGetOrCreateContextNode() {
        if (getOrCreateContextNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getOrCreateContextNode = insert(GetOrCreateContextNode.create(code, false));
        }
        return getOrCreateContextNode;
    }

    /*
     * Inspired by Sulong's LLVMDispatchBasicBlockNode (https://git.io/fjEDw).
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    private Object startBytecode(final VirtualFrame frame) {
        CompilerAsserts.compilationConstant(bytecodeNodes.length);
        final String frameString;
        if (isLoggingEnabled) {
            frameString = " frame @" + Integer.toHexString(frame.hashCode()) + ", " + toString;
            LOG.finer(() -> "Executing fresh " + frameString);
        } else {
            frameString = toString;
        }
        int pc = 0;
        int backJumpCounter = 0;
        Object returnValue = null;
        bytecode_loop: while (pc != LOCAL_RETURN_PC) {
            CompilerAsserts.partialEvaluationConstant(pc);
            final AbstractBytecodeNode node = fetchNextBytecodeNode(pc);
            if (node instanceof CallPrimitiveNode) {
                final CallPrimitiveNode callPrimitiveNode = (CallPrimitiveNode) node;
                if (callPrimitiveNode.primitiveNode != null) {
                    try {
                        if (isLoggingEnabled) {
                            LOG.finer("Primitive return from " + frameString);
                        }
                        return callPrimitiveNode.primitiveNode.executePrimitive(frame);
                    } catch (final PrimitiveFailed e) {
                        getHandlePrimitiveFailedNode().executeHandle(frame, e.getReasonCode());
                        /* continue with fallback code. */
                    }
                }
                pc = callPrimitiveNode.getSuccessorIndex();
                assert pc == CallPrimitiveNode.NUM_BYTECODES;
                continue;
            } else if (node instanceof ConditionalJumpNode) {
                final ConditionalJumpNode jumpNode = (ConditionalJumpNode) node;
                if (jumpNode.executeCondition(frame)) {
                    final int successor = jumpNode.getJumpSuccessorIndex();
                    if (successor <= pc) {
                        if (CompilerDirectives.inInterpreter()) {
                            backJumpCounter++;
                        }
                    }
                    pc = successor;
                    continue bytecode_loop;
                } else {
                    final int successor = jumpNode.getSuccessorIndex();
                    if (successor <= pc) {
                        if (CompilerDirectives.inInterpreter()) {
                            backJumpCounter++;
                        }
                    }
                    pc = successor;
                    continue bytecode_loop;
                }
            } else if (node instanceof UnconditionalJumpNode) {
                final int successor = ((UnconditionalJumpNode) node).getJumpSuccessor();
                if (successor <= pc) {
                    if (CompilerDirectives.inInterpreter()) {
                        backJumpCounter++;
                    }
                }
                pc = successor;
                continue bytecode_loop;
            } else if (node instanceof AbstractReturnNode) {
                if (isLoggingEnabled) {
                    LOG.finest(() -> stackFor(frame, frameString));
                }
                returnValue = ((AbstractReturnNode) node).executeReturn(frame);
                if (isLoggingEnabled) {
                    LOG.finer("Exited normally " + frameString + ", at pc " + node.getIndex());
                }
                pc = LOCAL_RETURN_PC;
                continue bytecode_loop;
            } else if (node instanceof PushClosureNode) {
                if (isLoggingEnabled) {
                    LOG.finest(() -> stackFor(frame, frameString));
                    LOG.finest(() -> node.toString() + ", at pc " + node.getIndex());
                }
                final PushClosureNode pushClosureNode = (PushClosureNode) node;
                pushClosureNode.executePush(frame);
                pc = pushClosureNode.getClosureSuccessorIndex();
                continue bytecode_loop;
            } else {
                if (isLoggingEnabled) {
                    LOG.finest(() -> stackFor(frame, frameString));
                    if (node instanceof AbstractSendNode) {
                        final String selector = ((NativeObject) ((AbstractSendNode) node).getSelector()).asStringUnsafe();
                        switch (selector) {
                            case "value":
                            case "value:":
                            case "value:value:":
                            case "value:value:value:":
                            case "value:value:value:value:":
                            case "value:value:value:value:value:":
                            case "valueWithArguments:":
                                LOG.finer("send: " + selector);
                                break;
                            default:
                                LOG.finest(() -> node.toString() + ", at pc " + node.getIndex());
                        }
                    } else if (node instanceof PushLiteralVariableNode || node instanceof PushLiteralConstantNode) {
                        LOG.finer(() -> node.toString() + ", at pc " + node.getIndex());
                    } else {
                        LOG.finest(() -> node.toString() + ", at pc " + node.getIndex());
                    }
                }
                pc = node.getSuccessorIndex();
                if (node instanceof AbstractSendNode) {
                    FrameAccess.setInstructionPointer(frame, code, pc);
                }
                node.executeVoid(frame);
                continue bytecode_loop;
            }
        }
        if (isLoggingEnabled) {
            LOG.finest(() -> stackFor(frame, frameString));
        }
        assert returnValue != null && !hasModifiedSender(frame);
        FrameAccess.terminate(frame, code);
        assert backJumpCounter >= 0;
        LoopNode.reportLoopCount(this, backJumpCounter);
        return returnValue;
    }

    private String stackFor(final VirtualFrame frame, final String frameString) {
        final int numArgsAndCopied = code.getNumArgsAndCopied();
        final StringBuilder b = new StringBuilder("\tStack of");
        b.append(frameString);
        b.append("\n");
        for (int i = 0; i < numArgsAndCopied; i++) {
            final FrameSlot slot = code.getStackSlot(i);
            final Object value = frame.getValue(slot);
            b.append("t");
            b.append(i);
            b.append("\t");
            b.append(value);
            b.append("\n");
        }
        if (numArgsAndCopied > 0) {
            b.append("------------------------------------------------\n");
        }
        final int p = FrameAccess.getStackPointer(frame, code) - 1;
        final int numStackSlots = code.getNumStackSlots();
        int j = numArgsAndCopied;
        for (int i = numArgsAndCopied; i < numStackSlots; i++) {
            final FrameSlot slot = code.getStackSlot(i);
            final Object value = frame.getValue(slot);
            if (value != null) {
                b.append(p == i ? "->\t" : "\t\t");
                b.append(value);
                b.append("\n");
            } else {
                j = i;
                break; // This and all following slots are not in use.
            }
        }
        return b.toString();
    }

    private HandlePrimitiveFailedNode getHandlePrimitiveFailedNode() {
        if (handlePrimitiveFailedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handlePrimitiveFailedNode = insert(HandlePrimitiveFailedNode.create(code));
        }
        return handlePrimitiveFailedNode;
    }

    /*
     * Non-optimized version of startBytecode which is used to resume contexts.
     */
    private Object resumeBytecode(final VirtualFrame frame, final long initialPC) {
        assert initialPC > 0 : "Trying to resume a fresh/terminated/illegal context";
        final String frameString;
        if (isLoggingEnabled) {
            frameString = " frame @" + Integer.toHexString(frame.hashCode()) + ", " + toString;
            LOG.finer(() -> "Resuming frame: " + frameString + " at pc " + initialPC);
        } else {
            frameString = toString;
        }
        int pc = (int) initialPC;
        Object returnValue = null;
        bytecode_loop_slow: while (pc != LOCAL_RETURN_PC) {
            final AbstractBytecodeNode node = fetchNextBytecodeNode(pc);
            if (node instanceof ConditionalJumpNode) {
                final ConditionalJumpNode jumpNode = (ConditionalJumpNode) node;
                if (jumpNode.executeCondition(frame)) {
                    final int successor = jumpNode.getSuccessorIndex();
                    pc = successor;
                    continue bytecode_loop_slow;
                } else {
                    final int successor = jumpNode.getSuccessorIndex();
                    pc = successor;
                    continue bytecode_loop_slow;
                }
            } else if (node instanceof UnconditionalJumpNode) {
                final int successor = ((UnconditionalJumpNode) node).getJumpSuccessor();
                pc = successor;
                continue bytecode_loop_slow;
            } else if (node instanceof AbstractReturnNode) {
                if (isLoggingEnabled) {
                    LOG.finest(() -> stackFor(frame, frameString));
                }
                returnValue = ((AbstractReturnNode) node).executeReturn(frame);
                if (isLoggingEnabled) {
                    LOG.finer("Exited normally " + frameString + ", at pc " + node.getIndex());
                }
                pc = LOCAL_RETURN_PC;
                continue bytecode_loop_slow;
            } else if (node instanceof PushClosureNode) {
                if (isLoggingEnabled) {
                    LOG.finest(() -> stackFor(frame, frameString));
                    LOG.finest(() -> node.toString() + ", at pc " + node.getIndex());
                }
                final PushClosureNode pushClosureNode = (PushClosureNode) node;
                pushClosureNode.executePush(frame);
                pc = pushClosureNode.getClosureSuccessorIndex();
                continue bytecode_loop_slow;
            } else {
                if (isLoggingEnabled) {
                    LOG.finest(() -> stackFor(frame, frameString));
                    if (node instanceof AbstractSendNode) {
                        final String selector = ((NativeObject) ((AbstractSendNode) node).getSelector()).asStringUnsafe();
                        switch (selector) {
                            case "value":
                            case "value:":
                            case "value:value:":
                            case "value:value:value:":
                            case "value:value:value:value:":
                            case "value:value:value:value:value:":
                            case "valueWithArguments:":
                                LOG.finer("send: " + selector);
                                break;
                            default:
                                LOG.finest(() -> node.toString() + ", at pc " + node.getIndex());
                        }
                    } else if (node instanceof PushLiteralVariableNode || node instanceof PushLiteralConstantNode) {
                        LOG.finer(() -> node.toString() + ", at pc " + node.getIndex());
                    } else {
                        LOG.finest(() -> node.toString() + ", at pc " + node.getIndex());
                    }
                }
                pc = node.getSuccessorIndex();
                if (node instanceof AbstractSendNode) {
                    FrameAccess.setInstructionPointer(frame, code, pc);
                }
                node.executeVoid(frame);
                continue bytecode_loop_slow;
            }
        }
        if (isLoggingEnabled) {
            LOG.finest(() -> stackFor(frame, frameString));
        }
        assert returnValue != null && !hasModifiedSender(frame);
        FrameAccess.terminate(frame, code);
        return returnValue;
    }

    /*
     * Fetch next bytecode and insert AST nodes on demand if enabled.
     */
    @SuppressWarnings("unused")
    private AbstractBytecodeNode fetchNextBytecodeNode(final int pc) {
        if (DECODE_BYTECODE_ON_DEMAND && bytecodeNodes[pc] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            bytecodeNodes[pc] = insert(SqueakBytecodeDecoder.decodeBytecode(code, pc));
            notifyInserted(bytecodeNodes[pc]);
        }
        return bytecodeNodes[pc];
    }

    /* Only use stackDepthProtection in interpreter or once per compilation unit (if at all). */
    private boolean enableStackDepthProtection() {
        return code.image.options.enableStackDepthProtection && (CompilerDirectives.inInterpreter() || CompilerDirectives.inCompilationRoot());
    }

    @Override
    public final boolean isInstrumentable() {
        return true;
    }

    @Override
    public final WrapperNode createWrapper(final ProbeNode probe) {
        return new ExecuteContextNodeWrapper(this, this, probe);
    }

    @Override
    public final boolean hasTag(final Class<? extends Tag> tag) {
        return StandardTags.RootTag.class == tag;
    }

    @Override
    public String getDescription() {
        return code.toString();
    }

    @Override
    public SourceSection getSourceSection() {
        if (section == null) {
            if (code.image.isTesting()) {
                // Cannot provide source section in case of AbstractSqueakTestCaseWithDummyImage.
                return null;
            }
            final Source source = code.getSource();
            section = source.createSection(1, 1, source.getLength());
        }
        return section;
    }

    // FIXME: Trigger interrupt check on sends and in loops.
    @NodeInfo(cost = NodeCost.NONE)
    protected abstract static class TriggerInterruptHandlerNode extends AbstractNodeWithCode {
        protected static final int BYTECODE_LENGTH_THRESHOLD = 32;

        protected TriggerInterruptHandlerNode(final CompiledCodeObject code) {
            super(code);
        }

        @SuppressWarnings("unused")
        private static TriggerInterruptHandlerNode create(final CompiledCodeObject code) {
            return TriggerInterruptHandlerNodeGen.create(code);
        }

        protected abstract void executeGeneric(VirtualFrame frame);

        @SuppressWarnings("unused")
        @Specialization(guards = {"shouldTrigger(frame)"})
        protected static final void doTrigger(final VirtualFrame frame, @Cached("create(code)") final InterruptHandlerNode interruptNode) {
            interruptNode.executeTrigger(frame);
        }

        @SuppressWarnings("unused")
        @Fallback
        protected final void doNothing() {
            // Do not trigger.
        }

        protected final boolean shouldTrigger(@SuppressWarnings("unused") final VirtualFrame frame) {
            if (CompilerDirectives.inCompiledCode() && !CompilerDirectives.inCompilationRoot()) {
                return false; // never trigger in inlined code
            }
            return code.image.interrupt.isActiveAndShouldTrigger();
        }
    }

    private HandleNonLocalReturnNode getHandleNonLocalReturnNode() {
        if (handleNonLocalReturnNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handleNonLocalReturnNode = insert(HandleNonLocalReturnNode.create(code));
        }
        return handleNonLocalReturnNode;
    }
}
