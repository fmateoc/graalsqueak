package de.hpi.swa.graal.squeak.exceptions;

import com.oracle.truffle.api.nodes.ControlFlowException;

import de.hpi.swa.graal.squeak.model.FrameMarker;

public final class InstructionPointerModification extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    private final FrameMarker target;
    private final int value;

    public InstructionPointerModification(final FrameMarker target, final int newPC) {
        this.target = target; // target is assumed to be active on Truffle stack.
        this.value = newPC;
    }

    public FrameMarker getTarget() {
        return target;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "InstructionPointerModification (target: " + target + ", value: " + value + ")";
    }
}
