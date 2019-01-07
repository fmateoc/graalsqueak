package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.graal.squeak.exceptions.InstructionPointerModification;
import de.hpi.swa.graal.squeak.exceptions.ProcessSwitch;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.MiscUtils;

public final class ContextObject extends AbstractPointersObject {
    private MaterializedFrame truffleFrame;
    private FrameMarker frameMarker;
    private int size;
    private boolean hasModifiedSender = false;
    public boolean escaped = false;

    public static ContextObject createWithHash(final SqueakImageContext image, final long hash) {
        return new ContextObject(image, hash);
    }

    private ContextObject(final SqueakImageContext image, final long hash) {
        super(image, hash, image.methodContextClass);
        truffleFrame = null;
        frameMarker = null;
    }

    public static ContextObject create(final SqueakImageContext image, final int size) {
        return new ContextObject(image, size);
    }

    private ContextObject(final SqueakImageContext image, final int size) {
        super(image, image.methodContextClass);
        truffleFrame = null;
        frameMarker = null;
        this.size = size;
    }

    public static ContextObject create(final SqueakImageContext image, final int size, final MaterializedFrame frame, final FrameMarker frameMarker) {
        return new ContextObject(image, size, frame, frameMarker);
    }

    private ContextObject(final SqueakImageContext image, final int size, final MaterializedFrame frame, final FrameMarker frameMarker) {
        super(image, image.methodContextClass);
        truffleFrame = frame;
        this.frameMarker = frameMarker;
        this.size = size;
    }

    public ContextObject(final ContextObject original) {
        super(original.image, original.image.methodContextClass);
        truffleFrame = original.truffleFrame;
        frameMarker = original.frameMarker;
        hasModifiedSender = original.hasModifiedSender;
        escaped = original.escaped;
    }

    public void fillIn(final Object[] pointers) {
        assert pointers.length > CONTEXT.TEMP_FRAME_START;
        final CompiledMethodObject method = (CompiledMethodObject) pointers[CONTEXT.METHOD];
        final Object sender = pointers[CONTEXT.SENDER_OR_NIL];
        assert sender != null : "sender should not be null";
        final BlockClosureObject closure = pointers[CONTEXT.CLOSURE_OR_NIL] == image.nil ? null : (BlockClosureObject) pointers[CONTEXT.CLOSURE_OR_NIL];
        final int endArguments = CONTEXT.RECEIVER + 1 + method.getNumArgsAndCopied();
        final Object[] arguments = Arrays.copyOfRange(pointers, CONTEXT.RECEIVER, endArguments);
        final Object[] frameArguments = FrameAccess.newWith(method, sender, closure, arguments);
        final FrameDescriptor frameDescriptor;
        if (closure != null) {
            if (!closure.hasHomeContext() || !((ContextObject) closure.getOuterContext()).hasTruffleFrame()) {
                // FIXME: this is hacky and slow (2nd loop needed in reader).
                return; // block closure not ready try again later
            }
            frameDescriptor = closure.getCompiledBlock().getFrameDescriptor();
        } else {
            frameDescriptor = method.getFrameDescriptor();
        }
        truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, frameDescriptor);
        truffleFrame.setObject(method.thisContextOrMarkerSlot, this);
        final int initalPC = method.getInitialPC();
        truffleFrame.setInt(method.instructionPointerSlot, pointers[CONTEXT.INSTRUCTION_POINTER] == image.nil ? -1 : (int) (long) pointers[CONTEXT.INSTRUCTION_POINTER] - initalPC);
        truffleFrame.setInt(method.stackPointerSlot, (int) (long) pointers[CONTEXT.STACKPOINTER]);
        for (int i = CONTEXT.TEMP_FRAME_START; i < pointers.length; i++) {
            final Object pointer = pointers[i];
            // if (pointer != image.nil) {
            // TODO: do better than FrameSlotKind.Object
            final FrameSlot frameSlot = method.getStackSlot(i - CONTEXT.TEMP_FRAME_START);
            truffleFrame.getFrameDescriptor().setFrameSlotKind(frameSlot, FrameSlotKind.Object);
            truffleFrame.setObject(frameSlot, pointer);
        }
    }

    public boolean isMatchingFrame(final VirtualFrame frame) {
        final Object contextOrMarker = FrameAccess.getContextOrMarker(frame);
        return this == contextOrMarker || frameMarker == contextOrMarker;
    }

    public void terminate() {
        // remove pc and sender without flagging as dirty
        atput0(CONTEXT.INSTRUCTION_POINTER, image.nil);
        atput0(CONTEXT.SENDER_OR_NIL, image.nil);
    }

    public boolean isTerminated() {
        return at0(CONTEXT.INSTRUCTION_POINTER) == image.nil &&
                        at0(CONTEXT.SENDER_OR_NIL) == image.nil;
    }

    public Object at0(final long longIndex) {
        assert longIndex >= 0;
        final int index = (int) longIndex;
        switch (index) {
            case CONTEXT.SENDER_OR_NIL:
                return getSender();
            case CONTEXT.INSTRUCTION_POINTER:
                final int pc = getFramePC();
                if (pc < 0) {
                    return image.nil;
                } else {
                    final BlockClosureObject closure = getClosure();
                    final int initalPC;
                    if (closure != null) {
                        initalPC = closure.getCompiledBlock().getInitialPC();
                    } else {
                        initalPC = getMethod().getInitialPC();
                    }
                    return (long) (initalPC + pc);
                }
            case CONTEXT.STACKPOINTER:
                return (long) FrameUtil.getIntSafe(truffleFrame, getMethod().stackPointerSlot);
            case CONTEXT.METHOD:
                return getMethod();
            case CONTEXT.CLOSURE_OR_NIL:
                final BlockClosureObject closure = FrameAccess.getClosure(truffleFrame);
                return closure == null ? image.nil : closure;
            case CONTEXT.RECEIVER:
                return FrameAccess.getReceiver(truffleFrame);
            default:
                final int stackIndex = index - CONTEXT.TEMP_FRAME_START;
                final CompiledCodeObject code = getMethod();
                if (stackIndex >= code.getNumStackSlots()) {
                    return image.nil; // TODO: make this better.
                } else {
                    return truffleFrame.getValue(code.getStackSlot(stackIndex));
                }
        }
    }

    public void atput0(final long longIndex, final Object value) {
        assert longIndex >= 0 && value != null;
        final int index = (int) longIndex;
        if (index == CONTEXT.SENDER_OR_NIL) {
            assert !(value instanceof FrameMarker) : "sender should not be a marker here anymore";
            image.printVerbose("Sender of", this, " set to", value);
            hasModifiedSender = true;
        }
        assert value != null : "null indicates a problem";
        switch (index) {
            case CONTEXT.SENDER_OR_NIL:
                getOrCreateTruffleFrame().getArguments()[FrameAccess.SENDER_OR_SENDER_MARKER] = value;
                break;
            case CONTEXT.INSTRUCTION_POINTER:
                final ContextObject[] lastSender = new ContextObject[]{null};
                final boolean isActiveOnTruffleStack = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Boolean>() {
                    public Boolean visitFrame(final FrameInstance frameInstance) {
                        final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                        if (!FrameAccess.isGraalSqueakFrame(current)) {
                            return null;
                        }
                        final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                        final Object sender = FrameAccess.getSender(current);
                        if (sender instanceof ContextObject) {
                            lastSender[0] = (ContextObject) sender;
                        } else {
                            lastSender[0] = null;
                        }
                        if (contextOrMarker == this || contextOrMarker == getFrameMarker()) {
                            return true;
                        }
                        return null;
                    }
                }) != null;
                boolean isActiveOnSqueakStack = false;
                final boolean isActive;
                if (lastSender[0] != null && !isActiveOnTruffleStack) {
                    ContextObject current = lastSender[0];
                    while (true) {
                        if (current == this) {
                            isActiveOnSqueakStack = true;
                            break;
                        }
                        final AbstractSqueakObject sender = current.getSender();
                        if (sender.isNil()) {
                            break;
                        } else {
                            current = (ContextObject) sender;
                        }
                    }
                    isActive = isActiveOnSqueakStack == true;
                } else {
                    isActive = isActiveOnTruffleStack == true;
                }
                if (value == image.nil) {
                    assert !isActive : "Cannot terminate active context";
                    truffleFrame.setInt(FrameAccess.getMethod(truffleFrame).instructionPointerSlot, -1);
                } else {
                    final BlockClosureObject closure = getClosure();
                    final int initalPC;
                    if (closure != null) {
                        initalPC = closure.getCompiledBlock().getInitialPC();
                    } else {
                        initalPC = getMethod().getInitialPC();
                    }
                    final int newPC = (int) (long) value - initalPC;
                    getOrCreateTruffleFrame().setInt(FrameAccess.getMethod(truffleFrame).instructionPointerSlot, newPC);
                    if (isActiveOnTruffleStack) {
                        throw new SqueakException("Signal InstructionPointerModification?");
                        // FIXME: throw new InstructionPointerModification(frameMarker, newPC);
                    } else if (isActiveOnSqueakStack) {
                        throw new SqueakException("Switch to this context?");
                        // FIXME: throw new ProcessSwitch(this); ?
                    }

                }
                break;
            case CONTEXT.STACKPOINTER:
                getOrCreateTruffleFrame().setInt(FrameAccess.getMethod(truffleFrame).stackPointerSlot, (int) (long) value);
                break;
            case CONTEXT.METHOD:
                assert value instanceof CompiledMethodObject : "CompiledMethodObject expected";
                getOrCreateTruffleFrame((CompiledMethodObject) value).getArguments()[FrameAccess.METHOD] = value;
                break;
            case CONTEXT.CLOSURE_OR_NIL:
                assert value == image.nil || value instanceof BlockClosureObject;
                getOrCreateTruffleFrame().getArguments()[FrameAccess.CLOSURE_OR_NULL] = value == image.nil ? null : value;
                break;
            case CONTEXT.RECEIVER:
                getOrCreateTruffleFrame().getArguments()[FrameAccess.RECEIVER] = value;
                break;
            default:
                final int stackIndex = index - CONTEXT.TEMP_FRAME_START;
                final CompiledCodeObject code = FrameAccess.getMethod(truffleFrame);
                if (stackIndex >= code.getNumStackSlots()) {
                    throw new SqueakException("unexpected store"); // TODO: make this better.
                } else {
                    getOrCreateTruffleFrame().setObject(code.getStackSlot(stackIndex), value);
                }
        }
    }

    private MaterializedFrame getOrCreateTruffleFrame() {
        if (truffleFrame == null) {
            // Method is unknown, use dummy frame instead
            final int guessedArgumentSize = size > CONTEXT.LARGE_FRAMESIZE ? size - CONTEXT.LARGE_FRAMESIZE : size - CONTEXT.SMALL_FRAMESIZE;
            final Object[] dummyArguments = FrameAccess.newDummyWith(null, image.nil, null, new Object[guessedArgumentSize]);
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(dummyArguments);
        }
        return truffleFrame;
    }

    private MaterializedFrame getOrCreateTruffleFrame(final CompiledMethodObject method) {
        if (truffleFrame == null || FrameAccess.getMethod(truffleFrame) == null) {
            final Object[] frameArguments;
            final FrameDescriptor frameDescriptor;
            if (truffleFrame != null) {
                assert FrameAccess.getSender(truffleFrame) != null : "Sender should not be null";

                final Object[] dummyArguments = truffleFrame.getArguments();
                final int expectedArgumentSize = FrameAccess.ARGUMENTS_START + method.getNumArgsAndCopied();
                assert dummyArguments.length >= expectedArgumentSize : "Unexpected argument size, maybe dummy frame had wrong size?";
                if (dummyArguments.length > expectedArgumentSize) {
                    // Trim arguments.
                    frameArguments = Arrays.copyOfRange(dummyArguments, 0, expectedArgumentSize);
                } else {
                    frameArguments = truffleFrame.getArguments();
                }
                assert frameArguments[FrameAccess.RECEIVER] != null : "Receiver should probably not be null here";
                final BlockClosureObject closure = FrameAccess.getClosure(truffleFrame);
                frameDescriptor = closure != null ? closure.getCompiledBlock().getFrameDescriptor() : method.getFrameDescriptor();
            } else {
                // Receiver plus arguments.
                final Object[] squeakArguments = new Object[1 + method.getNumArgsAndCopied()];
                frameArguments = FrameAccess.newDummyWith(method, image.nil, null, squeakArguments);
                frameDescriptor = method.getFrameDescriptor();
            }
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, frameDescriptor);
            truffleFrame.setInt(method.instructionPointerSlot, -2);
            truffleFrame.setObject(method.thisContextOrMarkerSlot, this);
        }
        return truffleFrame;
    }

    public CompiledCodeObject getClosureOrMethod() {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            return closure.getCompiledBlock();
        } else {
            return getMethod();
        }
    }

    private boolean hasMethod() {
        return getMethod() != null;
    }

    public CompiledMethodObject getMethod() {
        return FrameAccess.getMethod(truffleFrame);
    }

    public AbstractSqueakObject shallowCopy() {
        return new ContextObject(this);
    }

    public boolean isDirty() {
        return false;
    }

    public boolean hasEscaped() {
        return escaped;
    }

    public void markEscaped() {
        this.escaped = true;
    }

    public boolean hasModifiedSender() {
        return hasModifiedSender;
    }

    public boolean hasVirtualSender() {
        return FrameAccess.getSender(truffleFrame) instanceof FrameMarker;
    }

    private int getFramePC() {
        return FrameUtil.getIntSafe(truffleFrame, getMethod().instructionPointerSlot);
    }

    private boolean truffleFrameMarkedAsTerminated() {
        return truffleFrame != null && getFramePC() < 0;
    }

    public AbstractSqueakObject getSender() {
        if (truffleFrame == null) {
            return null; // Signaling not initialized, FIXME
        }
        final AbstractSqueakObject actualSender;
        final Object senderOrMarker = FrameAccess.getSender(truffleFrame);
        if (senderOrMarker instanceof FrameMarker) {
            final Frame frame = FrameAccess.findFrameForMarker((FrameMarker) senderOrMarker);
            if (frame == null) {
                throw new SqueakException("Unable to find frame for marker:", senderOrMarker);
            }
            actualSender = getOrCreateContextFor(frame.materialize());
            assert actualSender != null;
            setSender(actualSender);
            return actualSender;
        } else {
            return (AbstractSqueakObject) senderOrMarker;
        }
    }

    public static ContextObject getOrCreateContextFor(final MaterializedFrame frame) {
        final Object contextOrMarker = FrameAccess.getContextOrMarker(frame);
        if (contextOrMarker instanceof FrameMarker) {
            final CompiledMethodObject method = FrameAccess.getMethod(frame);
            final ContextObject context = ContextObject.create(method.image, method.sqContextSize(), frame, (FrameMarker) contextOrMarker);
            frame.setObject(method.thisContextOrMarkerSlot, context);
            return context;
        } else {
            return (ContextObject) contextOrMarker;
        }
    }

    // should only be used when sender is not nil
    public ContextObject getNotNilSender() {
        return (ContextObject) getSender();
    }

    /*
     * Set sender without flagging context as dirty.
     */
    public void setSender(final Object sender) {
        assert !(sender instanceof FrameMarker) : "sender should not be a marker here anymore";
        atput0(CONTEXT.SENDER_OR_NIL, sender);
    }

    public void push(final Object value) {
        assert value != null;
        final long newSP = getStackPointer() + 1;
        assert newSP <= CONTEXT.MAX_STACK_SIZE;
        atStackPut(newSP, value);
        setStackPointer(newSP);
    }

    public long getInstructionPointer() {
        return (long) at0(CONTEXT.INSTRUCTION_POINTER);
    }

    public void setInstructionPointer(final long newPC) {
        assert newPC >= 0;
        atput0(CONTEXT.INSTRUCTION_POINTER, newPC);
    }

    public long getStackPointer() {
        return (long) at0(CONTEXT.STACKPOINTER);
    }

    public void setStackPointer(final long newSP) {
        assert 0 <= newSP && newSP <= CONTEXT.MAX_STACK_SIZE;
        atput0(CONTEXT.STACKPOINTER, newSP);
    }

    @Override
    public String toString() {
        if (hasMethod()) {
            final BlockClosureObject closure = getClosure();
            if (closure != null) {
                return "CTX [] in " + getMethod();
            } else {
                return "CTX " + getMethod();
            }
        } else {
            return "CTX without method";
        }
    }

    public int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    public Object top() {
        return peek(0);
    }

    public Object peek(final int offset) {
        return atStack(getStackPointer() - offset);
    }

    public Object getReceiver() {
        return at0(CONTEXT.RECEIVER);
    }

    public Object atTemp(final long argumentIndex) {
        final Object value = at0(CONTEXT.TEMP_FRAME_START + argumentIndex);
        if (value == null) {
            return image.nil;
        } else {
            return value;
        }
    }

    public void atTempPut(final long argumentIndex, final Object value) {
        atput0(CONTEXT.TEMP_FRAME_START + argumentIndex, value);
    }

    public Object atStack(final long argumentIndex) {
        return at0(CONTEXT.TEMP_FRAME_START - 1 + argumentIndex);
    }

    public void atStackPut(final long argumentIndex, final Object value) {
        atput0(CONTEXT.TEMP_FRAME_START - 1 + argumentIndex, value);
    }

    public int getStackSize() {
        return size() - CONTEXT.TEMP_FRAME_START;
    }

    public void become(final ContextObject other) {
        becomeOtherClass(other);
        // FIXME:
        final Object[] otherPointers = other.getPointers();
        other.setPointers(this.getPointers());
        setPointers(otherPointers);
        throw new SqueakException("Not implemented yet");
    }

    public BlockClosureObject getClosure() {
        final Object closureOrNil = at0(CONTEXT.CLOSURE_OR_NIL);
        return closureOrNil == image.nil ? null : (BlockClosureObject) closureOrNil;
    }

    public boolean isUnwindContext() {
        return getMethod().isUnwindMarked();
    }

    public Object[] getReceiverAndNArguments(final int numArgs) {
        final Object[] arguments = new Object[1 + numArgs];
        arguments[0] = getReceiver();
        for (int i = 0; i < numArgs; i++) {
            arguments[1 + i] = atTemp(i);
        }
        return arguments;
    }

    public MaterializedFrame getTruffleFrame(final int numArgs) {
        assert truffleFrame == null || FrameAccess.getMethod(truffleFrame) != null : "Frame without method should not leak";
        if (truffleFrame != null) {
            return truffleFrame;
        }
        final CompiledMethodObject closureOrMethod = getMethod();
        final AbstractSqueakObject sender = getSender();
        final BlockClosureObject closure = getClosure();
        final Object[] frameArgs = getReceiverAndNArguments(numArgs);
        final MaterializedFrame frame = Truffle.getRuntime().createMaterializedFrame(FrameAccess.newWith(closureOrMethod, sender, closure, frameArgs), closureOrMethod.getFrameDescriptor());
        throw new SqueakException("Not yet supported");
        // frame.setObject(closureOrMethod.thisContextOrMarkerSlot, this);
        // return frame;
    }

    /*
     * Helper function for debugging purposes.
     */
    @TruffleBoundary
    public void printSqStackTrace() {
        ContextObject current = this;
        while (current != null) {
            final CompiledCodeObject code = current.getClosureOrMethod();
            final Object[] rcvrAndArgs = current.getReceiverAndNArguments(code.getNumArgsAndCopied());
            image.getOutput().println(MiscUtils.format("%s #(%s) [%s]", current, ArrayUtils.toJoinedString(", ", rcvrAndArgs), current.getFrameMarker()));
            final AbstractSqueakObject sender = current.getSender();
            if (sender == image.nil) {
                break;
            } else {
                current = (ContextObject) sender;
            }
        }
    }

    public MaterializedFrame getTruffleFrame() {
        return truffleFrame;
    }

    public void findAndSetTruffleFrame() {
        assert frameMarker != null;
        final Frame frame = FrameAccess.findFrameForMarker(frameMarker);
        if (frame == null) {
            throw new SqueakException("Unable to find frame for marker:", frameMarker);
        }
        truffleFrame = frame.materialize();
    }

    public boolean hasTruffleFrame() {
        return truffleFrame != null;
    }

    public boolean hasMaterializedSender() {
        final Object sender = FrameAccess.getSender(truffleFrame);
        return sender == image.nil || sender instanceof ContextObject;
    }

    public FrameMarker getFrameMarker() {
        return frameMarker;
    }

    /**
     * This should only be used on contexts generated by
     * {@link SqueakImageContext#getDoItContext(Source)}.
     */
    public void restartIfTerminated() {
        if (isTerminated()) {
            atput0(CONTEXT.INSTRUCTION_POINTER, (long) ((CompiledMethodObject) getMethod()).getInitialPC());
        }
    }

    // The context represents primitive call which needs to be skipped when unwinding call stack.
    public boolean isPrimitiveContext() {
        final CompiledCodeObject codeObject = getClosureOrMethod();
        return codeObject.hasPrimitive() && codeObject instanceof CompiledMethodObject &&
                        (getInstructionPointer() - ((CompiledMethodObject) codeObject).getInitialPC() == CallPrimitiveNode.NUM_BYTECODES);
    }

    @Override
    public int size() {
        return size;
    }
}
