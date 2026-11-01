package com.litematicaturbo.shared;

import java.nio.file.Path;

import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;

import com.litematicaturbo.core.CompactStorage;
import com.litematicaturbo.core.MemoryBudget;
import com.litematicaturbo.core.SparseIntArray;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;

/**
 * The game-side calls of the compact schematic storage: which array Litematica's block container gets, and the red
 * message for a file that cannot fit in memory. An error in here switches the sparse storage off for the session
 * ({@link CompactStorage#switchOff}) and the schematic is stored the plain Litematica way.
 */
public final class CompactBlocks
{
    private CompactBlocks()
    {
    }

    /** The array for a region read from a file (or a saved block list): sparse when it is huge and mostly empty. */
    public static LitematicaBitArray fromPacked(int bits, long size, long[] words, int sizeX, int sizeY, int sizeZ)
    {
        if (CompactStorage.active() && CompactStorage.worthConverting(words))
        {
            try
            {
                CompactStorage.checkFaultForTest();
                SparseIntArray sparse = SparseIntArray.fromPacked(bits, sizeX, sizeY, sizeZ, words);

                if (sparse != null && CompactStorage.worthKeeping(sparse, bits))
                {
                    CompactStorage.countSparse();
                    return new SparseBitArray(bits, sparse);
                }
            }
            catch (RuntimeException | LinkageError | OutOfMemoryError e)
            {
                CompactStorage.switchOff("fromPacked", e);
            }
        }

        return new LitematicaBitArray(bits, size, words);
    }

    /** The empty array of a new bit width: a sparse region stays sparse, so the palette growing does not allocate the whole box. */
    public static LitematicaBitArray resized(LitematicaBitArray old, int bits, long size, int sizeX, int sizeY, int sizeZ)
    {
        if (old instanceof SparseBitArray)
        {
            try
            {
                return new SparseBitArray(bits, new SparseIntArray(bits, sizeX, sizeY, sizeZ));
            }
            catch (RuntimeException | LinkageError e)
            {
                CompactStorage.switchOff("resized", e);
            }
        }

        return new LitematicaBitArray(bits, size);
    }

    /** Whether the file must not be read because its unpacked data cannot fit in memory; shows the red message then. */
    public static boolean refuseRead(Path file)
    {
        if (TurboConfig.get().compactStorageEnabled() == false)
        {
            return false;
        }

        try
        {
            long unpacked = MemoryBudget.gzipUnpackedSize(file);

            if (CompactStorage.allowRead(unpacked))
            {
                return false;
            }

            long maxMb = MemoryBudget.maxMemory() / (1024 * 1024);
            TurboLog.warn("Not enough memory to load " + file + " (unpacked " + unpacked + " bytes, the game has " + maxMb + " MB)");
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, CompactStorage.OUT_OF_MEMORY_KEY, maxMb);
            return true;
        }
        catch (RuntimeException | LinkageError e)
        {
            CompactStorage.switchOff("refuseRead", e);
            return false;
        }
    }
}
