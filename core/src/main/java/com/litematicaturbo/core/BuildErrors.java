package com.litematicaturbo.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An error on one of Turbo's build threads. The build of that chunk is dropped (the chunk keeps its last finished
 * picture and is built again), the player is told once in the chat and the log gets every error with its stack.
 * Rebuilding stops after {@link #MAX_REBUILDS} drops in a session, so an error that repeats exactly cannot loop forever.
 */
public final class BuildErrors
{
    /** Drops after which a chunk is no longer queued for another build. */
    public static final int MAX_REBUILDS = 20;

    private static volatile int dropped;
    private static volatile String lastReason;
    private static volatile boolean warned;
    private static final AtomicBoolean FAULT_FOR_TEST = new AtomicBoolean();

    private BuildErrors()
    {
    }

    /** A build was dropped because of this error: counted and logged with its stack. */
    public static void drop(Throwable error)
    {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        TurboLog.warn("Dropped the build of a schematic chunk after an error on a build thread; it is built again: " + stack);
        lastReason = error.toString();
        ++dropped;
    }

    /** Whether the chunk of a dropped build should be queued again. */
    public static boolean mayRebuild()
    {
        return dropped <= MAX_REBUILDS;
    }

    /** How many builds were dropped so far (for the tests). */
    public static int dropped()
    {
        return dropped;
    }

    /** The reason for the one chat message after the first dropped build; null when none is due. */
    public static String takeWarning(boolean playerPresent)
    {
        if (warned == false && lastReason != null && playerPresent)
        {
            warned = true;
            return lastReason;
        }

        return null;
    }

    /** Tests make the next build on a build thread fail once, as a broken vertex writer would. */
    public static void armFaultForTest()
    {
        FAULT_FOR_TEST.set(true);
    }

    /** Called by a build thread before it builds: throws the armed test fault, once. */
    public static void checkFaultForTest()
    {
        if (FAULT_FOR_TEST.getAndSet(false))
        {
            throw new IllegalStateException("test fault in the chunk build");
        }
    }

    /** Back to the start of a session (tests only). */
    public static void resetForTest()
    {
        dropped = 0;
        lastReason = null;
        warned = false;
        FAULT_FOR_TEST.set(false);
    }
}
