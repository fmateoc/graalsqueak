package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.MiscUtils;

public final class ContextObject extends AbstractSqueakObject {
    @CompilationFinal private MaterializedFrame truffleFrame;
    @CompilationFinal private FrameMarker frameMarker;
    @CompilationFinal private int size;
    private boolean hasModifiedSender = false;
    private boolean escaped = false;

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
        assert FrameAccess.getSender(frame) != null;
        truffleFrame = frame;
        this.frameMarker = frameMarker;
        this.size = size;
    }

    public ContextObject(final ContextObject original) {
        super(original.image, original.image.methodContextClass);
        final CompiledCodeObject code = FrameAccess.getBlockOrMethod(original.truffleFrame);
        frameMarker = new FrameMarker(null);
        hasModifiedSender = original.hasModifiedSender();
        escaped = original.escaped;
        size = original.size;
        // Create shallow copy of Truffle frame
        truffleFrame = Truffle.getRuntime().createMaterializedFrame(original.truffleFrame.getArguments(), code.getFrameDescriptor());
        // Copy frame slot values
        truffleFrame.setObject(code.thisContextOrMarkerSlot, this);
        truffleFrame.setInt(code.instructionPointerSlot, FrameUtil.getIntSafe(original.truffleFrame, code.instructionPointerSlot));
        truffleFrame.setInt(code.stackPointerSlot, FrameUtil.getIntSafe(original.truffleFrame, code.stackPointerSlot));
        // Copy stack
        final int numStackSlots = code.getNumStackSlots();
        for (int i = 0; i < numStackSlots; i++) {
            final FrameSlot slot = code.getStackSlot(i);
            truffleFrame.setObject(slot, original.truffleFrame.getValue(slot));
        }
    }

    public void fillIn(final Object[] pointers) {
        assert pointers.length > CONTEXT.TEMP_FRAME_START;
        final CompiledMethodObject method = (CompiledMethodObject) pointers[CONTEXT.METHOD];
        final Object sender = pointers[CONTEXT.SENDER_OR_NIL];
        assert sender != null : "sender should not be null";
        final Object closureOrNil = pointers[CONTEXT.CLOSURE_OR_NIL];
        final BlockClosureObject closure;
        final CompiledCodeObject code;
        if (closureOrNil == image.nil) {
            closure = null;
            code = method;
        } else {
            closure = (BlockClosureObject) closureOrNil;
            code = closure.getCompiledBlock(method);
        }
        final int endArguments = CONTEXT.RECEIVER + 1 + method.getNumArgsAndCopied();
        final Object[] arguments = Arrays.copyOfRange(pointers, CONTEXT.RECEIVER, endArguments);
        final Object[] frameArguments = FrameAccess.newWith(method, sender, closure, arguments);
        CompilerDirectives.transferToInterpreterAndInvalidate();
        truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, code.getFrameDescriptor());
        truffleFrame.setObject(code.thisContextOrMarkerSlot, this);
        truffleFrame.setInt(code.instructionPointerSlot, pointers[CONTEXT.INSTRUCTION_POINTER] == image.nil ? -1 : (int) (long) pointers[CONTEXT.INSTRUCTION_POINTER]);
        truffleFrame.setInt(code.stackPointerSlot, (int) (long) pointers[CONTEXT.STACKPOINTER]);
        for (int i = CONTEXT.TEMP_FRAME_START; i < pointers.length; i++) {
            final Object pointer = pointers[i];
            // if (pointer != image.nil) {
            // TODO: do better than FrameSlotKind.Object
            final FrameSlot frameSlot = code.getStackSlot(i - CONTEXT.TEMP_FRAME_START);
            truffleFrame.getFrameDescriptor().setFrameSlotKind(frameSlot, FrameSlotKind.Object);
            truffleFrame.setObject(frameSlot, pointer);
        }
    }

    public void terminate() {
        // remove pc and sender without flagging as dirty
        atput0(CONTEXT.INSTRUCTION_POINTER, image.nil);
        atput0(CONTEXT.SENDER_OR_NIL, image.nil);
    }

    public boolean isTerminated() {
        return at0(CONTEXT.INSTRUCTION_POINTER) == image.nil && at0(CONTEXT.SENDER_OR_NIL) == image.nil;
    }

    public Object at0(final long longIndex) {
        assert longIndex >= 0;
        final int index = (int) longIndex;
        switch (index) {
            case CONTEXT.SENDER_OR_NIL:
                return getSender();
            case CONTEXT.INSTRUCTION_POINTER:
                final int pc = getInstructionPointer();
                return pc < 0 ? image.nil : (long) pc;  // Must return a long here.
            case CONTEXT.STACKPOINTER:
                return (long) getStackPointer(); // Must return a long here.
            case CONTEXT.METHOD:
                return getMethod();
            case CONTEXT.CLOSURE_OR_NIL:
                final BlockClosureObject closure = FrameAccess.getClosure(truffleFrame);
                return closure == null ? image.nil : closure;
            case CONTEXT.RECEIVER:
                return FrameAccess.getReceiver(truffleFrame);
            default:
                final int stackIndex = index - CONTEXT.TEMP_FRAME_START;
                final CompiledMethodObject method = getMethod();
                assert stackIndex < method.getNumStackSlots() : "Invalid context stack access at #" + stackIndex;
                final Object value = truffleFrame.getValue(method.getStackSlot(stackIndex));
                return value == null ? image.nil : value;
        }
    }

    public void atput0(final long longIndex, final Object value) {
        assert longIndex >= 0 && value != null;
        final int index = (int) longIndex;
        assert value != null : "null indicates a problem";
        switch (index) {
            case CONTEXT.SENDER_OR_NIL:
                assert value != null && !(value instanceof FrameMarker) : "sender should not be null or a marker anymore";
                if (!hasModifiedSender && value != image.nil && (truffleFrame == null || FrameAccess.getSender(truffleFrame) != ((ContextObject) value).getFrameMarker())) {
                    hasModifiedSender = true;
                }
                getOrCreateTruffleFrame().getArguments()[FrameAccess.SENDER_OR_SENDER_MARKER] = value;
                break;
            case CONTEXT.INSTRUCTION_POINTER:
                // TODO: adjust control flow when pc of active context is changed.
                setInstructionPointer(value == image.nil ? -1 : (int) (long) value);
                break;
            case CONTEXT.STACKPOINTER:
                final int intValue = (int) (long) value;
                assert 0 <= intValue && intValue <= getMethod().getSqueakContextSize();
                getOrCreateTruffleFrame().setInt(getMethod().stackPointerSlot, intValue);
                break;
            case CONTEXT.METHOD:
                assert value instanceof CompiledMethodObject : "CompiledMethodObject expected";
                getOrCreateTruffleFrame((CompiledMethodObject) value).getArguments()[FrameAccess.METHOD] = value;
                break;
            case CONTEXT.CLOSURE_OR_NIL:
                assert value == image.nil || value instanceof BlockClosureObject;
                getOrCreateTruffleFrame(value == image.nil ? null : ((BlockClosureObject) value)).getArguments()[FrameAccess.CLOSURE_OR_NULL] = value == image.nil ? null : value;
                break;
            case CONTEXT.RECEIVER:
                getOrCreateTruffleFrame().getArguments()[FrameAccess.RECEIVER] = value;
                break;
            default:
                final int stackIndex = index - CONTEXT.TEMP_FRAME_START;
                final Object[] frameArguments = getOrCreateTruffleFrame().getArguments();
                if (FrameAccess.ARGUMENTS_START + stackIndex < frameArguments.length) {
                    frameArguments[FrameAccess.ARGUMENTS_START + stackIndex] = value;
                }
                final CompiledMethodObject method = FrameAccess.getMethod(truffleFrame);
                assert stackIndex < method.getNumStackSlots() : "Invalid context stack access at #" + stackIndex;
                final Object valueOrNull = value == image.nil ? null : value;
                getOrCreateTruffleFrame().setObject(method.getStackSlot(stackIndex), valueOrNull);
        }
    }

    private MaterializedFrame getOrCreateTruffleFrame() {
        if (truffleFrame == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
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
            final int instructionPointer;
            final int stackPointer;
            if (truffleFrame != null) {
                assert FrameAccess.getSender(truffleFrame) != null : "Sender should not be null";

                final Object[] dummyArguments = truffleFrame.getArguments();
                final int expectedArgumentSize = FrameAccess.ARGUMENTS_START + method.getNumArgsAndCopied();
                assert dummyArguments.length >= expectedArgumentSize : "Unexpected argument size, maybe dummy frame had wrong size?";
                frameArguments = truffleFrame.getArguments();
                assert frameArguments[FrameAccess.RECEIVER] != null : "Receiver should probably not be null here";
                if (truffleFrame.getFrameDescriptor().getSize() > 0) {
                    instructionPointer = FrameUtil.getIntSafe(truffleFrame, method.instructionPointerSlot);
                    stackPointer = FrameUtil.getIntSafe(truffleFrame, method.stackPointerSlot);
                } else { // Frame slots unknown, so initialize PC and SP.
                    instructionPointer = method.getInitialPC();
                    stackPointer = 0;
                }
            } else {
                // Receiver plus arguments.
                final Object[] squeakArguments = new Object[1 + method.getNumArgsAndCopied()];
                frameArguments = FrameAccess.newDummyWith(method, image.nil, null, squeakArguments);
                instructionPointer = 0;
                stackPointer = 0;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, method.getFrameDescriptor());
            truffleFrame.setInt(method.instructionPointerSlot, instructionPointer);
            truffleFrame.setInt(method.stackPointerSlot, stackPointer);
            truffleFrame.setObject(method.thisContextOrMarkerSlot, this);
        }
        return truffleFrame;
    }

    private MaterializedFrame getOrCreateTruffleFrame(final BlockClosureObject closure) {
        if (truffleFrame == null || FrameAccess.getClosure(truffleFrame) != closure) {
            final Object[] frameArguments;
            final CompiledBlockObject compiledBlock = closure.getCompiledBlock();
            final int instructionPointer;
            final int stackPointer;
            if (truffleFrame != null) {
                // FIXME: Assuming here this context is not active, add check?
                assert FrameAccess.getSender(truffleFrame) != null : "Sender should not be null";

                final Object[] dummyArguments = truffleFrame.getArguments();
                final int expectedArgumentSize = FrameAccess.ARGUMENTS_START + compiledBlock.getNumArgsAndCopied();
                assert dummyArguments.length >= expectedArgumentSize : "Unexpected argument size, maybe dummy frame had wrong size?";
                if (dummyArguments.length > expectedArgumentSize) {
                    // Trim arguments.
                    frameArguments = Arrays.copyOfRange(dummyArguments, 0, expectedArgumentSize);
                } else {
                    frameArguments = truffleFrame.getArguments();
                }
                assert frameArguments[FrameAccess.RECEIVER] != null : "Receiver should probably not be null here";

                if (truffleFrame.getFrameDescriptor().getSize() > 0) {
                    instructionPointer = FrameUtil.getIntSafe(truffleFrame, compiledBlock.instructionPointerSlot);
                    stackPointer = FrameUtil.getIntSafe(truffleFrame, compiledBlock.stackPointerSlot);
                } else { // Frame slots unknown, so initialize PC and SP;
                    instructionPointer = compiledBlock.getInitialPC();
                    stackPointer = 0;
                }
            } else {
                // Receiver plus arguments.
                final Object[] squeakArguments = new Object[1 + compiledBlock.getNumArgsAndCopied()];
                frameArguments = FrameAccess.newDummyWith(null, image.nil, null, squeakArguments);
                instructionPointer = compiledBlock.getInitialPC();
                stackPointer = 0;
            }
            assert frameArguments[FrameAccess.RECEIVER] != null : "Receiver should probably not be null here";
            assert frameArguments[FrameAccess.SENDER_OR_SENDER_MARKER] != null;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, compiledBlock.getFrameDescriptor());
            truffleFrame.setInt(compiledBlock.instructionPointerSlot, instructionPointer);
            truffleFrame.setInt(compiledBlock.stackPointerSlot, stackPointer);
            truffleFrame.setObject(compiledBlock.thisContextOrMarkerSlot, this);
        }
        return truffleFrame;
    }

    public CompiledCodeObject getBlockOrMethod() {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            return closure.getCompiledBlock();
        } else {
            return getMethod();
        }
    }

    private boolean hasMethod() {
        return hasTruffleFrame() && getMethod() != null;
    }

    public CompiledMethodObject getMethod() {
        return FrameAccess.getMethod(truffleFrame);
    }

    public AbstractSqueakObject shallowCopy() {
        return new ContextObject(this);
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

    public AbstractSqueakObject getSender() {
        final Object value = FrameAccess.getSender(truffleFrame);
        if (value instanceof FrameMarker) {
            return ((FrameMarker) value).getMaterializedContext();
        } else {
            return (AbstractSqueakObject) value;
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
        final int newSP = getStackPointer() + 1;
        assert newSP <= CONTEXT.MAX_STACK_SIZE;
        atStackPut(newSP, value);
        setStackPointer(newSP);
    }

    public int getInstructionPointer() {
        return FrameUtil.getIntSafe(truffleFrame, getMethod().instructionPointerSlot);
    }

    public void setInstructionPointer(final int value) {
        getOrCreateTruffleFrame().setInt(getMethod().instructionPointerSlot, value);
    }

    public int getStackPointer() {
        return FrameUtil.getIntSafe(truffleFrame, getMethod().stackPointerSlot);
    }

    public void setStackPointer(final int value) {
        assert 0 <= value && value <= getMethod().getSqueakContextSize();
        getOrCreateTruffleFrame().setInt(getMethod().stackPointerSlot, value);
    }

    @Override
    public String toString() {
        if (hasMethod()) {
            final BlockClosureObject closure = getClosure();
            if (closure != null) {
                return "CTX [] in " + getMethod() + " #" + hashCode();
            } else {
                return "CTX " + getMethod() + " #" + hashCode();
            }
        } else {
            return "CTX without method" + " #" + hashCode();
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
        return at0(CONTEXT.TEMP_FRAME_START + argumentIndex);
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
        return FrameAccess.getStackSize(truffleFrame);
    }

    public void become(final ContextObject other) {
        becomeOtherClass(other);
        // FIXME:
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

    /*
     * Helper function for debugging purposes.
     */
    @TruffleBoundary
    public void printSqStackTrace() {
        ContextObject current = this;
        while (current != null) {
            final CompiledCodeObject code = current.getBlockOrMethod();
            final Object[] rcvrAndArgs = current.getReceiverAndNArguments(code.getNumArgsAndCopied());
            image.getOutput().println(MiscUtils.format("%s #(%s) [%s]", current, ArrayUtils.toJoinedString(", ", rcvrAndArgs), current.getFrameMarker()));
            final Object sender = current.getSender();
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

    public boolean hasTruffleFrame() {
        return truffleFrame != null;
    }

    public boolean hasMaterializedSender() {
        final Object sender = FrameAccess.getSender(truffleFrame);
        return sender == image.nil || sender instanceof ContextObject;
    }

    public boolean hasFrameMarker() {
        return frameMarker != null;
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
            assert getClosure() == null;
            atput0(CONTEXT.INSTRUCTION_POINTER, (long) getMethod().getInitialPC());
        }
    }

    // The context represents primitive call which needs to be skipped when unwinding call stack.
    public boolean isPrimitiveContext() {
        final CompiledCodeObject codeObject = getBlockOrMethod();
        return codeObject.hasPrimitive() && codeObject instanceof CompiledMethodObject &&
                        (getInstructionPointer() - ((CompiledMethodObject) codeObject).getInitialPC() == CallPrimitiveNode.NUM_BYTECODES);
    }

    public int size() {
        return size;
    }

    public boolean pointsTo(final Object thang) {
        // TODO: make sure this works correctly
        if (truffleFrame != null) {
            for (int i = 0; i < size(); i++) {
                if (at0(i) == thang) {
                    return true;
                }
            }
        }
        return false;
    }
}
