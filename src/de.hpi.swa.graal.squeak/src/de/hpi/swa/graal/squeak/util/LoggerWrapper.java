/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.util;

import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;

import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;

/**
 * Logging infrastructure for GraalSqueak. All loggers are consistently defined here, so that it is
 * clear which loggers are available.
 */
public abstract class LoggerWrapper {

    public enum Name {
        CONTEXT_STACK,
        GC,
        INTEROP,
        INTERRUPTS,
        IO,
        ITERATE_FRAMES,
        PRIMITIVES,
        SCHEDULING,
        STARTUP,
        TESTING;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    private static final LoggerWrapper NULL = new NullWrapper();
    private static ThreadLocal<Map<Name, TruffleLoggerWrapper>> loggers = ThreadLocal.withInitial(HashMap::new);

    public static LoggerWrapper get(final Name loggerName, final Level level) {
        final TruffleLogger logger = TruffleLogger.getLogger(SqueakLanguageConfig.ID, loggerName.toString());
        if (logger.isLoggable(level)) {
            final Map<Name, TruffleLoggerWrapper> map = loggers.get();
            final TruffleLoggerWrapper w = map.get(loggerName);
            return w != null ? w : map.put(loggerName, new TruffleLoggerWrapper(logger));
        } else {
            return NULL;
        }
    }

    private static final class NullWrapper extends LoggerWrapper {

        @Override
        public boolean config(final String message) {
            return true;
        }

        @Override
        public boolean config(final Supplier<String> stringSupplier) {
            return true;
        }

        @Override
        public boolean config(final String pattern, final Object... argv) {
            return true;
        }

        @Override
        public boolean config(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            return true;
        }

        @Override
        public boolean fine(final String message) {
            return true;
        }

        @Override
        public boolean fine(final Supplier<String> stringSupplier) {
            return true;
        }

        @Override
        public boolean fine(final String pattern, final Object... argv) {
            return true;
        }

        @Override
        public boolean fine(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            return true;
        }

        @Override
        public boolean finer(final String message) {
            return true;
        }

        @Override
        public boolean finer(final Supplier<String> stringSupplier) {
            return true;
        }

        @Override
        public boolean finer(final String pattern, final Object... argv) {
            return true;
        }

        @Override
        public boolean finer(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            return true;
        }

        @Override
        public boolean finest(final String message) {
            return true;
        }

        @Override
        public boolean finest(final Supplier<String> stringSupplier) {
            return true;
        }

        @Override
        public boolean finest(final String pattern, final Object... argv) {
            return true;
        }

        @Override
        public boolean finest(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            return true;
        }

        @Override
        public boolean info(final String message) {
            return true;
        }

        @Override
        public boolean info(final Supplier<String> stringSupplier) {
            return true;
        }

        @Override
        public boolean info(final String pattern, final Object... argv) {
            return true;
        }

        @Override
        public boolean info(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            return true;
        }

        @Override
        public boolean severe(final String message) {
            return true;
        }

        @Override
        public boolean severe(final Supplier<String> stringSupplier) {
            return true;
        }

        @Override
        public boolean severe(final String pattern, final Object... argv) {
            return true;
        }

        @Override
        public boolean severe(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            return true;
        }

        @Override
        public boolean warning(final String message) {
            return true;
        }

        @Override
        public boolean warning(final Supplier<String> stringSupplier) {
            return true;
        }

        @Override
        public boolean warning(final String pattern, final Object... argv) {
            return true;
        }

        @Override
        public boolean warning(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            return true;
        }

        @Override
        public boolean log(final Level level, final String string, final Throwable e) {
            return true;
        }

    }

    private static final class TruffleLoggerWrapper extends LoggerWrapper {
        private final TruffleLogger truffleLogger;
        private final ArgChain argChain = new ArgChain();
        private final StringSupplier1 stringSupplier1 = new StringSupplier1();
        private final StringSupplier2 stringSupplier2 = new StringSupplier2(argChain);

        public TruffleLoggerWrapper(final TruffleLogger truffleLogger) {
            this.truffleLogger = truffleLogger;
        }

        @Override
        public boolean config(final String message) {
            truffleLogger.config(message);
            return true;
        }

        @Override
        public boolean config(final Supplier<String> stringSupplier) {
            truffleLogger.config(stringSupplier);
            return true;
        }

        @Override
        public boolean config(final String pattern, final Object... argv) {
            try {
                truffleLogger.config(stringSupplier1.set(pattern, argv));
            } finally {
                stringSupplier1.set(null, (Object[]) null);
            }
            return true;
        }

        @Override
        public boolean config(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            try {
                truffleLogger.config(stringSupplier2.set(pattern, argChainConsumer));
            } finally {
                stringSupplier2.set(null, null);
            }
            return true;
        }

        @Override
        public boolean fine(final String message) {
            truffleLogger.fine(message);
            return true;
        }

        @Override
        public boolean fine(final Supplier<String> stringSupplier) {
            truffleLogger.fine(stringSupplier);
            return true;
        }

        @Override
        public boolean fine(final String pattern, final Object... argv) {
            try {
                truffleLogger.fine(stringSupplier1.set(pattern, argv));
            } finally {
                stringSupplier1.set(null, (Object[]) null);
            }
            return true;
        }

        @Override
        public boolean fine(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            try {
                truffleLogger.fine(stringSupplier2.set(pattern, argChainConsumer));
            } finally {
                stringSupplier2.set(null, null);
            }
            return true;
        }

        @Override
        public boolean finer(final String message) {
            truffleLogger.finer(message);
            return true;
        }

        @Override
        public boolean finer(final Supplier<String> stringSupplier) {
            truffleLogger.finer(stringSupplier);
            return true;
        }

        @Override
        public boolean finer(final String pattern, final Object... argv) {
            try {
                truffleLogger.finer(stringSupplier1.set(pattern, argv));
            } finally {
                stringSupplier1.set(null, (Object[]) null);
            }
            return true;
        }

        @Override
        public boolean finer(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            try {
                truffleLogger.finer(stringSupplier2.set(pattern, argChainConsumer));
            } finally {
                stringSupplier2.set(null, null);
            }
            return true;
        }

        @Override
        public boolean finest(final String message) {
            truffleLogger.finest(message);
            return true;
        }

        @Override
        public boolean finest(final Supplier<String> stringSupplier) {
            truffleLogger.finest(stringSupplier);
            return true;
        }

        @Override
        public boolean finest(final String pattern, final Object... argv) {
            try {
                truffleLogger.finest(stringSupplier1.set(pattern, argv));
            } finally {
                stringSupplier1.set(null, (Object[]) null);
            }
            return true;
        }

        @Override
        public boolean finest(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            try {
                truffleLogger.finest(stringSupplier2.set(pattern, argChainConsumer));
            } finally {
                stringSupplier2.set(null, null);
            }
            return true;
        }

        @Override
        public boolean info(final String message) {
            truffleLogger.info(message);
            return true;
        }

        @Override
        public boolean info(final Supplier<String> stringSupplier) {
            truffleLogger.info(stringSupplier);
            return true;
        }

        @Override
        public boolean info(final String pattern, final Object... argv) {
            try {
                truffleLogger.info(stringSupplier1.set(pattern, argv));
            } finally {
                stringSupplier1.set(null, (Object[]) null);
            }
            return true;
        }

        @Override
        public boolean info(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            try {
                truffleLogger.info(stringSupplier2.set(pattern, argChainConsumer));
            } finally {
                stringSupplier2.set(null, null);
            }
            return true;
        }

        @Override
        public boolean severe(final String message) {
            truffleLogger.severe(message);
            return true;
        }

        @Override
        public boolean severe(final Supplier<String> stringSupplier) {
            truffleLogger.severe(stringSupplier);
            return true;
        }

        @Override
        public boolean severe(final String pattern, final Object... argv) {
            try {
                truffleLogger.severe(stringSupplier1.set(pattern, argv));
            } finally {
                stringSupplier1.set(null, (Object[]) null);
            }
            return true;
        }

        @Override
        public boolean severe(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            try {
                truffleLogger.severe(stringSupplier2.set(pattern, argChainConsumer));
            } finally {
                stringSupplier2.set(null, null);
            }
            return true;
        }

        @Override
        public boolean warning(final String message) {
            truffleLogger.warning(message);
            return true;
        }

        @Override
        public boolean warning(final Supplier<String> stringSupplier) {
            truffleLogger.warning(stringSupplier);
            return true;
        }

        @Override
        public boolean warning(final String pattern, final Object... argv) {
            try {
                truffleLogger.warning(stringSupplier1.set(pattern, argv));
            } finally {
                stringSupplier1.set(null, (Object[]) null);
            }
            return true;
        }

        @Override
        public boolean warning(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            try {
                truffleLogger.warning(stringSupplier2.set(pattern, argChainConsumer));
            } finally {
                stringSupplier2.set(null, null);
            }
            return true;
        }

        @Override
        public boolean log(final Level level, final String string, final Throwable e) {
            truffleLogger.log(level, string, e);
            return true;
        }

        private static final class StringSupplier1 implements Supplier<String> {
            private String pattern;
            private Object[] argv;

            StringSupplier1 set(final String pattern, final Object... argv) {
                this.pattern = pattern;
                this.argv = argv;
                return this;
            }

            @Override
            public String get() {
                return LoggerWrapper.safeFormat(pattern, argv);
            }
        }

        private static final class StringSupplier2 implements Supplier<String> {
            private final ArgChain argChain;
            private String pattern;
            private Consumer<ArgChain> argChainConsumer;

            public StringSupplier2(final ArgChain argChain) {
                this.argChain = argChain;
            }

            StringSupplier2 set(final String pattern, final Consumer<ArgChain> argChainConsumer) {
                this.pattern = pattern;
                this.argChainConsumer = argChainConsumer;
                return this;
            }

            @Override
            public String get() {
                return argChain.safeFormat(pattern, argChainConsumer);
            }
        }
    }

    public static final class ArgChain {
        private static final int MAX_ARGS = 16;

        private int argc;
        private Object[] argv = new Object[MAX_ARGS];

        public ArgChain add(final Object arg) {
            if (argc == MAX_ARGS)
                throw new TooManyArgsException("Number of args cannot exceed " + MAX_ARGS);
            argv[argc++] = arg;
            return this;
        }

        String safeFormat(final String pattern, final Consumer<ArgChain> argChainConsumer) {
            assert argc == 0;
            argChainConsumer.accept(this);
            assert argc != 0;
            try {
                return LoggerWrapper.safeFormat(pattern, argv);
            } finally {
                for (int i = 0; i < argc; i++) {
                    argv[i] = null;
                }
                argc = 0;
            }
        }

        public static final class TooManyArgsException extends IllegalStateException {
            private static final long serialVersionUID = 1L;

            TooManyArgsException(final String m) {
                super(m);
            }
        }
    }

    static String safeFormat(final String pattern, final Object... argv) {
        String message = null;
        try {
            message = String.format(pattern, argv);
        } catch (final IllegalFormatException e) {
            message = e + " for format string \"" + pattern + "\"";
        }
        return message;
    }

    public abstract boolean config(String message);

    public abstract boolean config(Supplier<String> stringSupplier);

    public abstract boolean config(String pattern, Object... argv);

    public abstract boolean config(String pattern, Consumer<ArgChain> argChainConsumer);

    public abstract boolean fine(String message);

    public abstract boolean fine(Supplier<String> stringSupplier);

    public abstract boolean fine(String pattern, Object... argv);

    public abstract boolean fine(String pattern, Consumer<ArgChain> argChainConsumer);

    public abstract boolean finer(String message);

    public abstract boolean finer(Supplier<String> stringSupplier);

    public abstract boolean finer(String pattern, Object... argv);

    public abstract boolean finer(String pattern, Consumer<ArgChain> argChainConsumer);

    public abstract boolean finest(String message);

    public abstract boolean finest(Supplier<String> stringSupplier);

    public abstract boolean finest(String pattern, Object... argv);

    public abstract boolean finest(String pattern, Consumer<ArgChain> argChainConsumer);

    public abstract boolean info(String message);

    public abstract boolean info(Supplier<String> stringSupplier);

    public abstract boolean info(String pattern, Object... argv);

    public abstract boolean info(String pattern, Consumer<ArgChain> argChainConsumer);

    public abstract boolean severe(String message);

    public abstract boolean severe(Supplier<String> stringSupplier);

    public abstract boolean severe(String pattern, Object... argv);

    public abstract boolean severe(String pattern, Consumer<ArgChain> argChainConsumer);

    public abstract boolean warning(String message);

    public abstract boolean warning(Supplier<String> stringSupplier);

    public abstract boolean warning(String pattern, Object... argv);

    public abstract boolean warning(String pattern, Consumer<ArgChain> argChainConsumer);

    public abstract boolean log(Level level, String string, Throwable e);

}
