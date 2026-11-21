package com.litematicaturbo.core;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Less memory for see-through sorting: Litematica keeps, for every built piece of the schematic, a copy of its
 * see-through quads (translucent blocks, overlay sides) to sort them again when the camera moves, though only pieces
 * near the camera are ever sorted again. A piece keeps its copies while its centre is within {@link #KEEP_BLOCKS} of the
 * camera and drops them past {@link #DROP_BLOCKS} (in between nothing changes); a piece whose copies were dropped is
 * built again once it comes within {@link #KEEP_BLOCKS}. Where Litematica never sorts again (1.16.5 - 1.18.2 with
 * Turbo's build threads) every copy is dropped and nothing is rebuilt. The sweep runs when the camera moved
 * {@link #MOVE_BLOCKS} since the last one, and at most once a {@link #BUILD_SWEEP_NANOS} while builds are landing.
 * Off for the session after an error (the player is told once in the chat, the log gets the details, every dropped piece
 * is built again). Also the counters the tests read. Render thread only.
 */
public final class SortCopies
{
    public static final double KEEP_BLOCKS = 48.0;
    public static final double DROP_BLOCKS = 64.0;
    public static final double MOVE_BLOCKS = 16.0;
    public static final long BUILD_SWEEP_NANOS = 1_000_000_000L;

    private static volatile String failure;
    private static volatile boolean warned;
    private static volatile boolean faultForTest;
    private static boolean swept;
    private static double sweptX;
    private static double sweptY;
    private static double sweptZ;
    private static long sweptBuilds;
    private static long sweptAt;
    private static volatile long dropped;
    private static volatile long rebuilt;

    private SortCopies()
    {
    }

    public static boolean active()
    {
        return TurboConfig.get().sortCopiesEnabled() && failure == null;
    }

    /** Whether a piece this far (squared blocks) from the camera loses its copies. */
    public static boolean drops(double distanceSq, boolean resorting)
    {
        return resorting == false || distanceSq > DROP_BLOCKS * DROP_BLOCKS;
    }

    /** Whether a piece this far (squared blocks) from the camera, whose copies were dropped, is built again. */
    public static boolean rebuilds(double distanceSq, boolean resorting)
    {
        return resorting && distanceSq <= KEEP_BLOCKS * KEEP_BLOCKS;
    }

    /**
     * Whether a sweep is due: none yet, the camera moved {@link #MOVE_BLOCKS} since the last one, or builds finished since
     * then and the last one is {@link #BUILD_SWEEP_NANOS} old.
     */
    public static boolean sweepDue(double x, double y, double z, long builds, long nanos)
    {
        if (swept == false)
        {
            return true;
        }

        double dx = x - sweptX;
        double dy = y - sweptY;
        double dz = z - sweptZ;

        return dx * dx + dy * dy + dz * dz >= MOVE_BLOCKS * MOVE_BLOCKS || (builds != sweptBuilds && nanos - sweptAt >= BUILD_SWEEP_NANOS);
    }

    public static void swept(double x, double y, double z, long builds, long nanos)
    {
        swept = true;
        sweptX = x;
        sweptY = y;
        sweptZ = z;
        sweptBuilds = builds;
        sweptAt = nanos;
    }

    /** The next sweep runs at once (a reloaded schematic, a test). */
    public static void sweepSoon()
    {
        swept = false;
    }

    public static void countDropped()
    {
        dropped++;
    }

    public static void countRebuilt()
    {
        rebuilt++;
    }

    /** Pieces whose copies were dropped so far (for the tests). */
    public static long dropped()
    {
        return dropped;
    }

    /** Pieces built again for their copies so far (for the tests). */
    public static long rebuilt()
    {
        return rebuilt;
    }

    /** Tests make the next sweep fail, as a bug in it would. */
    public static void setFaultForTest(boolean fault)
    {
        faultForTest = fault;
    }

    public static void checkFaultForTest()
    {
        if (faultForTest)
        {
            throw new IllegalStateException("test fault in the see-through sort copy sweep");
        }
    }

    /** Back to the start of a session (tests only). */
    public static void resetForTest()
    {
        failure = null;
        warned = false;
        faultForTest = false;
        swept = false;
    }

    /** The sweep failed: it is off for the session; the caller gives every dropped piece back to Litematica. */
    public static void switchOff(String call, Throwable t)
    {
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        TurboLog.error("Less memory for see-through sorting failed in " + call + " and is off for this session; dropped pieces are built again:\n" + trace);
        failure = t.toString();
    }

    public static String failure()
    {
        return failure;
    }

    /** The reason for the one chat message after the sweep switched itself off; null when none is due. */
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
