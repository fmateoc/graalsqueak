package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;

public abstract class GetOrCreateContextNode extends AbstractNodeWithCode {
    protected final boolean materializeFrame;

    protected GetOrCreateContextNode(final CompiledCodeObject code, final boolean materializeFrame) {
        super(code);
        this.materializeFrame = materializeFrame;
    }

    public static GetOrCreateContextNode create(final CompiledCodeObject code) {
        return GetOrCreateContextNodeGen.create(code, true);
    }

    public static GetOrCreateContextNode create(final CompiledCodeObject code, final boolean materializeFrame) {
        return GetOrCreateContextNodeGen.create(code, materializeFrame);
    }

    public abstract ContextObject executeGet(Frame frame);

    @Specialization(guards = {"materializeFrame", "isVirtualized(frame)"})
    protected final ContextObject doCreateMaterialized(final VirtualFrame frame) {
        return ContextObject.create(frame, code);
    }

    @Specialization(guards = {"!materializeFrame", "isVirtualized(frame)"})
    protected final ContextObject doCreateWithFrameMarker(final VirtualFrame frame) {
        return ContextObject.createWithFrameMarker(frame, code);
    }

    @Fallback
    protected final ContextObject doGet(final VirtualFrame frame) {
        return getContext(frame);
    }
}
