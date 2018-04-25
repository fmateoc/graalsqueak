package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;

import de.hpi.swa.graal.squeak.exceptions.Returns.FreshReturn;
import de.hpi.swa.graal.squeak.exceptions.Returns.LocalReturn;
import de.hpi.swa.graal.squeak.exceptions.Returns.NonLocalReturn;
import de.hpi.swa.graal.squeak.exceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.nodes.bytecodes.AbstractBytecodeNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.JumpBytecodes.ConditionalJumpNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.JumpBytecodes.UnconditionalJumpNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameSlotReadNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameSlotWriteNode;
import de.hpi.swa.graal.squeak.nodes.context.stack.StackPushNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.SqueakBytecodeDecoder;

public class ExecuteContextNode extends AbstractNodeWithCode {
    @Children private AbstractBytecodeNode[] bytecodeNodes;
    @Child private HandleLocalReturnNode handleLocalReturnNode;
    @Child private HandleNonLocalReturnNode handleNonLocalReturnNode;
    @Child private HandleNonVirtualReturnNode handleNonVirtualReturnNode;
    @Child private FrameSlotReadNode contextReadNode;
    @Child private FrameSlotWriteNode instructionPointerWriteNode;
    @Child private StackPushNode pushStackNode;

    public static ExecuteContextNode create(final CompiledCodeObject code) {
        return new ExecuteContextNode(code);
    }

    protected ExecuteContextNode(final CompiledCodeObject code) {
        super(code);
        bytecodeNodes = new SqueakBytecodeDecoder(code).decode();
        CompilerAsserts.compilationConstant(bytecodeNodes.length);
        handleLocalReturnNode = HandleLocalReturnNode.create(code);
        handleNonLocalReturnNode = HandleNonLocalReturnNode.create(code);
        handleNonVirtualReturnNode = HandleNonVirtualReturnNode.create(code);
        contextReadNode = FrameSlotReadNode.create(code.thisContextOrMarkerSlot);
        instructionPointerWriteNode = FrameSlotWriteNode.create(code.instructionPointerSlot);
        pushStackNode = StackPushNode.create(code);
    }

    public Object executeVirtualized(final VirtualFrame frame) {
        code.image.interrupt.sendOrBackwardJumpTrigger(frame);
        try {
            startBytecode(frame);
            throw new SqueakException("Method did not return");
        } catch (LocalReturn lr) {
            return handleLocalReturnNode.executeHandle(frame, lr);
        } catch (NonLocalReturn nlr) {
            return handleNonLocalReturnNode.executeHandle(frame, nlr);
            // TODO: use handleNonVirtualReturnNode again
            // } catch (NonVirtualReturn nvr) {
            // return handleNonVirtualReturnNode.executeHandle(frame, nvr);
        }
    }

    public Object executeNonVirtualized(final VirtualFrame frame, final ContextObject newContext) {
        // maybe persist newContext, so there's no need to lookup the context to update its pc.
        assert newContext.getCodeObject() == FrameAccess.getMethod(frame);
        code.image.interrupt.sendOrBackwardJumpTrigger(frame);
        try {
            final long initialPC = newContext.getInstructionPointer();
            if (initialPC == 0) {
                startBytecode(frame);
            } else {
                // avoid optimizing the cases in which a context is resumed
                CompilerDirectives.transferToInterpreter();
                resumeBytecode(frame, initialPC);
            }
            throw new SqueakException("Method did not return");
        } catch (LocalReturn lr) {
            return handleLocalReturnNode.executeHandle(frame, lr);
        } catch (NonLocalReturn nlr) {
            return handleNonLocalReturnNode.executeHandle(frame, nlr);
            // TODO: use handleNonVirtualReturnNode again
            // } catch (NonVirtualReturn nvr) {
            // return handleNonVirtualReturnNode.executeHandle(frame, nvr);
        }
    }

    /*
     * Inspired by Sulong's LLVMDispatchBasicBlockNode (https://goo.gl/4LMzfX).
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    protected void startBytecode(final VirtualFrame frame) {
        int pc = 0;
        int backJumpCounter = 0;
        AbstractBytecodeNode node = bytecodeNodes[pc];
        try {
            while (pc >= 0) {
                CompilerAsserts.partialEvaluationConstant(pc);
                storeInstructionPointer(frame, node);
                if (node instanceof ConditionalJumpNode) {
                    final ConditionalJumpNode jumpNode = (ConditionalJumpNode) node;
                    final boolean condition = jumpNode.executeCondition(frame);
                    if (CompilerDirectives.injectBranchProbability(jumpNode.getBranchProbability(ConditionalJumpNode.TRUE_SUCCESSOR), condition)) {
                        final int successor = jumpNode.getJumpSuccessor();
                        if (CompilerDirectives.inInterpreter()) {
                            jumpNode.increaseBranchProbability(ConditionalJumpNode.TRUE_SUCCESSOR);
                            if (successor <= pc) {
                                backJumpCounter++;
                            }
                        }
                        pc = successor;
                        node = bytecodeNodes[pc];
                        continue;
                    } else {
                        final int successor = jumpNode.getSuccessorIndex();
                        if (CompilerDirectives.inInterpreter()) {
                            jumpNode.increaseBranchProbability(ConditionalJumpNode.FALSE_SUCCESSOR);
                            if (successor <= pc) {
                                backJumpCounter++;
                            }
                        }
                        pc = successor;
                        node = bytecodeNodes[pc];
                        continue;
                    }
                } else if (node instanceof UnconditionalJumpNode) {
                    final int successor = ((UnconditionalJumpNode) node).getJumpSuccessor();
                    if (CompilerDirectives.inInterpreter() && successor <= pc) {
                        backJumpCounter++;
                    }
                    pc = successor;
                    node = bytecodeNodes[pc];
                    continue;
                } else {
                    try {
                        pc = node.executeInt(frame);
                    } catch (FreshReturn fr) {
                        throw fr.getReturnException();
                    } catch (LocalReturn lr) {
                        pushStackNode.executeWrite(frame, lr.getReturnValue());
                    } catch (NonLocalReturn nlr) {
                        if (nlr.hasArrivedAtTargetContext()) {
                            pushStackNode.executeWrite(frame, nlr.getReturnValue());
                            pc = node.getSuccessorIndex();
                        } else {
                            throw nlr;
                        }
                    }
                    node = bytecodeNodes[pc];
                    continue;
                }
            }
        } finally {
            assert backJumpCounter >= 0;
            LoopNode.reportLoopCount(this, backJumpCounter);
        }
    }

    /*
     * Non-optimized version of startBytecode that is used to resume contexts.
     */
    protected void resumeBytecode(final VirtualFrame frame, final long initialPC) {
        int pc = (int) initialPC;
        AbstractBytecodeNode node = bytecodeNodes[pc];
        while (pc >= 0) {
            storeInstructionPointer(frame, node);
            try {
                pc = node.executeInt(frame);
            } catch (FreshReturn fr) {
                throw fr.getReturnException();
            } catch (LocalReturn lr) {
                pushStackNode.executeWrite(frame, lr.getReturnValue());
            } catch (NonLocalReturn nlr) {
                if (nlr.hasArrivedAtTargetContext()) {
                    pushStackNode.executeWrite(frame, nlr.getReturnValue());
                    pc = node.getSuccessorIndex();
                } else {
                    throw nlr;
                }
            }
            node = bytecodeNodes[pc];
        }
    }

    private void storeInstructionPointer(final VirtualFrame frame, final AbstractBytecodeNode node) {
        final Object contextOrMarker = contextReadNode.executeRead(frame);
        if (contextOrMarker instanceof ContextObject) {
            ((ContextObject) contextOrMarker).setInstructionPointer(node.getSuccessorIndex());
        } else {
            instructionPointerWriteNode.executeWrite(frame, (long) node.getSuccessorIndex());
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return code.toString();
    }
}