package com.litematicaturbo.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/** How much memory a schematic file may take while it is read (idea from Schematica Upgraded). */
public final class MemoryBudget
{
    public static final long MIN_BUDGET = 128L * 1024 * 1024;

    /** Tests set these to pretend the game has less memory (0 = the real values). */
    private static volatile long maxMemoryOverride;
    private static volatile long budgetOverride;

    private MemoryBudget()
    {
    }

    public static void setMaxMemoryForTest(long bytes)
    {
        maxMemoryOverride = bytes;
    }

    public static void setBudgetForTest(long bytes)
    {
        budgetOverride = bytes;
    }

    public static long maxMemory()
    {
        long override = maxMemoryOverride;
        return override > 0 ? override : Runtime.getRuntime().maxMemory();
    }

    /** Half the game's memory, at least {@link #MIN_BUDGET}. */
    public static long readBudget()
    {
        long override = budgetOverride;
        return override > 0 ? override : Math.max(MIN_BUDGET, maxMemory() / 2);
    }

    /** Whether data of this many bytes (once read into memory) fits the budget. */
    public static boolean fits(long bytes)
    {
        return bytes <= readBudget();
    }

    /** The size of a gzip file once unpacked (its last four bytes, exact below 4 GiB), or -1 when it is not a gzip file. */
    public static long gzipUnpackedSize(Path file)
    {
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r"))
        {
            if (f.length() < 18 || f.readUnsignedShort() != 0x1f8b)
            {
                return -1;
            }

            f.seek(f.length() - 4);
            long size = 0;

            for (int i = 0; i < 4; i++)
            {
                size |= (long) f.read() << (8 * i);
            }

            return size;
        }
        catch (IOException e)
        {
            return -1;
        }
    }
}
