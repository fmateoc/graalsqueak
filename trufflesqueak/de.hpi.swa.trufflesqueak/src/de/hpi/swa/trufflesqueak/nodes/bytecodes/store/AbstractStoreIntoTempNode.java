package de.hpi.swa.trufflesqueak.nodes.bytecodes.store;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.SqueakBytecodeNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameSlotWriteNode;

public abstract class AbstractStoreIntoTempNode extends SqueakBytecodeNode {
    @Child FrameSlotWriteNode storeNode;
    protected final int tempIndex;

    public AbstractStoreIntoTempNode(CompiledCodeObject code, int index, int numBytecodes, int tempIndex) {
        super(code, index, numBytecodes);
        assert code.getNumStackSlots() > tempIndex;
        this.tempIndex = tempIndex;
        this.storeNode = FrameSlotWriteNode.create(code.getTempSlot(tempIndex));
    }
}
