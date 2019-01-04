package de.hpi.swa.graal.squeak.model;

import java.io.PrintStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.interop.SqueakObjectMessageResolutionForeign;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class FrameMarker implements TruffleObject {
    private static final boolean LOG_ALLOCATIONS = false;

    public FrameMarker(final VirtualFrame frame) {
        if (LOG_ALLOCATIONS) {
            logAllocations(frame.getArguments());
        }
    }

    @TruffleBoundary
    private void logAllocations(final Object[] arguments) {
        final PrintStream err = System.err;
        err.println(String.format("new %s; frame args: %s", toString(), ArrayUtils.toJoinedString(", ", arguments)));
    }

    @Override
    public String toString() {
        return "FrameMarker@" + Integer.toHexString(System.identityHashCode(this));
    }

    public boolean matches(final Frame frame) {
        return FrameAccess.getContextOrMarker(frame) == this;
    }

    public ContextObject getMaterializedContext() {
        final Frame targetFrame = FrameAccess.findFrameForMarker(this);
        if (targetFrame == null) {
            throw new SqueakException("Could not find frame for: " + this);
        }
        return getMaterializedContext(targetFrame);
    }

    public ContextObject getMaterializedContext(final Frame matchingFrame) {
        final Object contextOrMarker = FrameAccess.getContextOrMarker(matchingFrame);
        if (contextOrMarker instanceof ContextObject) {
            return (ContextObject) contextOrMarker; // TODO: Refactor this code path.
        }
        assert matches(matchingFrame) : "Frame does not match";
        final CompiledCodeObject code = FrameAccess.getMethod(matchingFrame);
        final MaterializedFrame materializedFrame = matchingFrame.materialize();
        final ContextObject context = ContextObject.create(code.image, code.sqContextSize(), materializedFrame, this);
        materializedFrame.setObject(code.thisContextOrMarkerSlot, context);
        return context;
    }

    public ForeignAccess getForeignAccess() {
        return SqueakObjectMessageResolutionForeign.ACCESS;
    }
}
