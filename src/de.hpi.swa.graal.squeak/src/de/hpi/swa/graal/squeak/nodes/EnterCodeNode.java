package de.hpi.swa.graal.squeak.nodes;

import java.util.logging.Level;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.graal.squeak.SqueakLanguage;
import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackWriteNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.impl.ControlPrimitives.PrimitiveFailedNode;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@GenerateWrapper
public abstract class EnterCodeNode extends AbstractNodeWithCode implements InstrumentableNode {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, CallPrimitiveNode.class);

    private SourceSection sourceSection;
    private final BranchProfile primitiveFailedProfile = BranchProfile.create();

    @Child private ExecuteContextNode executeContextNode;
    @Child private FrameStackWriteNode pushNode;
    @Child private AbstractPrimitiveNode primitiveNode;
    @Child private HandlePrimitiveFailedNode handlePrimitiveFailedNode;

    protected EnterCodeNode(final EnterCodeNode codeNode) {
        this(codeNode.code);
    }

    protected EnterCodeNode(final CompiledCodeObject code) {
        super(code);
        primitiveNode = code.hasPrimitive() ? code.image.primitiveNodeFactory.forIndex((CompiledMethodObject) code, code.primitiveIndex()) : null;
    }

    public static SqueakCodeRootNode create(final SqueakLanguage language, final CompiledCodeObject code) {
        return new SqueakCodeRootNode(language, code);
    }

    public abstract Object execute(VirtualFrame frame);

    @NodeInfo(cost = NodeCost.NONE)
    public static final class SqueakCodeRootNode extends RootNode {
        @Child private EnterCodeNode codeNode;

        protected SqueakCodeRootNode(final SqueakLanguage language, final CompiledCodeObject code) {
            super(language, code.getFrameDescriptor());
            codeNode = EnterCodeNodeGen.create(code);
        }

        @Override
        public Object execute(final VirtualFrame frame) {
            return codeNode.execute(frame);
        }

        @Override
        public String getName() {
            return codeNode.toString();
        }

        @Override
        public SourceSection getSourceSection() {
            return codeNode.getSourceSection();
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return codeNode.toString();
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    @Specialization(assumptions = {"code.getCanBeVirtualizedAssumption()"})
    protected final Object enterVirtualized(final VirtualFrame frame) {
        CompilerDirectives.ensureVirtualized(frame);
        if (primitiveNode != null) {
            try {
                return primitiveNode.executePrimitive(frame);
            } catch (final PrimitiveFailed e) {
                primitiveFailedProfile.enter();
                initializeSlots(code, frame);
                initializeArgumentsAndTemps(frame);
                getHandlePrimitiveFailedNode().executeHandle(frame, getPushNode(), e);
                LOG.log(Level.FINE, () -> (primitiveNode instanceof PrimitiveFailedNode ? FrameAccess.getMethod(frame) : primitiveNode) +
                                " (" + ArrayUtils.toJoinedString(", ", FrameAccess.getReceiverAndArguments(frame)) + ")");
                /** continue with fallback code. */
            }
        } else {
            initializeSlots(code, frame);
            initializeArgumentsAndTemps(frame);
        }
        return getExecuteContextNode().executeContext(frame);
    }

    @Fallback
    protected final Object enter(final VirtualFrame frame) {
        final ContextObject newContext;
        if (primitiveNode != null) {
            try {
                return primitiveNode.executePrimitive(frame);
            } catch (final PrimitiveFailed e) {
                primitiveFailedProfile.enter();
                initializeSlots(code, frame);
                newContext = ContextObject.create(frame, code);
                assert newContext == FrameAccess.getContext(frame, code);
                initializeArgumentsAndTemps(frame);
                getHandlePrimitiveFailedNode().executeHandle(frame, getPushNode(), e);
                LOG.log(Level.FINE, () -> (primitiveNode instanceof PrimitiveFailedNode ? FrameAccess.getMethod(frame) : primitiveNode) +
                                " (" + ArrayUtils.toJoinedString(", ", FrameAccess.getReceiverAndArguments(frame)) + ")");
                /** continue with fallback code. */
            }
        } else {
            initializeSlots(code, frame);
            newContext = ContextObject.create(frame, code);
            assert newContext == FrameAccess.getContext(frame, code);
            initializeArgumentsAndTemps(frame);
        }
        return getExecuteContextNode().executeContext(frame);
    }

    private static void initializeSlots(final CompiledCodeObject code, final VirtualFrame frame) {
        FrameAccess.initializeMarker(frame, code);
        FrameAccess.setInstructionPointer(frame, code, 0);
        FrameAccess.setStackPointer(frame, code, 0);
    }

    @ExplodeLoop
    private void initializeArgumentsAndTemps(final VirtualFrame frame) {
        // Push arguments and copied values onto the newContext.
        final Object[] arguments = frame.getArguments();
        assert arguments.length == FrameAccess.expectedArgumentSize(code.getNumArgsAndCopied());
        for (int i = 0; i < code.getNumArgsAndCopied(); i++) {
            getPushNode().executePush(frame, arguments[FrameAccess.getArgumentStartIndex() + i]);
        }
        // Initialize remaining temporary variables with nil in newContext.
        final int remainingTemps = code.getNumTemps() - code.getNumArgs();
        for (int i = 0; i < remainingTemps; i++) {
            getPushNode().executePush(frame, NilObject.SINGLETON);
        }
        assert FrameAccess.getStackPointer(frame, code) >= remainingTemps;
    }

    private ExecuteContextNode getExecuteContextNode() {
        if (executeContextNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            executeContextNode = insert(ExecuteContextNode.create(code));
        }
        return executeContextNode;
    }

    private FrameStackWriteNode getPushNode() {
        if (pushNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pushNode = insert(FrameStackWriteNode.create(code));
        }
        return pushNode;
    }

    private HandlePrimitiveFailedNode getHandlePrimitiveFailedNode() {
        if (handlePrimitiveFailedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handlePrimitiveFailedNode = insert(HandlePrimitiveFailedNode.create(code));
        }
        return handlePrimitiveFailedNode;
    }

    @Override
    public final String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return code.toString();
    }

    @Override
    public final boolean hasTag(final Class<? extends Tag> tag) {
        return tag == StandardTags.RootTag.class;
    }

    @Override
    public final boolean isInstrumentable() {
        return true;
    }

    @Override
    public final WrapperNode createWrapper(final ProbeNode probe) {
        return new EnterCodeNodeWrapper(this, this, probe);
    }

    @Override
    @TruffleBoundary
    public final SourceSection getSourceSection() {
        CompilerAsserts.neverPartOfCompilation();
        if (sourceSection == null) {
            sourceSection = code.getSource().createSection(1);
        }
        return sourceSection;
    }
}
