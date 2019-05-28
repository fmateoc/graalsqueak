package de.hpi.swa.graal.squeak.exceptions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ControlFlowException;

import de.hpi.swa.graal.squeak.model.ObjectLayouts.ERROR_TABLE;

public final class PrimitiveExceptions {

    protected static class AbstractPrimitiveFailed extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        private final int reasonCode;

        protected AbstractPrimitiveFailed(final int reasonCode) {
            this.reasonCode = reasonCode;
        }

        public final int getReasonCode() {
            return reasonCode;
        }
    }

    /**
     * Primitive failed.
     *
     * <p>
     * Below factory methods return {@code PrimitiveFailed}, such that it is possible to substitue a
     * return clause. Example:
     * </p>
     *
     * <pre>
     * <code>long getStatus() {
     *   try {
     *     return 0;
     *   } catch (final IOException e) {
     *     throw PrimitiveFailed.andTransferToInterpreter();
     *      // no unreachable return statement required.
     *   }
     * }
     * </code>
     * </pre>
     */
    public static final class PrimitiveFailed extends AbstractPrimitiveFailed {
        private static final long serialVersionUID = 1L;

        public PrimitiveFailed() {
            this(ERROR_TABLE.GENERIC_ERROR);
        }

        public PrimitiveFailed(final int reasonCode) {
            super(reasonCode);
        }

        public static PrimitiveFailed andTransferToInterpreter() {
            CompilerDirectives.transferToInterpreter();
            throw new PrimitiveFailed();
        }

        public static PrimitiveFailed andTransferToInterpreter(final int reason) {
            CompilerDirectives.transferToInterpreter();
            throw new PrimitiveFailed(reason);
        }
    }

    public static final class SimulationPrimitiveFailed extends AbstractPrimitiveFailed {
        private static final long serialVersionUID = 1L;

        public SimulationPrimitiveFailed(final int reasonCode) {
            super(reasonCode);
        }
    }

    private PrimitiveExceptions() {
    }
}
