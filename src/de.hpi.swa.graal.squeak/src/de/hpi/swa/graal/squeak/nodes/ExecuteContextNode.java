/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes;

import static de.hpi.swa.graal.squeak.util.LogUtils.CONTEXT_STACK_TRACE;
import static de.hpi.swa.graal.squeak.util.LogUtils.PRIMITIVES;
import static de.hpi.swa.graal.squeak.util.LogUtils.SCHEDULING;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.exceptions.ProcessSwitch;
import de.hpi.swa.graal.squeak.exceptions.Returns.NonLocalReturn;
import de.hpi.swa.graal.squeak.exceptions.Returns.NonVirtualReturn;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
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
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.DebugUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.InterruptHandlerNode;
import de.hpi.swa.graal.squeak.util.SqueakBytecodeDecoder;

@GenerateWrapper
public class ExecuteContextNode extends AbstractNodeWithCode implements InstrumentableNode {
    private static final boolean DECODE_BYTECODE_ON_DEMAND = true;
    private static final int STACK_DEPTH_LIMIT = 25000;
    private static final int LOCAL_RETURN_PC = -2;
    private static final int MIN_NUMBER_OF_BYTECODE_FOR_INTERRUPT_CHECKS = 32;

    @Children private AbstractBytecodeNode[] bytecodeNodes;
    @Child private HandleNonLocalReturnNode handleNonLocalReturnNode;
    @Child private GetOrCreateContextNode getOrCreateContextNode;

    @Child private FrameStackInitializationNode frameInitializationNode;
    @Child private HandlePrimitiveFailedNode handlePrimitiveFailedNode;
    @Child private InterruptHandlerNode interruptHandlerNode;
    @Child private MaterializeContextOnMethodExitNode materializeContextOnMethodExitNode;

    private SourceSection section;
    private final String toString;

    protected ExecuteContextNode(final CompiledCodeObject code, final boolean resume) {
        super(code);
        if (DECODE_BYTECODE_ON_DEMAND) {
            bytecodeNodes = new AbstractBytecodeNode[SqueakBytecodeDecoder.trailerPosition(code)];
        } else {
            bytecodeNodes = SqueakBytecodeDecoder.decode(code);
        }
        frameInitializationNode = resume ? null : FrameStackInitializationNode.create(code);
        /*
         * Only check for interrupts if method is relatively large. Also, skip timer interrupts here
         * as they trigger too often, which causes a lot of context switches and therefore
         * materialization and deopts. Timer inputs are currently handled in
         * primitiveRelinquishProcessor (#230) only.
         */
        interruptHandlerNode = bytecodeNodes.length >= MIN_NUMBER_OF_BYTECODE_FOR_INTERRUPT_CHECKS ? InterruptHandlerNode.create(code, false) : null;
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
                final ContextObject context = getGetOrCreateContextNode().executeGet(frame, NilObject.SINGLETON);
                throw ProcessSwitch.createWithBoundary(context, context, context.getProcess());
            }
            frameInitializationNode.executeInitialize(frame);
            if (interruptHandlerNode != null) {
                interruptHandlerNode.executeTrigger(frame);
            }
            return startBytecode(frame);
        } catch (final NonLocalReturn nlr) {
            SCHEDULING.finer("Exited context " + toString + " through a non-local return");
            /** {@link getHandleNonLocalReturnNode()} acts as {@link BranchProfile} */
            return getHandleNonLocalReturnNode().executeHandle(frame, nlr);
        } catch (final NonVirtualReturn nvr) {
            SCHEDULING.finer("Exited context " + toString + " through a non-virtual return");
            /** {@link getGetOrCreateContextNode()} acts as {@link BranchProfile} */
            getGetOrCreateContextNode().executeGet(frame, (PointersObject) null).markEscaped();
            throw nvr;
        } catch (final ProcessSwitch ps) {
            SCHEDULING.finer("Exited context " + toString + " through a process switch");
            /** {@link getGetOrCreateContextNode()} acts as {@link BranchProfile} */
            getGetOrCreateContextNode().executeGet(frame, ps.getOldProcess()).markEscaped();
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
            SCHEDULING.finer("Exited context " + toString + " through a non-local return");
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
            SCHEDULING.finer("Exited context " + toString + " through a non-local return");
            /** {@link getHandleNonLocalReturnNode()} acts as {@link BranchProfile} */
            return getHandleNonLocalReturnNode().executeHandle(frame, nlr);
        } finally {
            code.image.lastSeenContext = null; // Stop materialization here.
        }
    }

    private GetOrCreateContextNode getGetOrCreateContextNode() {
        if (getOrCreateContextNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getOrCreateContextNode = insert(GetOrCreateContextNode.create(code));
        }
        return getOrCreateContextNode;
    }

    /*
     * Inspired by Sulong's LLVMDispatchBasicBlockNode (https://git.io/fjEDw).
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    private Object startBytecode(final VirtualFrame frame) {
        CompilerAsserts.compilationConstant(bytecodeNodes.length);
        final String frameString = toString;
        SCHEDULING.finer(() -> "Entering fresh context for " + frameString);
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
                        SCHEDULING.finer("Primitive return from " + frameString);
                        return callPrimitiveNode.primitiveNode.executePrimitive(frame);
                    } catch (final PrimitiveFailed e) {
                        getHandlePrimitiveFailedNode().executeHandle(frame, e.getReasonCode());
                        /*
                         * Same toString() methods may throw compilation warnings, this is expected
                         * and ok for primitive failure logging purposes.
                         */
                        PRIMITIVES.fine(() -> callPrimitiveNode.primitiveNode.getClass().getSimpleName() + " failed (arguments: " +
                                        ArrayUtils.toJoinedString(", ", FrameAccess.getReceiverAndArguments(frame)) + ")");
                        /* continue with fallback code. */
                    }
                }
                pc = callPrimitiveNode.getSuccessorIndex();
                assert pc == CallPrimitiveNode.NUM_BYTECODES;
                continue;
            } else if (node instanceof AbstractSendNode) {
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code) +
                                "\n" + node.getIndex() + " " + node);
                SCHEDULING.finer(() -> {
                    final String selector = ((NativeObject) ((AbstractSendNode) node).getSelector()).asStringUnsafe();
                    switch (selector) {
                        case "value":
                        case "value:":
                        case "value:value:":
                        case "value:value:value:":
                        case "value:value:value:value:":
                        case "value:value:value:value:value:":
                        case "valueWithArguments:":
                            return "send: " + selector;
                    }
                    return null;
                });
                pc = node.getSuccessorIndex();
                FrameAccess.setInstructionPointer(frame, code, pc);
                node.executeVoid(frame);
                final int actualNextPc = FrameAccess.getInstructionPointer(frame, code);
                if (pc != actualNextPc) {
                    /*
                     * pc has changed, which can happen if a context is restarted (e.g. as part of
                     * Exception>>retry). For now, we continue in the interpreter to avoid confusing
                     * the Graal compiler.
                     */
                    CompilerDirectives.transferToInterpreter();
                    pc = actualNextPc;
                }
                continue bytecode_loop;
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
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code));
                returnValue = ((AbstractReturnNode) node).executeReturn(frame);
                SCHEDULING.finer("Exited context for " + frameString + " normally, at pc " + node.getIndex());
                pc = LOCAL_RETURN_PC;
                continue bytecode_loop;
            } else if (node instanceof PushClosureNode) {
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code) +
                                "\n" + node.getIndex() + " " + node);
                final PushClosureNode pushClosureNode = (PushClosureNode) node;
                pushClosureNode.executePush(frame);
                pc = pushClosureNode.getClosureSuccessorIndex();
                continue bytecode_loop;
            } else {
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code));
                if (node instanceof PushLiteralVariableNode || node instanceof PushLiteralConstantNode) {
                    SCHEDULING.finer(() -> node.getIndex() + " " + node);
                } else {
                    CONTEXT_STACK_TRACE.finest(() -> node.getIndex() + " " + node);
                }
                node.executeVoid(frame);
                pc = node.getSuccessorIndex();
                continue bytecode_loop;
            }
        }
        assert returnValue != null && !hasModifiedSender(frame);
        FrameAccess.terminate(frame, code);
        assert backJumpCounter >= 0;
        LoopNode.reportLoopCount(this, backJumpCounter);
        return returnValue;
    }

    private HandlePrimitiveFailedNode getHandlePrimitiveFailedNode() {
        if (handlePrimitiveFailedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handlePrimitiveFailedNode = insert(HandlePrimitiveFailedNode.create(code));
        }
        return handlePrimitiveFailedNode;
    }

    /*
     * Non-optimized version of startBytecode used to resume contexts.
     */
    private Object resumeBytecode(final VirtualFrame frame, final long initialPC) {
        assert initialPC > 0 : "Trying to resume a fresh/terminated/illegal context";
        final String frameString = toString;
        SCHEDULING.finer(() -> "Entering resumed context for " + frameString + " at pc " + initialPC);
        int pc = (int) initialPC;
        Object returnValue = null;
        bytecode_loop_slow: while (pc != LOCAL_RETURN_PC) {
            final AbstractBytecodeNode node = fetchNextBytecodeNode(pc);
            if (node instanceof AbstractSendNode) {
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code) +
                                "\n" + node.getIndex() + " " + node);
                SCHEDULING.finer(() -> {
                    final String selector = ((NativeObject) ((AbstractSendNode) node).getSelector()).asStringUnsafe();
                    switch (selector) {
                        case "value":
                        case "value:":
                        case "value:value:":
                        case "value:value:value:":
                        case "value:value:value:value:":
                        case "value:value:value:value:value:":
                        case "valueWithArguments:":
                            return "send: " + selector;
                    }
                    return null;
                });
                pc = node.getSuccessorIndex();
                FrameAccess.setInstructionPointer(frame, code, pc);
                node.executeVoid(frame);
                final int actualNextPc = FrameAccess.getInstructionPointer(frame, code);
                if (pc != actualNextPc) {
                    /*
                     * pc has changed, which can happen if a context is restarted (e.g. as part of
                     * Exception>>retry). For now, we continue in the interpreter to avoid confusing
                     * the Graal compiler.
                     */
                    CompilerDirectives.transferToInterpreter();
                    pc = actualNextPc;
                }
                continue bytecode_loop_slow;
            } else if (node instanceof ConditionalJumpNode) {
                final ConditionalJumpNode jumpNode = (ConditionalJumpNode) node;
                if (jumpNode.executeCondition(frame)) {
                    final int successor = jumpNode.getJumpSuccessorIndex();
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
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code));
                returnValue = ((AbstractReturnNode) node).executeReturn(frame);
                SCHEDULING.finer("Exited context for " + frameString + " normally, at pc " + node.getIndex());
                pc = LOCAL_RETURN_PC;
                continue bytecode_loop_slow;
            } else if (node instanceof PushClosureNode) {
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code) +
                                "\n" + node.getIndex() + " " + node);
                final PushClosureNode pushClosureNode = (PushClosureNode) node;
                pushClosureNode.executePush(frame);
                pc = pushClosureNode.getClosureSuccessorIndex();
                continue bytecode_loop_slow;
            } else {
                CONTEXT_STACK_TRACE.finest(() -> "...within " + frameString + DebugUtils.stackFor(frame, code));
                if (node instanceof PushLiteralVariableNode || node instanceof PushLiteralConstantNode) {
                    SCHEDULING.finer(() -> node.getIndex() + " " + node);
                } else {
                    CONTEXT_STACK_TRACE.finest(() -> node.getIndex() + " " + node);
                }
                node.executeVoid(frame);
                pc = node.getSuccessorIndex();
                continue bytecode_loop_slow;
            }
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
            final Source source = code.getSource();
            section = source.createSection(1, 1, source.getLength());
        }
        return section;
    }

    private HandleNonLocalReturnNode getHandleNonLocalReturnNode() {
        if (handleNonLocalReturnNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handleNonLocalReturnNode = insert(HandleNonLocalReturnNode.create(code));
        }
        return handleNonLocalReturnNode;
    }
}
