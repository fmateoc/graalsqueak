package de.hpi.swa.graal.squeak.image.reading;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ContextObject;

public abstract class FillInContextNode extends Node {

    public static FillInContextNode create() {
        return FillInContextNodeGen.create();
    }

    public abstract void execute(Object obj, SqueakImageChunk chunk);

    @Specialization
    protected static final void doContext(final ContextObject obj, final SqueakImageChunk chunk) {
        obj.fillIn(chunk.getPointers());
        // assert obj.getMethod().sqContextSize() + CONTEXT.TEMP_FRAME_START ==
        // obj.getPointers().length : "ContextObject has wrong size";
    }

    @SuppressWarnings("unused")
    @Fallback
    protected static final void doNothing(final Object obj, final SqueakImageChunk chunk) {
        // Nothing to do.
    }
}
