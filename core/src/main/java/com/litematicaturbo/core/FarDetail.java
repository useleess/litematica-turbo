package com.litematicaturbo.core;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Less detail far away: pieces of the schematic at least half the render distance away (3 pieces at least) are drawn
 * without their outline lines, which there are only a blur of thin lines but cost as many vertices as near ones. Filled
 * overlay sides and the blocks are untouched. Off by itself while Java memory is low, and for the session after an error
 * (the player is told once in the chat, the log gets the details, every outline is drawn again). Also the counters the
 * tests read. Render thread only.
 */
public final class FarDetail
{
    private static final long MEMORY_CHECK_NANOS = 500_000_000L;

    private static volatile String failure;
    private static volatile boolean warned;
    private static volatile boolean faultForTest;
    private static long lastMemoryCheck;
    /** The maximum memory the last check used (a test pretending less memory, or ending that, re-checks at once). */
    private static long lastMaxMemory;
    private static boolean memoryLow;
    private static volatile long farSkipped;
    private static volatile long farDrawn;
    private static volatile long nearDrawn;

    private FarDetail()
    {
    }

    /** Pieces at least this many pieces away (horizontally) count as far: half the render distance, at least 3. */
    public static int farPieces(int renderDistanceChunks)
    {
        return Math.max(3, renderDistanceChunks / 2);
    }

    public static boolean isFar(int pieceX, int pieceZ, int cameraPieceX, int cameraPieceZ, int renderDistanceChunks)
    {
        int d = Math.max(Math.abs(pieceX - cameraPieceX), Math.abs(pieceZ - cameraPieceZ));
        return d >= farPieces(renderDistanceChunks);
    }

    /** Less than a quarter of the maximum memory is free. */
    public static boolean javaMemoryLow(long maxBytes, long usedBytes)
    {
        return maxBytes - usedBytes < maxBytes / 4;
    }

    public static boolean active()
    {
        return TurboConfig.get().farDetailEnabled() && failure == null && memoryLow() == false;
    }

    private static boolean memoryLow()
    {
        long now = System.nanoTime();
        long max = MemoryBudget.maxMemory();

        if (now - lastMemoryCheck > MEMORY_CHECK_NANOS || max != lastMaxMemory)
        {
            lastMemoryCheck = now;
            lastMaxMemory = max;
            Runtime rt = Runtime.getRuntime();
            memoryLow = javaMemoryLow(max, rt.totalMemory() - rt.freeMemory());
        }

        return memoryLow;
    }

    /**
     * Whether the outline lines of a piece (block coordinates of its corner) that has some are left out this frame;
     * counts the piece for the tests.
     */
    public static boolean hideOutlines(int blockX, int blockZ, double cameraX, double cameraZ, int renderDistanceChunks)
    {
        if (isFar(blockX >> 4, blockZ >> 4, (int) Math.floor(cameraX) >> 4, (int) Math.floor(cameraZ) >> 4, renderDistanceChunks) == false)
        {
            nearDrawn++;
            return false;
        }

        if (active())
        {
            checkFaultForTest();
            farSkipped++;
            return true;
        }

        farDrawn++;
        return false;
    }

    /** Outline draws of far pieces left out so far (for the tests). */
    public static long farSkipped()
    {
        return farSkipped;
    }

    /** Outline draws of far pieces made so far (for the tests). */
    public static long farDrawn()
    {
        return farDrawn;
    }

    /** Outline draws of near pieces made so far (for the tests). */
    public static long nearDrawn()
    {
        return nearDrawn;
    }

    /** Tests make the next far check fail, as a bug in it would. */
    public static void setFaultForTest(boolean fault)
    {
        faultForTest = fault;
    }

    private static void checkFaultForTest()
    {
        if (faultForTest)
        {
            throw new IllegalStateException("test fault in the far detail check");
        }
    }

    /** Back to the start of a session (tests only). */
    public static void resetForTest()
    {
        failure = null;
        warned = false;
        faultForTest = false;
        MemoryBudget.setMaxMemoryForTest(0);
    }

    /** The far check failed: it is off for the session; the caller draws every outline. */
    public static void switchOff(String call, Throwable t)
    {
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        TurboLog.error("Less detail far away failed in " + call + " and is off for this session; every outline is drawn:\n" + trace);
        failure = t.toString();
    }

    public static String failure()
    {
        return failure;
    }

    /** The reason for the one chat message after the far check switched itself off; null when none is due. */
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
