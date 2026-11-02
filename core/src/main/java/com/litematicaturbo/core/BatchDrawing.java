package com.litematicaturbo.core;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Whether batch drawing (several built pieces of the schematic drawn with one call) is on, the switch-off after an
 * error (off for the session, the player is told once in the chat, the log gets the details, pieces are drawn one by
 * one again), and the draw-call counters the tests read.
 */
public final class BatchDrawing
{
    private static volatile String failure;
    private static volatile boolean warned;
    private static volatile boolean faultForTest;
    private static volatile boolean pausedForTest;
    private static volatile String pausedBy;
    private static volatile boolean pauseWarned;
    private static volatile long drawCalls;
    private static volatile long frames;
    private static volatile long batchedRegions;

    private BatchDrawing()
    {
    }

    public static boolean active()
    {
        return TurboConfig.get().batchDrawEnabled() && failure == null && pausedBy == null;
    }

    /**
     * Asked once a frame: what, if anything, draws the world in a way batch drawing cannot follow (a shader pack). While
     * there is one, batch drawing is paused (pieces are drawn one by one) and comes back by itself when it is gone.
     */
    public static void pauseFor(String who)
    {
        if (who == null ? pausedBy != null : who.equals(pausedBy) == false)
        {
            pausedBy = who;
            pauseWarned = false;

            if (who != null)
            {
                TurboLog.warn("Batch drawing is paused while shaders from " + who + " are on: they draw the schematic differently. Pieces are drawn one by one until they are off.");
            }
        }
    }

    /** The one chat message after batch drawing was paused by something; null when none is due. */
    public static String takePauseWarning(boolean playerPresent)
    {
        String who = pausedBy;

        if (pauseWarned == false && who != null && playerPresent)
        {
            pauseWarned = true;
            return who;
        }

        return null;
    }

    /** One draw call of the schematic's block layers was issued. */
    public static void countDrawCall()
    {
        drawCalls++;
    }

    /** The schematic was drawn once more (its first block layer was reached). */
    public static void countFrame()
    {
        frames++;
    }

    /** A region buffer was made. */
    public static void countRegion()
    {
        batchedRegions++;
    }

    /** All draw calls of the schematic's block layers so far (for the tests). */
    public static long drawCalls()
    {
        return drawCalls;
    }

    /** All frames the schematic was drawn in so far (for the tests). */
    public static long frames()
    {
        return frames;
    }

    /** All region buffers made so far (for the tests). */
    public static long batchedRegions()
    {
        return batchedRegions;
    }

    /** Tests draw frames piece by piece without giving the region buffers back, to compare them with batched frames. */
    public static void setPausedForTest(boolean paused)
    {
        pausedForTest = paused;
    }

    public static boolean pausedForTest()
    {
        return pausedForTest;
    }

    /** Tests make the next batch drawing fail, as a bug in it would. */
    public static void setFaultForTest(boolean fault)
    {
        faultForTest = fault;
    }

    /** Throws when a test asked for a failing batch. */
    public static void checkFaultForTest()
    {
        if (faultForTest)
        {
            throw new IllegalStateException("test fault in the batch drawing");
        }
    }

    /** Back to the start of a session (tests only). */
    public static void resetForTest()
    {
        failure = null;
        warned = false;
        faultForTest = false;
        pausedForTest = false;
        pausedBy = null;
        pauseWarned = false;
    }

    /** Batch drawing failed: it is off for the session; the caller draws the pieces one by one. */
    public static void switchOff(String call, Throwable t)
    {
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        TurboLog.error("Batch drawing failed in " + call + " and is off for this session; schematic pieces are drawn one by one:\n" + trace);
        failure = t.toString();
    }

    public static String failure()
    {
        return failure;
    }

    /** The reason for the one chat message after batch drawing switched itself off; null when none is due. */
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
