package com.litematicaturbo.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters for schematic chunk builds, shown in F3 so before/after numbers can be compared.
 * Thread-safe; updated from build workers and the render thread.
 */
public final class BuildStats
{
    private static final AtomicLong builds = new AtomicLong();
    private static final AtomicLong buildNanos = new AtomicLong();
    private static final AtomicLong mainThreadNanos = new AtomicLong();
    private static final AtomicLong sectionsScanned = new AtomicLong();
    private static final AtomicLong sectionsSkipped = new AtomicLong();

    public static final AtomicLong placeCalls = new AtomicLong();
    public static final AtomicLong placeCallsFast = new AtomicLong();
    public static final AtomicLong placeCallsSkipped = new AtomicLong();

    private static long windowStart = System.nanoTime();
    private static long windowBuilds;
    private static long windowBuildNanos;
    private static long windowMainNanos;
    private static String summary = "";

    private BuildStats()
    {
    }

    public static void recordBuild(long nanos, boolean onMainThread)
    {
        builds.incrementAndGet();
        buildNanos.addAndGet(nanos);

        if (onMainThread)
        {
            mainThreadNanos.addAndGet(nanos);
        }
    }

    public static void recordSections(int scanned, int skipped)
    {
        sectionsScanned.addAndGet(scanned);
        sectionsSkipped.addAndGet(skipped);
    }

    public static long totalBuilds()
    {
        return builds.get();
    }

    public static long totalBuildNanos()
    {
        return buildNanos.get();
    }

    public static long totalMainThreadNanos()
    {
        return mainThreadNanos.get();
    }

    /**
     * One-line summary over the last ~second: builds per second, average build time,
     * render-thread milliseconds per second spent building, and the share of skipped slices.
     */
    public static synchronized String summary(int threads, int queued)
    {
        long now = System.nanoTime();
        long elapsed = now - windowStart;

        if (elapsed >= 1_000_000_000L || summary.isEmpty())
        {
            long b = builds.get();
            long bn = buildNanos.get();
            long mn = mainThreadNanos.get();
            long db = b - windowBuilds;
            double seconds = Math.max(elapsed, 1) / 1.0e9;
            double avgMs = db > 0 ? (bn - windowBuildNanos) / 1.0e6 / db : 0.0;
            double mainMsPerSec = (mn - windowMainNanos) / 1.0e6 / seconds;
            long scanned = sectionsScanned.get();
            long skipped = sectionsSkipped.get();
            int skipPct = scanned > 0 ? (int) (skipped * 100 / scanned) : 0;

            summary = String.format("Turbo T:%d Q:%d B/s:%.0f avg:%.1fms main:%.0fms/s skip:%d%%",
                                    threads, queued, db / seconds, avgMs, mainMsPerSec, skipPct);
            windowStart = now;
            windowBuilds = b;
            windowBuildNanos = bn;
            windowMainNanos = mn;
        }

        return summary;
    }
}
