package de.hpi.swa.graal.squeak.exceptions;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ControlFlowException;

import de.hpi.swa.graal.squeak.model.ObjectLayouts.ERROR_TABLE;

public final class PrimitiveExceptions {

    protected static class AbstractPrimitiveFailed extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        private final long reasonCode;

        protected AbstractPrimitiveFailed(final long reasonCode) {
            this.reasonCode = reasonCode;
        }

        public final long getReasonCode() {
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
        private final Object[] suppliedValues;

        public PrimitiveFailed(final Object... values) {
            super(ERROR_TABLE.GENERIC_ERROR);
            suppliedValues = values;
        }

        public PrimitiveFailed() {
            this(ERROR_TABLE.GENERIC_ERROR);
        }

        public PrimitiveFailed(final long reasonCode) {
            super(reasonCode);
            suppliedValues = null;
        }

        public static PrimitiveFailed andTransferToInterpreter() {
            CompilerDirectives.transferToInterpreter();
            throw new PrimitiveFailed();
        }

        public static PrimitiveFailed andTransferToInterpreter(final long reason) {
            CompilerDirectives.transferToInterpreter();
            throw new PrimitiveFailed(reason);
        }

        @Override
        public String getMessage() {
            if (suppliedValues == null) {
                return super.getMessage();
            }
            final StringBuilder str = new StringBuilder();
            str.append(Arrays.toString(suppliedValues)).append(", [");
            for (int i = 0; i < suppliedValues.length; i++) {
                str.append(i == 0 ? "" : ",").append(suppliedValues[i] == null ? "null" : suppliedValues[i].getClass().getSimpleName());
            }
            return str.append("]").toString();
        }
    }

    public static final class SimulationPrimitiveFailed extends AbstractPrimitiveFailed {
        private static final long serialVersionUID = 1L;

        public SimulationPrimitiveFailed(final long reasonCode) {
            super(reasonCode);
        }
    }

    public static class PrimitiveWithoutResultException extends ControlFlowException {
        private static final long serialVersionUID = 1L;
    }

    private PrimitiveExceptions() {
    }
}
