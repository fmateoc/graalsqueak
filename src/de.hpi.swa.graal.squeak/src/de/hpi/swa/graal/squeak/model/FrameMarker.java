package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import de.hpi.swa.graal.squeak.interop.SqueakObjectMessageResolutionForeign;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public final class FrameMarker implements TruffleObject {

    @Override
    public String toString() {
        return "FrameMarker@" + Integer.toHexString(System.identityHashCode(this));
    }

    public boolean isMatchingFrame(final Frame frame) {
        return FrameAccess.getContextOrMarker(frame) == this;
    }

    public ForeignAccess getForeignAccess() {
        return SqueakObjectMessageResolutionForeign.ACCESS;
    }
}
