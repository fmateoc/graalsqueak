/*
 * Copyright (c) 2017-2019 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes.context.frame;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;

import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;

@ImportStatic(FrameSlotKind.class)
public abstract class FrameSlotReadNode extends AbstractFrameSlotNode {
    protected final boolean clear;

    protected FrameSlotReadNode(final boolean clear) {
        this.clear = clear;
    }

    public static FrameSlotReadNode create(final FrameSlot frameSlot) {
        return FrameSlotReadNodeGen.create(false, frameSlot);
    }

    public static FrameSlotReadNode create(final CompiledCodeObject code, final int index) {
        // Only clear stack values, not receiver, arguments, or temporary variables.
        final boolean clear = index >= (code instanceof CompiledBlockObject ? code.getNumArgsAndCopied() : code.getNumTemps());
        return FrameSlotReadNodeGen.create(clear, code.getStackSlot(index));
    }

    public abstract Object executeRead(Frame frame);

    @Specialization(guards = "frame.getFrameDescriptor().getFrameSlotKind(getSlot()) == Boolean")
    protected final boolean readBoolean(final Frame frame) {
        return FrameUtil.getBooleanSafe(frame, getSlot());
    }

    @Specialization(guards = "frame.getFrameDescriptor().getFrameSlotKind(getSlot()) == Long")
    protected final long readLong(final Frame frame) {
        return FrameUtil.getLongSafe(frame, getSlot());
    }

    @Specialization(guards = "frame.getFrameDescriptor().getFrameSlotKind(getSlot()) == Double")
    protected final double readDouble(final Frame frame) {
        return FrameUtil.getDoubleSafe(frame, getSlot());
    }

    @Specialization(guards = "!shouldClear()", replaces = {"readBoolean", "readLong", "readDouble"})
    protected final Object readObject(final Frame frame) {
        if (!frame.isObject(getSlot())) {
            /*
             * The FrameSlotKind has been set to Object, so from now on all writes to the slot will
             * be Object writes. However, now we are in a frame that still has an old non-Object
             * value. This is a slow-path operation: we read the non-Object value, and write it
             * immediately as an Object value so that we do not hit this path again multiple times
             * for the same slot of the same frame.
             */
            CompilerDirectives.transferToInterpreter();
            final Object value = frame.getValue(getSlot());
            assert value != null : "Unexpected `null` value";
            frame.setObject(getSlot(), value);
            return value;
        } else {
            return FrameUtil.getObjectSafe(frame, getSlot());
        }
    }

    @Specialization(guards = "shouldClear()", replaces = {"readBoolean", "readLong", "readDouble"})
    protected final Object readAndClearObject(final Frame frame) {
        final Object value;
        if (!frame.isObject(getSlot())) {
            /*
             * The FrameSlotKind has been set to Object, so from now on all writes to the slot will
             * be Object writes. However, now we are in a frame that still has an old non-Object
             * value. This is a slow-path operation: we read the non-Object value, and clear it
             * immediately as an Object value so that we do not hit this path again multiple times
             * for the same slot of the same frame.
             */
            CompilerDirectives.transferToInterpreter();
            value = frame.getValue(getSlot());
            assert value != null : "Unexpected `null` value";
        } else {
            value = FrameUtil.getObjectSafe(frame, getSlot());
        }
        frame.setObject(getSlot(), null);
        return value;
    }

    protected final boolean shouldClear() {
        return clear; // Work around "Useless condition" error in generated code.
    }
}
