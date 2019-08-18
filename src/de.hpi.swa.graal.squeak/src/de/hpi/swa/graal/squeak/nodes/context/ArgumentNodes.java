package de.hpi.swa.graal.squeak.nodes.context;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.NotProvided;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameSlotReadNode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class ArgumentNodes {
    public abstract static class AbstractArgumentNode extends AbstractNode {
        public abstract Object execute(VirtualFrame frame);

        public static final AbstractArgumentNode create(final int argumentIndex, final int numArguments, final int argumentCount) {
            if (argumentIndex <= numArguments) {
                if (argumentCount >= 0) {
                    return new ArgumentOnStackNode(argumentIndex, argumentCount);
                } else {
                    return new ArgumentNode(argumentIndex);
                }
            } else {
                return new ArgumentNotProvidedNode();
            }
        }
    }

    public static final class ArgumentNode extends AbstractArgumentNode {
        private final int argumentIndex;

        public ArgumentNode(final int argumentIndex) {
            this.argumentIndex = argumentIndex; // argumentIndex == 0 returns receiver
        }

        @Override
        public Object execute(final VirtualFrame frame) {
            return FrameAccess.getArgument(frame, argumentIndex);
        }
    }

    public static final class ArgumentOnStackNode extends AbstractArgumentNode {
        private final int argumentIndex;
        private final int argumentCount;

        @Child private FrameSlotReadNode readNode;

        public ArgumentOnStackNode(final int argumentIndex, final int argumentCount) {
            this.argumentIndex = argumentIndex; // argumentIndex == 0 returns receiver
            this.argumentCount = argumentCount;
        }

        @Override
        public Object execute(final VirtualFrame frame) {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                final CompiledCodeObject blockOrMethod = FrameAccess.getBlockOrMethod(frame);
                final int stackPointer = FrameAccess.getStackPointer(frame, blockOrMethod);
                readNode = insert(FrameSlotReadNode.create(blockOrMethod.getStackSlot(stackPointer - 1 - argumentCount + argumentIndex)));
            }
            return readNode.executeRead(frame);
        }
    }

    private static final class ArgumentNotProvidedNode extends AbstractArgumentNode {
        @Override
        public NotProvided execute(final VirtualFrame frame) {
            return NotProvided.SINGLETON;
        }
    }
}
