package com.litematicaturbo.core;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * The complete copy shown while a placement moves. Litematica's pieces leave out the blocks that already match the world,
 * so the old pieces drawn shifted (the ghosts) have holes. When a move starts, a second set of pieces is built in the
 * background from every schematic block, nearest pieces first, within a memory limit; while moving, a finished complete
 * piece is drawn in place of its ghost. The game side (CompletePieces) does the building and drawing; this class holds
 * the rules: the memory limits, when the copy is skipped or dropped, the switch-off after an error (the player is told
 * once in the chat, the log gets the details, the ghosts show as before) and the counters the tests read.
 * Render thread only.
 */
public final class CompleteCopy
{
    /** How long a move may wait for the complete copy's last builds before the real rebuild starts anyway. */
    public static final long MAX_HOLD_NANOS = 3_000_000_000L;
    /** Pieces handed to the build threads at a time. */
    public static final int IN_FLIGHT = 8;
    /** What a built block takes on the graphics card, estimated high (a few faces of 4 vertices at 32 bytes). */
    public static final long BYTES_PER_BLOCK = 256;
    public static final long MIN_JAVA_BUDGET = 64L * 1024 * 1024;
    /** The copy is dropped (and not made) while the card reports less free graphics memory than this. */
    public static final long MIN_GPU_FREE_BYTES = 256L * 1024 * 1024;
    public static final long MEMORY_CHECK_NANOS = 500_000_000L;
    private static final long NOT_SET = Long.MIN_VALUE;

    private static volatile String failure;
    private static volatile boolean warned;
    private static volatile boolean faultForTest;
    private static volatile long gpuFreeKbForTest = NOT_SET;
    private static volatile int made;
    private static volatile int skippedLowMemory;
    private static volatile int droppedTight;

    private CompleteCopy()
    {
    }

    public static boolean active()
    {
        return TurboConfig.get().completeCopyEnabled() && failure == null;
    }

    /** What the built blocks of a piece take on the card. */
    public static long estimateBytes(long blocks)
    {
        return blocks * BYTES_PER_BLOCK;
    }

    /** An eighth of Java's memory, at least {@link #MIN_JAVA_BUDGET}. */
    public static long javaBudget(long maxMemory)
    {
        return Math.max(MIN_JAVA_BUDGET, maxMemory / 8);
    }

    /** A quarter of the free graphics memory as the card reports it (KB); no limit of its own when it does not (below 0). */
    public static long gpuBudget(long gpuFreeKb)
    {
        return gpuFreeKb < 0 ? Long.MAX_VALUE : gpuFreeKb * 1024 / 4;
    }

    /** The limit for a copy made now: the smaller of the Java side and the graphics side. */
    public static long budget(long maxMemory, long gpuFreeKb)
    {
        return Math.min(javaBudget(maxMemory), gpuBudget(gpuFreeKb));
    }

    /** Whether a piece of this many bytes may be kept: it must fit in what is left of the limit. */
    public static boolean fits(long usedBytes, long pieceBytes, long budgetBytes)
    {
        return usedBytes + pieceBytes <= budgetBytes;
    }

    public static boolean gpuLow(long gpuFreeKb)
    {
        return gpuFreeKb >= 0 && gpuFreeKb * 1024 < MIN_GPU_FREE_BYTES;
    }

    /** Java memory or graphics memory is tight: no copy is made, and a made one is dropped. */
    public static boolean memoryTight(long gpuFreeKb)
    {
        Runtime rt = Runtime.getRuntime();
        return FarDetail.javaMemoryLow(MemoryBudget.maxMemory(), rt.totalMemory() - rt.freeMemory()) || gpuLow(gpuFreeKb);
    }

    /** The free graphics memory (KB) to judge by: what the card reported, or what a test pretends. */
    public static long gpuFreeKb(long reportedKb)
    {
        long pretended = gpuFreeKbForTest;
        return pretended == NOT_SET ? reportedKb : pretended;
    }

    public static void noteMade()
    {
        made++;
    }

    public static void noteSkippedLowMemory()
    {
        skippedLowMemory++;
    }

    public static void noteDroppedTight()
    {
        droppedTight++;
    }

    public static int made()
    {
        return made;
    }

    public static int skippedLowMemory()
    {
        return skippedLowMemory;
    }

    public static int droppedTight()
    {
        return droppedTight;
    }

    /** Tests make the next step of the copy fail, as a bug in it would. */
    public static void setFaultForTest(boolean fault)
    {
        faultForTest = fault;
    }

    public static void checkFaultForTest()
    {
        if (faultForTest)
        {
            throw new IllegalStateException("test fault in the complete copy");
        }
    }

    /** Tests pretend the card reports this much free memory (KB, below 0 = unknown); {@link #resetForTest} ends it. */
    public static void setGpuFreeKbForTest(long kb)
    {
        gpuFreeKbForTest = kb;
    }

    /** Back to the start of a session (tests only). */
    public static void resetForTest()
    {
        failure = null;
        warned = false;
        faultForTest = false;
        gpuFreeKbForTest = NOT_SET;
        made = 0;
        skippedLowMemory = 0;
        droppedTight = 0;
        MemoryBudget.setMaxMemoryForTest(0);
    }

    /** The copy failed: it is off for the session; the caller drops it and the ghosts are drawn as before. */
    public static void switchOff(String call, Throwable t)
    {
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        TurboLog.error("Complete copy while moving failed in " + call + " and is off for this session; the moved placement shows the normal ghosts:\n" + trace);
        failure = t.toString();
    }

    public static String failure()
    {
        return failure;
    }

    /** The reason for the one chat message after the copy switched itself off; null when none is due. */
    public static String takeFailureWarning(boolean playerPresent)
    {
        if (warned == false && failure != null && playerPresent)
        {
            warned = true;
            return failure;
        }

        return null;
    }
}
