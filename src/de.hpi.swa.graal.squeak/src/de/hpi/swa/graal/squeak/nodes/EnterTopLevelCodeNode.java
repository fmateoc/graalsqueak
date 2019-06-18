package de.hpi.swa.graal.squeak.nodes;

import java.util.logging.Level;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackWriteNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.impl.ControlPrimitives.PrimitiveFailedNode;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class EnterTopLevelCodeNode extends AbstractNodeWithCode {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, CallPrimitiveNode.class);

    @Child private ExecuteContextNode executeContextNode;
    @Child private FrameStackWriteNode pushNode;
    @Child private AbstractPrimitiveNode primitiveNode;
    @Child private HandlePrimitiveFailedNode handlePrimitiveFailedNode;

    protected EnterTopLevelCodeNode(final CompiledCodeObject code) {
        super(code);
        primitiveNode = code.hasPrimitive() ? code.image.primitiveNodeFactory.forIndex((CompiledMethodObject) code, code.primitiveIndex()) : null;
    }

    public static EnterTopLevelCodeNode create(final CompiledCodeObject code) {
        return new EnterTopLevelCodeNode(code);
    }

    public Object execute(final ContextObject context) {
        final MaterializedFrame frame = context.getTruffleFrame();
        if (primitiveNode != null) {
            try {
                return primitiveNode.executePrimitive(frame);
            } catch (final PrimitiveFailed e) {
                /** getHandlePrimitiveFailedNode() acts as branch profile. */
                getHandlePrimitiveFailedNode().executeHandle(frame, getPushNode(), e);
                LOG.log(Level.FINE, () -> (primitiveNode instanceof PrimitiveFailedNode ? FrameAccess.getMethod(frame) : primitiveNode) +
                                " (" + ArrayUtils.toJoinedString(", ", FrameAccess.getReceiverAndArguments(frame)) + ")");
                /** continue with fallback code. */
            }
        }
        return getExecuteContextNode().executeTopLevelContext(context);
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
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return code.toString();
    }

    @Override
    @TruffleBoundary
    public SourceSection getSourceSection() {
        CompilerAsserts.neverPartOfCompilation();
        return code.getSource().createSection(1);
    }
}
