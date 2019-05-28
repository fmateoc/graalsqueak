package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.exceptions.ProcessSwitch;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageReader;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackReadNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackWriteNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.MiscUtils;

@ExportLibrary(SqueakObjectLibrary.class)
public final class ContextObject extends AbstractSqueakObjectWithClassAndHash {
    @CompilationFinal private MaterializedFrame truffleFrame;
    @CompilationFinal private int size;
    private boolean hasModifiedSender = false;
    private boolean escaped = false;

    private ContextObject(final SqueakImageContext image, final long hash) {
        super(image, hash, image.methodContextClass);
        truffleFrame = null;
    }

    private ContextObject(final SqueakImageContext image, final int size) {
        super(image, image.methodContextClass);
        truffleFrame = null;
        this.size = size;
    }

    private ContextObject(final Frame frame, final CompiledCodeObject blockOrMethod) {
        super(blockOrMethod.image, blockOrMethod.image.methodContextClass);
        assert FrameAccess.getSender(frame) != null;
        assert FrameAccess.getContext(frame, blockOrMethod) == null;
        truffleFrame = frame.materialize();
        FrameAccess.setContext(truffleFrame, blockOrMethod, this);
        size = blockOrMethod.getSqueakContextSize();
    }

    private ContextObject(final ContextObject original) {
        super(original.image, original.image.methodContextClass);
        final CompiledCodeObject code = FrameAccess.getBlockOrMethod(original.truffleFrame);
        hasModifiedSender = original.hasModifiedSender();
        escaped = original.escaped;
        size = original.size;
        // Create shallow copy of Truffle frame
        truffleFrame = Truffle.getRuntime().createMaterializedFrame(original.truffleFrame.getArguments(), code.getFrameDescriptor());
        // Copy frame slot values
        FrameAccess.setMarker(truffleFrame, code, FrameAccess.getMarker(original.truffleFrame, code));
        FrameAccess.setContext(truffleFrame, code, this);
        FrameAccess.setInstructionPointer(truffleFrame, code, FrameAccess.getInstructionPointer(original.truffleFrame, code));
        FrameAccess.setStackPointer(truffleFrame, code, FrameAccess.getStackPointer(original.truffleFrame, code));
        // Copy stack
        final int numStackSlots = code.getNumStackSlots();
        for (int i = 0; i < numStackSlots; i++) {
            final FrameSlot slot = code.getStackSlot(i);
            FrameAccess.setStackSlot(truffleFrame, slot, original.truffleFrame.getValue(slot));
        }
    }

    public static ContextObject create(final SqueakImageContext image, final int size) {
        return new ContextObject(image, size);
    }

    public static ContextObject createWithHash(final SqueakImageContext image, final long hash) {
        return new ContextObject(image, hash);
    }

    public static ContextObject create(final FrameInstance frameInstance) {
        final Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.MATERIALIZE);
        return create(frame, FrameAccess.getBlockOrMethod(frame));
    }

    public static ContextObject create(final Frame frame, final CompiledCodeObject blockOrMethod) {
        return new ContextObject(frame, blockOrMethod);
    }

    /**
     * {@link ContextObject}s are filled in at a later stage by a
     * {@link SqueakImageReader#fillInContextObjects}.
     */
    @Override
    public void fillin(final SqueakImageChunk chunk) {
        // Do nothing.
    }

    public void fillinContext(final SqueakImageChunk chunk) {
        final Object[] pointers = chunk.getPointers();
        size = pointers.length;
        assert size > CONTEXT.TEMP_FRAME_START;
        final CompiledMethodObject method = (CompiledMethodObject) pointers[CONTEXT.METHOD];
        final AbstractSqueakObject sender = (AbstractSqueakObject) pointers[CONTEXT.SENDER_OR_NIL];
        assert sender != null : "sender should not be null";
        final Object closureOrNil = pointers[CONTEXT.CLOSURE_OR_NIL];
        final BlockClosureObject closure;
        final CompiledCodeObject code;
        if (closureOrNil == NilObject.SINGLETON) {
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
        FrameAccess.initializeMarker(truffleFrame, code);
        FrameAccess.setContext(truffleFrame, code, this);
        final SqueakObjectLibrary objectLibrary = SqueakObjectLibrary.getFactory().getUncached(this);
        objectLibrary.atput0(this, CONTEXT.INSTRUCTION_POINTER, pointers[CONTEXT.INSTRUCTION_POINTER]);
        objectLibrary.atput0(this, CONTEXT.STACKPOINTER, pointers[CONTEXT.STACKPOINTER]);
        for (int i = CONTEXT.TEMP_FRAME_START; i < pointers.length; i++) {
            objectLibrary.atput0(this, i, pointers[i]);
        }
    }

    /** Turns a ContextObject back into an array of pointers (fillIn reversed). */
    public Object[] asPointers() {
        assert hasTruffleFrame();
        final SqueakObjectLibrary objectLibrary = SqueakObjectLibrary.getFactory().getUncached(this);
        final Object[] pointers = new Object[size];
        for (int i = 0; i < size; i++) {
            pointers[i] = objectLibrary.at0(this, i);
        }
        return pointers;
    }

    public MaterializedFrame getOrCreateTruffleFrame() {
        if (truffleFrame == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Method is unknown, use dummy frame instead
            final int guessedArgumentSize = size > CONTEXT.LARGE_FRAMESIZE ? size - CONTEXT.LARGE_FRAMESIZE : size - CONTEXT.SMALL_FRAMESIZE;
            final Object[] dummyArguments = FrameAccess.newDummyWith(null, NilObject.SINGLETON, null, new Object[guessedArgumentSize]);
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
                final int expectedArgumentSize = FrameAccess.expectedArgumentSize(method.getNumArgsAndCopied());
                assert dummyArguments.length >= expectedArgumentSize : "Unexpected argument size, maybe dummy frame had wrong size?";
                FrameAccess.assertReceiverNotNull(truffleFrame);
                frameArguments = truffleFrame.getArguments();
                if (truffleFrame.getFrameDescriptor().getSize() > 0) {
                    instructionPointer = FrameAccess.getInstructionPointer(truffleFrame, method);
                    stackPointer = FrameAccess.getStackPointer(truffleFrame, method);
                } else { // Frame slots unknown, so initialize PC and SP.
                    instructionPointer = 0;
                    stackPointer = 0;
                }
            } else {
                // Receiver plus arguments.
                final Object[] squeakArguments = new Object[1 + method.getNumArgsAndCopied()];
                frameArguments = FrameAccess.newDummyWith(method, NilObject.SINGLETON, null, squeakArguments);
                instructionPointer = 0;
                stackPointer = 0;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, method.getFrameDescriptor());
            FrameAccess.initializeMarker(truffleFrame, method);
            FrameAccess.setContext(truffleFrame, method, this);
            FrameAccess.setInstructionPointer(truffleFrame, method, instructionPointer);
            FrameAccess.setStackPointer(truffleFrame, method, stackPointer);
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
                final int expectedArgumentSize = FrameAccess.expectedArgumentSize(compiledBlock.getNumArgsAndCopied());
                assert dummyArguments.length >= expectedArgumentSize : "Unexpected argument size, maybe dummy frame had wrong size?";
                if (dummyArguments.length > expectedArgumentSize) {
                    // Trim arguments.
                    frameArguments = Arrays.copyOfRange(dummyArguments, 0, expectedArgumentSize);
                } else {
                    frameArguments = truffleFrame.getArguments();
                }
                if (truffleFrame.getFrameDescriptor().getSize() > 0) {
                    instructionPointer = FrameAccess.getInstructionPointer(truffleFrame, compiledBlock);
                    stackPointer = FrameAccess.getStackPointer(truffleFrame, compiledBlock);
                } else { // Frame slots unknown, so initialize PC and SP;
                    instructionPointer = 0;
                    stackPointer = 0;
                }
            } else {
                // Receiver plus arguments.
                final Object[] squeakArguments = new Object[1 + compiledBlock.getNumArgsAndCopied()];
                frameArguments = FrameAccess.newDummyWith(null, NilObject.SINGLETON, null, squeakArguments);
                instructionPointer = 0;
                stackPointer = 0;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, compiledBlock.getFrameDescriptor());
            FrameAccess.assertSenderNotNull(truffleFrame);
            FrameAccess.assertReceiverNotNull(truffleFrame);
            FrameAccess.initializeMarker(truffleFrame, compiledBlock);
            FrameAccess.setContext(truffleFrame, compiledBlock, this);
            FrameAccess.setInstructionPointer(truffleFrame, compiledBlock, instructionPointer);
            FrameAccess.setStackPointer(truffleFrame, compiledBlock, stackPointer);
        }
        return truffleFrame;
    }

    public Object getFrameSender() {
        return FrameAccess.getSender(truffleFrame);
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

    /**
     * Sets the sender of a ContextObject.
     */
    public void setSender(final ContextObject value) {
        if (!hasModifiedSender && truffleFrame != null && FrameAccess.getSender(truffleFrame) != value.getFrameMarker()) {
            hasModifiedSender = true;
        }
        FrameAccess.setSender(getOrCreateTruffleFrame(), value);
    }

    public void removeSender() {
        if (hasModifiedSender) {
            hasModifiedSender = false;
        }
        FrameAccess.setSender(getOrCreateTruffleFrame(), NilObject.SINGLETON);
    }

    public int getInstructionPointer() {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            final CompiledBlockObject block = closure.getCompiledBlock();
            return FrameAccess.getInstructionPointer(truffleFrame, block) + block.getInitialPC();
        } else {
            final CompiledMethodObject method = getMethod();
            return FrameAccess.getInstructionPointer(truffleFrame, method) + method.getInitialPC();
        }
    }

    public int getInstructionPointerForBytecodeLoop() {
        return FrameAccess.getInstructionPointer(truffleFrame, getBlockOrMethod());
    }

    public void setInstructionPointer(final int value) {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            final CompiledBlockObject block = closure.getCompiledBlock();
            FrameAccess.setInstructionPointer(truffleFrame, block, value - block.getInitialPC());
        } else {
            final CompiledMethodObject method = getMethod();
            FrameAccess.setInstructionPointer(truffleFrame, method, value - method.getInitialPC());
        }
    }

    public int getStackPointer() {
        return FrameAccess.getStackPointer(truffleFrame, getBlockOrMethod());
    }

    public void setStackPointer(final int value) {
        assert 0 <= value && value <= getBlockOrMethod().getSqueakContextSize();
        FrameAccess.setStackPointer(getOrCreateTruffleFrame(), getBlockOrMethod(), value);
    }

    private boolean hasMethod() {
        return hasTruffleFrame() && getMethod() != null;
    }

    public CompiledMethodObject getMethod() {
        return FrameAccess.getMethod(truffleFrame);
    }

    public void setMethod(final CompiledMethodObject value) {
        FrameAccess.setMethod(getOrCreateTruffleFrame(value), value);
    }

    public BlockClosureObject getClosure() {
        return FrameAccess.getClosure(truffleFrame);
    }

    public boolean hasClosure() {
        return FrameAccess.getClosure(truffleFrame) != null;
    }

    public void setClosure(final BlockClosureObject value) {
        FrameAccess.setClosure(getOrCreateTruffleFrame(value), value);
    }

    public CompiledCodeObject getBlockOrMethod() {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            return closure.getCompiledBlock();
        } else {
            return getMethod();
        }
    }

    public Object getReceiver() {
        return FrameAccess.getReceiver(truffleFrame);
    }

    public void setReceiver(final Object value) {
        FrameAccess.setReceiver(getOrCreateTruffleFrame(), value);
    }

    public Object atTemp(final int index) {
        final CompiledCodeObject blockOrMethod = getBlockOrMethod();
        assert index < blockOrMethod.getNumStackSlots() : "Invalid context stack access at #" + index;
        return NilObject.nullToNil(truffleFrame.getValue(blockOrMethod.getStackSlot(index)));
    }

    public void atTempPut(final int index, final Object value) {
        FrameAccess.setArgumentIfInRange(getOrCreateTruffleFrame(), index, value);
        final CompiledCodeObject blockOrMethod = FrameAccess.getBlockOrMethod(truffleFrame);
        assert index < blockOrMethod.getNumStackSlots() : "Invalid context stack access at #" + index;
        final FrameSlot frameSlot = blockOrMethod.getStackSlot(index);
        FrameAccess.setStackSlot(truffleFrame, frameSlot, value);
    }

    public void terminate() {
        // Remove pc and sender.
        setInstructionPointer(-1);
        removeSender();
    }

    public boolean isTerminated() {
        return getInstructionPointerForBytecodeLoop() < 0 && getSender() == NilObject.SINGLETON;
    }

    public boolean hasEscaped() {
        return escaped;
    }

    public void markEscaped() {
        escaped = true;
    }

    public boolean hasModifiedSender() {
        return hasModifiedSender;
    }

    public void push(final Object value) {
        assert value != null;
        final int currentStackPointer = getStackPointer();
        assert currentStackPointer < CONTEXT.MAX_STACK_SIZE;
        setStackPointer(currentStackPointer + 1);
        atTempPut(currentStackPointer, value);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
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

    public int getStackSize() {
        return getBlockOrMethod().getSqueakContextSize();
    }

    private void setFields(final MaterializedFrame otherTruffleFrame, final int otherSize, final boolean otherHasModifiedSender, final boolean otherEscaped) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        truffleFrame = otherTruffleFrame;
        size = otherSize;
        hasModifiedSender = otherHasModifiedSender;
        escaped = otherEscaped;
    }

    private Object[] getReceiverAndNArguments(final int numArgs) {
        final Object[] arguments = new Object[1 + numArgs];
        arguments[0] = getReceiver();
        for (int i = 0; i < numArgs; i++) {
            arguments[1 + i] = atTemp(i);
        }
        return arguments;
    }

    public void transferTo(final PointersObject newProcess) {
        // Record a process to be awakened on the next interpreter cycle.
        final PointersObject scheduler = image.getScheduler();
        // assert newProcess != image.getActiveProcess() : "trying to switch to already active
        // process";
        final PointersObject currentProcess = image.getActiveProcess(); // overwritten in next line.
        scheduler.atput0(PROCESS_SCHEDULER.ACTIVE_PROCESS, newProcess);
        currentProcess.atput0(PROCESS.SUSPENDED_CONTEXT, this);
        final ContextObject newActiveContext = (ContextObject) newProcess.at0(PROCESS.SUSPENDED_CONTEXT);
        newProcess.atput0(PROCESS.SUSPENDED_CONTEXT, NilObject.SINGLETON);
        if (CompilerDirectives.isPartialEvaluationConstant(newActiveContext)) {
            throw ProcessSwitch.create(newActiveContext);
        } else {
            // Avoid further PE if newActiveContext is not a PE constant.
            throw ProcessSwitch.createWithBoundary(newActiveContext);
        }
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
            if (sender == NilObject.SINGLETON) {
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
        return !(FrameAccess.getSender(truffleFrame) instanceof FrameMarker);
    }

    public FrameMarker getFrameMarker() {
        return FrameAccess.getMarker(truffleFrame);
    }

    // The context represents primitive call which needs to be skipped when unwinding call stack.
    public boolean isPrimitiveContext() {
        return !hasClosure() && getMethod().hasPrimitive() && getInstructionPointerForBytecodeLoop() == CallPrimitiveNode.NUM_BYTECODES;
    }

    public boolean pointsTo(final Object thang) {
        // TODO: make sure this works correctly
        final SqueakObjectLibrary objectLibrary = SqueakObjectLibrary.getFactory().getUncached(this);
        if (truffleFrame != null) {
            for (int i = 0; i < size(); i++) {
                if (objectLibrary.at0(this, i) == thang) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * SQUEAK OBJECT ACCESS
     */

    @ExportMessage
    @ImportStatic(CONTEXT.class)
    public abstract static class At0 {
        @Specialization(guards = "index == SENDER_OR_NIL")
        protected static final Object doSender(final ContextObject context, @SuppressWarnings("unused") final int index) {
            return context.getSender();
        }

        @Specialization(guards = {"index == INSTRUCTION_POINTER", "context.getInstructionPointer() >= 0"})
        protected static final long doInstructionPointer(final ContextObject context, @SuppressWarnings("unused") final int index) {
            return context.getInstructionPointer(); // Must return a long.
        }

        @Specialization(guards = {"index == INSTRUCTION_POINTER", "context.getInstructionPointer() < 0"})
        protected static final NilObject doInstructionPointerTerminated(@SuppressWarnings("unused") final ContextObject context, @SuppressWarnings("unused") final int index) {
            return NilObject.SINGLETON;
        }

        @Specialization(guards = "index == STACKPOINTER")
        protected static final long doStackPointer(final ContextObject context, @SuppressWarnings("unused") final int index) {
            return context.getStackPointer(); // Must return a long.
        }

        @Specialization(guards = "index == METHOD")
        protected static final CompiledMethodObject doMethod(final ContextObject context, @SuppressWarnings("unused") final int index) {
            return context.getMethod();
        }

        @Specialization(guards = {"index == CLOSURE_OR_NIL", "context.getClosure() != null"})
        protected static final BlockClosureObject doClosure(final ContextObject context, @SuppressWarnings("unused") final int index) {
            return context.getClosure();
        }

        @Specialization(guards = {"index == CLOSURE_OR_NIL", "context.getClosure() == null"})
        protected static final NilObject doClosureNil(@SuppressWarnings("unused") final ContextObject context, @SuppressWarnings("unused") final int index) {
            return NilObject.SINGLETON;
        }

        @Specialization(guards = "index == RECEIVER")
        protected static final Object doReceiver(final ContextObject context, @SuppressWarnings("unused") final int index) {
            return context.getReceiver();
        }

        @Specialization(guards = {"index >= TEMP_FRAME_START", "codeObject == context.getBlockOrMethod()"}, //
                        limit = "2" /** thisContext and sender */
        )
        protected static final Object doTempCached(final ContextObject context, @SuppressWarnings("unused") final int index,
                        @SuppressWarnings("unused") @Cached(value = "context.getBlockOrMethod()", allowUncached = true) final CompiledCodeObject codeObject,
                        @Cached(value = "create(codeObject)", allowUncached = true) final FrameStackReadNode readNode) {
            final Object value = readNode.execute(context.getTruffleFrame(), index - CONTEXT.TEMP_FRAME_START);
            return NilObject.nullToNil(value);
        }

        @Specialization(guards = "index >= TEMP_FRAME_START")
        protected static final Object doTemp(final ContextObject context, final int index) {
            return context.atTemp(index - CONTEXT.TEMP_FRAME_START);
        }
    }

    @ExportMessage
    @ImportStatic(CONTEXT.class)
    public abstract static class Atput0 {
        @Specialization(guards = "index == SENDER_OR_NIL")
        protected static final void doSender(final ContextObject context, @SuppressWarnings("unused") final int index, final ContextObject value) {
            context.setSender(value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "index == SENDER_OR_NIL")
        protected static final void doSender(final ContextObject context, final int index, final NilObject value) {
            context.removeSender();
        }

        @Specialization(guards = {"index == INSTRUCTION_POINTER"})
        protected static final void doInstructionPointer(final ContextObject context, @SuppressWarnings("unused") final int index, final long value) {
            /**
             * TODO: Adjust control flow when pc of active context is changed. For this, an
             * exception could be used to unwind Truffle frames until the target frame is found.
             * However, this exception should only be thrown when the context object is actually
             * active. So it might need to be necessary to extend ContextObjects with an `isActive`
             * field to avoid the use of iterateFrames.
             */
            context.setInstructionPointer((int) value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"index == INSTRUCTION_POINTER"})
        protected static final void doInstructionPointerTerminated(final ContextObject context, final int index, final NilObject value) {
            context.setInstructionPointer(-1);
        }

        @Specialization(guards = "index == STACKPOINTER")
        protected static final void doStackPointer(final ContextObject context, @SuppressWarnings("unused") final int index, final long value) {
            context.setStackPointer((int) value);
        }

        @Specialization(guards = "index == METHOD")
        protected static final void doMethod(final ContextObject context, @SuppressWarnings("unused") final int index, final CompiledMethodObject value) {
            context.setMethod(value);
        }

        @Specialization(guards = {"index == CLOSURE_OR_NIL"})
        protected static final void doClosure(final ContextObject context, @SuppressWarnings("unused") final int index, final BlockClosureObject value) {
            context.setClosure(value);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"index == CLOSURE_OR_NIL"})
        protected static final void doClosure(final ContextObject context, final int index, final NilObject value) {
            context.setClosure(null);
        }

        @Specialization(guards = "index == RECEIVER")
        protected static final void doReceiver(final ContextObject context, @SuppressWarnings("unused") final int index, final Object value) {
            context.setReceiver(value);
        }

        @Specialization(guards = {"index >= TEMP_FRAME_START", "context.getBlockOrMethod() == codeObject"}, //
                        limit = "2"/** thisContext and sender */
        )
        protected static final void doTempCached(final ContextObject context, final int index, final Object value,
                        @SuppressWarnings("unused") @Cached(value = "context.getBlockOrMethod()", allowUncached = true) final CompiledCodeObject codeObject,
                        @Cached(value = "create(codeObject)", allowUncached = true) final FrameStackWriteNode writeNode) {
            final int stackIndex = index - CONTEXT.TEMP_FRAME_START;
            FrameAccess.setArgumentIfInRange(context.getTruffleFrame(), stackIndex, value);
            writeNode.execute(context.getTruffleFrame(), stackIndex, value);
        }

        @Specialization(guards = "index >= TEMP_FRAME_START")
        protected static final void doTemp(final ContextObject context, final int index, final Object value) {
            context.atTempPut(index - CONTEXT.TEMP_FRAME_START, value);
        }
    }

    @ExportMessage
    public static class Become {
        @Specialization(guards = "receiver != other")
        protected static final boolean doBecome(final ContextObject receiver, final ContextObject other) {
            receiver.becomeOtherClass(other);
            final MaterializedFrame otherTruffleFrame = other.truffleFrame;
            final int otherSize = other.size;
            final boolean otherHasModifiedSender = other.hasModifiedSender;
            final boolean otherEscaped = other.escaped;
            other.setFields(receiver.truffleFrame, receiver.size, receiver.hasModifiedSender, receiver.escaped);
            receiver.setFields(otherTruffleFrame, otherSize, otherHasModifiedSender, otherEscaped);
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static final boolean doFail(final ContextObject receiver, final Object other) {
            return false;
        }
    }

    @ExportMessage
    public int instsize() {
        return getSqueakClass().getBasicInstanceSize();
    }

    @ExportMessage
    public int size() {
        return size;
    }

    @ExportMessage
    public ContextObject shallowCopy() {
        return new ContextObject(this);
    }
}
