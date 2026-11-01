package com.litematicaturbo.core;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * When Litematica's block storage of a schematic region is kept in sparse sections (see {@link SparseIntArray}), and the
 * switch-off after an error: the sparse code is then off for the session, the player is told once in the chat, the log
 * gets the details and schematics are stored the plain Litematica way.
 */
public final class CompactStorage
{
    /** Packed arrays with fewer longs than this stay dense: nothing worth saving. */
    public static final int MIN_WORDS = 1 << 16;
    /** The key of the red message shown when a file does not fit in memory. */
    public static final String OUT_OF_MEMORY_KEY = "litematica_turbo.message.out_of_memory";

    private static volatile boolean forceForTest;
    private static volatile String failure;
    private static volatile boolean warned;
    private static volatile boolean faultForTest;
    private static volatile int refusals;
    private static volatile int sparseRegions;

    private CompactStorage()
    {
    }

    public static boolean active()
    {
        return TurboConfig.get().compactStorageEnabled() && failure == null;
    }

    /** Whether a packed array is big and empty enough to try sparse sections. */
    public static boolean worthConverting(long[] words)
    {
        return forceForTest || (words.length >= MIN_WORDS && SparseIntArray.mostlyZero(words));
    }

    /** Whether the sparse sections really are much smaller than the packed array, which is then dropped. */
    public static boolean worthKeeping(SparseIntArray sparse, int bits)
    {
        return forceForTest || sparse.allocatedBytes() * 2 <= sparse.denseBytes(bits);
    }

    /** A region was stored in sparse sections. */
    public static void countSparse()
    {
        sparseRegions++;
    }

    /** How many regions were stored in sparse sections so far (for the tests). */
    public static int sparseRegions()
    {
        return sparseRegions;
    }

    /** Tests make small fixtures take the sparse path too (any array is converted and kept). */
    public static void setForceForTest(boolean force)
    {
        forceForTest = force;
    }

    /** Tests make the next sparse conversion fail, as a bug in the sparse code would. */
    public static void setFaultForTest(boolean fault)
    {
        faultForTest = fault;
    }

    /** Throws when a test asked for a failing conversion. */
    public static void checkFaultForTest()
    {
        if (faultForTest)
        {
            throw new IllegalStateException("test fault in the sparse storage");
        }
    }

    /** Back to the start of a session (tests only). */
    public static void resetForTest()
    {
        forceForTest = false;
        failure = null;
        warned = false;
        faultForTest = false;
        refusals = 0;
        MemoryBudget.setMaxMemoryForTest(0);
        MemoryBudget.setBudgetForTest(0);
    }

    /** The sparse code failed: it is off for the session; the caller stores the data the plain way. */
    public static void switchOff(String call, Throwable t)
    {
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        TurboLog.error("Compact schematic storage failed in " + call + " and is off for this session; schematics are stored the plain Litematica way:\n" + trace);
        failure = t.toString();
    }

    public static String failure()
    {
        return failure;
    }

    /** The reason for the one chat message after the sparse code switched itself off; null when none is due. */
    public static String takeFailureWarning(boolean playerPresent)
    {
        if (warned == false && failure != null && playerPresent)
        {
            warned = true;
            return failure;
        }

        return null;
    }

    /** Whether a file of this unpacked size may be read; counts the refusal when it may not. */
    public static boolean allowRead(long unpackedBytes)
    {
        if (unpackedBytes > 0 && MemoryBudget.fits(unpackedBytes) == false)
        {
            refusals++;
            return false;
        }

        return true;
    }

    /** How many files were refused because they did not fit in memory (for the tests). */
    public static int refusals()
    {
        return refusals;
    }
}
