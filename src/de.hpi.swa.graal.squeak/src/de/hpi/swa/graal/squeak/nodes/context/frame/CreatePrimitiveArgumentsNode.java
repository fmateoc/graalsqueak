package de.hpi.swa.graal.squeak.nodes.context.frame;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.NotProvided;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithCode;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class CreatePrimitiveArgumentsNode extends AbstractNodeWithCode {
    protected final int argumentCount;
    protected final int numNotProvided;
    @CompilationFinal private int stackPointer = -1;

    @Children private FrameSlotReadNode[] readNodes;

    protected CreatePrimitiveArgumentsNode(final CompiledCodeObject code, final int argumentCount, final int argumentsConsumedByPrimitive) {
        super(code);
        assert argumentCount <= argumentsConsumedByPrimitive : "Primitive may only consume more, not less arguments";
        this.argumentCount = argumentCount;
        numNotProvided = Math.max(argumentsConsumedByPrimitive - argumentCount, 0);
        readNodes = argumentCount == 0 ? null : new FrameSlotReadNode[argumentCount];
    }

    public static CreatePrimitiveArgumentsNode create(final CompiledCodeObject code, final int argumentCount, final int argumentsConsumedByPrimitive) {
        return new CreatePrimitiveArgumentsNode(code, argumentCount, argumentsConsumedByPrimitive);
    }

    @ExplodeLoop
    public Object[] execute(final VirtualFrame frame, final Object receiver) {
        if (stackPointer == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackPointer = FrameAccess.getStackPointer(frame, code) - argumentCount;
            assert stackPointer >= 0 : "Bad stack pointer";
        }
        FrameAccess.setStackPointer(frame, code, stackPointer - 1);

        final Object[] result = new Object[1 + argumentCount + numNotProvided];
        result[0] = receiver;
        for (int i = 0; i < argumentCount; i++) {
            result[1 + i] = getReadNode(i).executeRead(frame);
            assert result[1 + i] != null;
        }
        for (int i = 0; i < numNotProvided; i++) {
            result[1 + argumentCount + i] = NotProvided.SINGLETON;
        }
        return result;
    }

    protected FrameSlotReadNode getReadNode(final int offset) {
        if (readNodes[offset] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readNodes[offset] = insert(FrameSlotReadNode.create(code, stackPointer + offset));
        }
        return readNodes[offset];
    }
}
