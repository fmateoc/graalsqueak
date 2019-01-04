package de.hpi.swa.graal.squeak.nodes.primitives;

import com.oracle.truffle.api.dsl.Fallback;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.FrameMarker;

public final class PrimitiveInterfaces {
    public interface AbstractPrimitive {
        int getNumArguments();
    }

    public interface UnaryPrimitiveWithoutFallback extends AbstractPrimitive {
        default int getNumArguments() {
            return 1;
        }
    }

    public interface UnaryPrimitive extends UnaryPrimitiveWithoutFallback {
        @Fallback
        default Object doFail(final Object arg1) {
            assert !(arg1 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }

    public interface BinaryPrimitiveWithoutFallback extends AbstractPrimitive {
        default int getNumArguments() {
            return 2;
        }
    }

    public interface BinaryPrimitive extends BinaryPrimitiveWithoutFallback {
        @Fallback
        default Object doFail(final Object arg1, final Object arg2) {
            assert !(arg1 instanceof FrameMarker) && !(arg2 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }

    public interface TernaryPrimitive extends AbstractPrimitive {
        default int getNumArguments() {
            return 3;
        }

        @Fallback
        default Object doFail(final Object arg1, final Object arg2, final Object arg3) {
            assert !(arg1 instanceof FrameMarker) && !(arg2 instanceof FrameMarker) && !(arg3 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }

    public interface QuaternaryPrimitive extends AbstractPrimitive {
        default int getNumArguments() {
            return 4;
        }

        @Fallback
        default Object doFail(final Object arg1, final Object arg2, final Object arg3, final Object arg4) {
            assert !(arg1 instanceof FrameMarker) && !(arg2 instanceof FrameMarker) && !(arg3 instanceof FrameMarker) && !(arg4 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }

    public interface QuinaryPrimitive extends AbstractPrimitive {
        default int getNumArguments() {
            return 5;
        }

        @Fallback
        default Object doFail(final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5) {
            assert !(arg1 instanceof FrameMarker) && !(arg2 instanceof FrameMarker) && !(arg3 instanceof FrameMarker) && !(arg4 instanceof FrameMarker) && !(arg5 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }

    public interface SenaryPrimitive extends AbstractPrimitive {
        default int getNumArguments() {
            return 6;
        }

        @Fallback
        default Object doFail(final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5, final Object arg6) {
            assert !(arg1 instanceof FrameMarker) && !(arg2 instanceof FrameMarker) && !(arg3 instanceof FrameMarker) && !(arg4 instanceof FrameMarker) && !(arg5 instanceof FrameMarker) &&
                            !(arg6 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }

    public interface SeptenaryPrimitive extends AbstractPrimitive {
        default int getNumArguments() {
            return 7;
        }

        @Fallback
        default Object doFail(final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5, final Object arg6, final Object arg7) {
            assert !(arg1 instanceof FrameMarker) && !(arg2 instanceof FrameMarker) && !(arg3 instanceof FrameMarker) && !(arg4 instanceof FrameMarker) && !(arg5 instanceof FrameMarker) &&
                            !(arg6 instanceof FrameMarker) && !(arg7 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }

    public interface OctonaryPrimitive extends AbstractPrimitive {
        default int getNumArguments() {
            return 8;
        }

        @Fallback
        default Object doFail(final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5, final Object arg6, final Object arg7, final Object arg8) {
            assert !(arg1 instanceof FrameMarker) && !(arg2 instanceof FrameMarker) && !(arg3 instanceof FrameMarker) && !(arg4 instanceof FrameMarker) && !(arg5 instanceof FrameMarker) &&
                            !(arg6 instanceof FrameMarker) && !(arg7 instanceof FrameMarker) && !(arg8 instanceof FrameMarker);
            throw new PrimitiveFailed();
        }
    }
}
