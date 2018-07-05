package de.hpi.swa.graal.squeak.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.exceptions.Returns.LocalReturn;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.nodes.HandlePrimitiveFailedNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodesFactory.CallPrimitiveNodeGen;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushLiteralConstantNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushLiteralVariableNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushReceiverVariableNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushTemporaryLocationNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.SendBytecodes.SendSelfSelector;
import de.hpi.swa.graal.squeak.nodes.bytecodes.SendBytecodes.SingleExtendedSuperNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.StoreBytecodes.PopIntoAssociationNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.StoreBytecodes.PopIntoReceiverVariableNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.StoreBytecodes.PopIntoTemporaryLocationNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.StoreBytecodes.StoreIntoAssociationNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.StoreBytecodes.StoreIntoReceiverVariableNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.StoreBytecodes.StoreIntoTempNode;
import de.hpi.swa.graal.squeak.nodes.context.stack.StackPopNode;
import de.hpi.swa.graal.squeak.nodes.context.stack.StackPushNode;
import de.hpi.swa.graal.squeak.nodes.context.stack.StackTopNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveNodeFactory;

public final class MiscellaneousBytecodes {

    public abstract static class CallPrimitiveNode extends AbstractBytecodeNode {
        @Child private HandlePrimitiveFailedNode handlePrimFailed;
        @Child protected AbstractPrimitiveNode primitiveNode;
        private final int primitiveIndex;
        private final ValueProfile primitiveNodeProfile = ValueProfile.createClassProfile();

        public static CallPrimitiveNode create(final CompiledMethodObject code, final int index, final int numBytecodes, final int byte1, final int byte2) {
            return CallPrimitiveNodeGen.create(code, index, numBytecodes, byte1, byte2);
        }

        public CallPrimitiveNode(final CompiledMethodObject code, final int index, final int numBytecodes, final int byte1, final int byte2) {
            super(code, index, numBytecodes);
            primitiveIndex = byte1 + (byte2 << 8);
            primitiveNode = PrimitiveNodeFactory.forIndex(code, primitiveIndex);
            handlePrimFailed = HandlePrimitiveFailedNode.create(code);
        }

        @Specialization(guards = {"code.hasPrimitive()", "primitiveNode != null"})
        protected final int doPrimitive(final VirtualFrame frame) {
            try {
                throw new LocalReturn(primitiveNodeProfile.profile(primitiveNode).executePrimitive(frame));
            } catch (PrimitiveFailed e) {
                handlePrimFailed.executeHandle(frame, e);
                // if (!(primitiveNode instanceof PrimitiveFailedNode)) {
                // code.image.trace("PrimFail: " + primitiveNode);
                // }
            } catch (UnsupportedSpecializationException e) {
                // final String message = e.getMessage();
                // if (message.contains("[Long,PointersObject]") ||
                // message.contains("[FloatObject,PointersObject]")) {
                // return;
                // }
                // code.image.trace("UnsupportedSpecializationException: " + e);
            }
            return getSuccessorIndex(); // continue with fallback code
        }

        @Specialization(guards = {"!code.hasPrimitive() || primitiveNode == null"})
        protected final int doFallback(@SuppressWarnings("unused") final VirtualFrame frame) {
            return getSuccessorIndex(); // continue with fallback code immediately
        }

        @Override
        public String toString() {
            return "callPrimitive: " + primitiveIndex;
        }
    }

    public static final class DoubleExtendedDoAnythingNode {

        public static AbstractBytecodeNode create(final CompiledCodeObject code, final int index, final int numBytecodes, final int second, final int third) {
            final int opType = second >> 5;
            switch (opType) {
                case 0:
                    return new SendSelfSelector(code, index, numBytecodes, code.getLiteral(third), second & 31);
                case 1:
                    return new SingleExtendedSuperNode(code, index, numBytecodes, third, second & 31);
                case 2:
                    return new PushReceiverVariableNode(code, index, numBytecodes, third);
                case 3:
                    return new PushLiteralConstantNode(code, index, numBytecodes, third);
                case 4:
                    return new PushLiteralVariableNode(code, index, numBytecodes, third);
                case 5:
                    return new StoreIntoReceiverVariableNode(code, index, numBytecodes, third);
                case 6:
                    return new PopIntoReceiverVariableNode(code, index, numBytecodes, third);
                case 7:
                    return new StoreIntoAssociationNode(code, index, numBytecodes, third);
                default:
                    return new UnknownBytecodeNode(code, index, numBytecodes, second);
            }
        }
    }

    public static final class DupNode extends UnknownBytecodeNode {
        @Child private StackPushNode pushNode = StackPushNode.create();
        @Child private StackTopNode topNode;

        public DupNode(final CompiledCodeObject code, final int index, final int numBytecodes) {
            super(code, index, numBytecodes, -1);
            topNode = StackTopNode.create(code);
        }

        @Override
        public void executeVoid(final VirtualFrame frame) {
            pushNode.executeWrite(frame, topNode.executeRead(frame));
        }

        @Override
        public String toString() {
            return "dup";
        }
    }

    public static final class ExtendedBytecodes {

        public static AbstractBytecodeNode createPopInto(final CompiledCodeObject code, final int index, final int numBytecodes, final int nextByte) {
            final long variableIndex = variableIndex(nextByte);
            switch (variableType(nextByte)) {
                case 0:
                    return new PopIntoReceiverVariableNode(code, index, numBytecodes, variableIndex);
                case 1:
                    return new PopIntoTemporaryLocationNode(code, index, numBytecodes, variableIndex);
                case 2:
                    return new UnknownBytecodeNode(code, index, numBytecodes, nextByte);
                case 3:
                    return new PopIntoAssociationNode(code, index, numBytecodes, variableIndex);
                default:
                    throw new SqueakException("illegal ExtendedStore bytecode");
            }
        }

        public static AbstractBytecodeNode createPush(final CompiledCodeObject code, final int index, final int numBytecodes, final int nextByte) {
            final int variableIndex = variableIndex(nextByte);
            switch (variableType(nextByte)) {
                case 0:
                    return new PushReceiverVariableNode(code, index, numBytecodes, variableIndex);
                case 1:
                    return new PushTemporaryLocationNode(code, index, numBytecodes, variableIndex);
                case 2:
                    return new PushLiteralConstantNode(code, index, numBytecodes, variableIndex);
                case 3:
                    return new PushLiteralVariableNode(code, index, numBytecodes, variableIndex);
                default:
                    throw new SqueakException("unexpected type for ExtendedPush");
            }
        }

        public static AbstractBytecodeNode createStoreInto(final CompiledCodeObject code, final int index, final int numBytecodes, final int nextByte) {
            final long variableIndex = variableIndex(nextByte);
            switch (variableType(nextByte)) {
                case 0:
                    return new StoreIntoReceiverVariableNode(code, index, numBytecodes, variableIndex);
                case 1:
                    return new StoreIntoTempNode(code, index, numBytecodes, variableIndex);
                case 2:
                    return new UnknownBytecodeNode(code, index, numBytecodes, nextByte);
                case 3:
                    return new StoreIntoAssociationNode(code, index, numBytecodes, variableIndex);
                default:
                    throw new SqueakException("illegal ExtendedStore bytecode");
            }
        }

        protected static byte variableIndex(final int i) {
            return (byte) (i & 63);
        }

        protected static byte variableType(final int i) {
            return (byte) ((i >> 6) & 3);
        }
    }

    public static final class PopNode extends UnknownBytecodeNode {
        @Child private StackPopNode popNode;

        public PopNode(final CompiledCodeObject code, final int index, final int numBytecodes) {
            super(code, index, numBytecodes, -1);
            popNode = StackPopNode.create(code);
        }

        @Override
        public void executeVoid(final VirtualFrame frame) {
            popNode.executeRead(frame);
        }

        @Override
        public String toString() {
            return "pop";
        }
    }

    public static class UnknownBytecodeNode extends AbstractBytecodeNode {
        private final long bytecode;

        public UnknownBytecodeNode(final CompiledCodeObject code, final int index, final int numBytecodes, final int bc) {
            super(code, index, numBytecodes);
            bytecode = bc;
        }

        @Override
        public void executeVoid(final VirtualFrame frame) {
            throw new SqueakException("Unknown/uninterpreted bytecode:", bytecode);
        }

        @Override
        public String toString() {
            return "unknown: " + bytecode;
        }
    }
}
