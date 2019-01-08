package de.hpi.swa.graal.squeak.util;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;

public interface SqueakFrameVisitor<T> {
    T visitFrame(FrameInstance frameInstance, Frame frame);
}
