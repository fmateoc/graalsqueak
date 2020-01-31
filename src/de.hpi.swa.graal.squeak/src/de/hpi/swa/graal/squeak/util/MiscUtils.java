/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.util;

import java.io.File;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakInterrupt;
import sun.misc.GC;
import sun.misc.JavaLangRefAccess;
import sun.misc.SharedSecrets;

public final class MiscUtils {
    private static final CompilationMXBean COMPILATION_BEAN = ManagementFactory.getCompilationMXBean();
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    private static final RuntimeMXBean RUNTIME_BEAN = ManagementFactory.getRuntimeMXBean();
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

    // number of sleeps with exponentially increasing delay before giving up on waiting for the gc
    // to happen:
    // 1, 2, 4, 8, 16, 32, 64, 128, 256 (total 511 ms ~ 0.5 s)
    private static final int MAX_SLEEPS = 9;

    // The delta between Squeak Epoch (January 1st 1901) and POSIX Epoch (January 1st 1970)
    public static final long EPOCH_DELTA_SECONDS = (69L * 365 + 17) * 24 * 3600;
    public static final long EPOCH_DELTA_MICROSECONDS = EPOCH_DELTA_SECONDS * 1000 * 1000;
    public static final long TIME_ZONE_OFFSET_MICROSECONDS = (Calendar.getInstance().get(Calendar.ZONE_OFFSET) + Calendar.getInstance().get(Calendar.DST_OFFSET)) * 1000L;
    public static final long TIME_ZONE_OFFSET_SECONDS = TIME_ZONE_OFFSET_MICROSECONDS / 1000 / 1000;

    public static final Random RANDOM = new Random();

    private MiscUtils() {
    }

    public static int bitSplit(final long value, final int offset, final int size) {
        return (int) (value >> offset & size - 1);
    }

    /** Ceil version of {@link Math#floorDiv(int, int)}. */
    public static int ceilDiv(final int x, final int y) {
        int r = x / y;
        // if the signs are the same and modulo not zero, round up
        if ((x ^ y) >= 0 && r * y != x) {
            r++;
        }
        return r;
    }

    @TruffleBoundary
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @TruffleBoundary
    public static String format(final String format, final Object... args) {
        return String.format(format, args);
    }

    @TruffleBoundary
    public static long getCollectionCount() {
        long totalCollectionCount = 0;
        for (final GarbageCollectorMXBean gcBean : GC_BEANS) {
            totalCollectionCount += Math.max(gcBean.getCollectionCount(), 0);
        }
        return totalCollectionCount;
    }

    @TruffleBoundary
    public static long getCollectionTime() {
        long totalCollectionTime = 0;
        for (final GarbageCollectorMXBean gcBean : GC_BEANS) {
            totalCollectionTime += Math.max(gcBean.getCollectionTime(), 0);
        }
        return totalCollectionTime;
    }

    public static void gc() {
        final JavaLangRefAccess jlra = SharedSecrets.getJavaLangRefAccess();
        final long previousInspectionAge = GC.maxObjectInspectionAge();
        final long start = System.nanoTime();
        // retry while helping enqueue pending Reference objects
        // which includes executing pending Cleaner(s) which includes
        // Cleaner(s) that free direct buffer memory
        while (jlra.tryHandlePendingReference()) {
            if (GC.maxObjectInspectionAge() < previousInspectionAge) {
                return;
            }
        }
        // trigger VM's Reference processing
        System.gc();
        final long gcDuration = (System.nanoTime() - start) / 1000;

        // a retry loop with exponential back-off delays
        // (this gives VM some time to do it's job)
        boolean interrupted = false;
        try {
            long sleepTime = 1;
            int sleeps = 0;
            while (true) {
                if (GC.maxObjectInspectionAge() < previousInspectionAge + gcDuration) {
                    System.out.println("Successfully triggered a garbage collect");
                    return;
                }
                if (sleeps >= MAX_SLEEPS) {
                    break;
                }
                if (!jlra.tryHandlePendingReference()) {
                    try {
                        Thread.sleep(sleepTime);
                        sleepTime <<= 1;
                        sleeps++;
                    } catch (final InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            // no luck
            DebugUtils.forceGcWithHistogram();

        } finally {
            if (interrupted) {
                // don't swallow interrupts
                Thread.currentThread().interrupt();
            }
        }
    }

    @TruffleBoundary
    public static String getGraalVMInformation() {
        final String graalVMVersion = System.getProperty("graalvm.version", "");
        if (graalVMVersion.isEmpty()) {
            return ""; // No information available; not running on GraalVM.
        }
        final String graalVMHome = System.getProperty("graalvm.home", "n/a");
        return String.format("GRAAL_VERSION=%s\nGRAAL_HOME=%s", graalVMVersion, graalVMHome);
    }

    @TruffleBoundary
    public static long getHeapMemoryMax() {
        return MEMORY_BEAN.getHeapMemoryUsage().getMax();
    }

    @TruffleBoundary
    public static long getHeapMemoryUsed() {
        return MEMORY_BEAN.getHeapMemoryUsage().getUsed();
    }

    @TruffleBoundary
    public static String getJavaClassPath() {
        return System.getProperty("java.class.path");
    }

    @TruffleBoundary
    public static String getJavaHome() {
        return System.getProperty("java.home");
    }

    @TruffleBoundary
    public static long getObjectPendingFinalizationCount() {
        return MEMORY_BEAN.getObjectPendingFinalizationCount();
    }

    @TruffleBoundary
    public static long getStartTime() {
        return RUNTIME_BEAN.getStartTime();
    }

    @TruffleBoundary
    public static String getSystemProperties() {
        final Properties properties = System.getProperties();
        final StringBuilder sb = new StringBuilder();
        sb.append("\n\n== System Properties =================================>\n");
        final Object[] keys = properties.keySet().toArray();
        Arrays.sort(keys);
        for (final Object systemKey : keys) {
            final String key = (String) systemKey;
            sb.append(String.format("%s = %s\n", key, System.getProperty(key, "n/a")));
        }
        sb.append("<= System Properties ===================================\n\n");
        return sb.toString();
    }

    @TruffleBoundary
    public static long getTotalCompilationTime() {
        if (COMPILATION_BEAN.isCompilationTimeMonitoringSupported()) {
            return COMPILATION_BEAN.getTotalCompilationTime();
        } else {
            return -1L;
        }
    }

    @TruffleBoundary
    public static long getUptime() {
        return RUNTIME_BEAN.getUptime();
    }

    @TruffleBoundary
    public static String getVMInformation() {
        return String.format("\n%s (%s; %s)\n", System.getProperty("java.vm.name"), System.getProperty("java.vm.version"), System.getProperty("java.vm.info"));
    }

    @TruffleBoundary
    public static String getVMPath() {
        final String binaryName = OSDetector.SINGLETON.isWindows() ? "java.exe" : "java";
        return System.getProperty("java.home") + File.separatorChar + "bin" + File.separatorChar + binaryName;
    }

    @TruffleBoundary
    public static long runtimeFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    @TruffleBoundary
    public static long runtimeMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    @TruffleBoundary
    public static long runtimeTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    @TruffleBoundary
    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SqueakInterrupt();
        }
    }

    @TruffleBoundary
    public static byte[] stringToBytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @TruffleBoundary
    public static int[] stringToCodePointsArray(final String value) {
        return value.codePoints().toArray();
    }

    @TruffleBoundary
    public static String stringValueOf(final char value) {
        return String.valueOf(value);
    }

    @TruffleBoundary
    public static void systemGC() {
        System.gc();
    }

    @TruffleBoundary
    public static byte[] toBytes(final String value) {
        return value.getBytes();
    }

    public static long toJavaMicrosecondsUTC(final long microseconds) {
        return microseconds - EPOCH_DELTA_MICROSECONDS;
    }

    public static long toSqueakMicrosecondsLocal(final long microseconds) {
        return toSqueakMicrosecondsUTC(microseconds) + TIME_ZONE_OFFSET_MICROSECONDS;
    }

    public static long toSqueakMicrosecondsUTC(final long microseconds) {
        return microseconds + EPOCH_DELTA_MICROSECONDS;
    }

    public static long toSqueakSecondsLocal(final long seconds) {
        return seconds + EPOCH_DELTA_SECONDS + TIME_ZONE_OFFSET_SECONDS;
    }

    @TruffleBoundary
    public static String toString(final Object value) {
        return value.toString();
    }
}
