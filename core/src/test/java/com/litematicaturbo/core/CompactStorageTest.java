package com.litematicaturbo.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.GZIPOutputStream;
import org.junit.After;
import org.junit.Test;

public class CompactStorageTest
{
    @After
    public void reset()
    {
        CompactStorage.resetForTest();
        TurboConfig.set(new TurboConfig());
    }

    private static long[] pack(int bits, int[] cells)
    {
        long[] words = new long[(int) SparseIntArray.packedLength(bits, cells.length)];

        for (int i = 0; i < cells.length; i++)
        {
            long start = (long) i * bits;
            int first = (int) (start >> 6);
            int shift = (int) (start & 63);
            words[first] |= ((long) cells[i]) << shift;

            if (shift + bits > 64)
            {
                words[first + 1] |= ((long) cells[i]) >>> (64 - shift);
            }
        }

        return words;
    }

    private static int[] scatter(int volume, int blocks, int maxId, long seed)
    {
        int[] cells = new int[volume];
        Random rnd = new Random(seed);

        for (int i = 0; i < blocks; i++)
        {
            cells[rnd.nextInt(volume)] = 1 + rnd.nextInt(maxId);
        }

        return cells;
    }

    @Test
    public void sparseReadsTheSameBlocksAsThePackedArray()
    {
        for (int bits = 2; bits <= 13; bits += 3)
        {
            int[] cells = scatter(37 * 21 * 53, 300, (1 << bits) - 1, bits);
            SparseIntArray sparse = SparseIntArray.fromPacked(bits, 37, 21, 53, pack(bits, cells));
            assertNotNull(sparse);

            for (int i = 0; i < cells.length; i++)
            {
                assertEquals("bits " + bits + " cell " + i, cells[i], sparse.getAt(i));
                assertEquals(cells[i], SparseIntArray.packedGet(pack(bits, cells), bits, i));
            }
        }
    }

    @Test
    public void writingAndPackingGivesTheSameArrayAgain()
    {
        int bits = 5;
        int[] cells = scatter(40 * 17 * 33, 500, 31, 7);
        long[] words = pack(bits, cells);
        SparseIntArray sparse = SparseIntArray.fromPacked(bits, 40, 17, 33, words);
        assertArrayEquals(words, sparse.toPacked(bits));

        sparse.setAt(5, 9);
        sparse.setAt(1000, 0);
        cells[5] = 9;
        cells[1000] = 0;
        assertArrayEquals(pack(bits, cells), sparse.toPacked(bits));
    }

    @Test
    public void onlySectionsWithBlocksTakeMemory()
    {
        SparseIntArray sparse = new SparseIntArray(4, 512, 128, 512);
        assertEquals(0, sparse.allocatedBytes());
        sparse.setAt(0, 3);
        sparse.setAt(1, 0);
        assertEquals(1, sparse.allocatedSections());
        assertEquals(SparseIntArray.SECTION_BYTES, sparse.allocatedBytes());
        sparse.setAt(512L * 512 * 100 + 7, 1);
        assertEquals(2, sparse.allocatedSections());
        assertEquals(32 * 8 * 32, sparse.totalSections());
        assertTrue(sparse.allocatedBytes() * 1000 < SparseIntArray.packedLength(4, sparse.size()) * 8);
    }

    @Test
    public void aValueThatDoesNotFitTheWidthIsRefusedLikeThePackedArray()
    {
        SparseIntArray sparse = new SparseIntArray(3, 16, 16, 16);

        try
        {
            sparse.setAt(0, 8);
            org.junit.Assert.fail("8 does not fit 3 bits");
        }
        catch (IllegalArgumentException expected)
        {
            assertEquals(0, sparse.allocatedSections());
        }
    }

    @Test
    public void aDenseOrTooSmallArrayIsNotWorthConverting()
    {
        long[] dense = new long[CompactStorage.MIN_WORDS];
        java.util.Arrays.fill(dense, 5L);
        assertFalse(CompactStorage.worthConverting(dense));
        assertFalse(CompactStorage.worthConverting(new long[100]));
        assertTrue(CompactStorage.worthConverting(new long[CompactStorage.MIN_WORDS]));
        CompactStorage.setForceForTest(true);
        assertTrue(CompactStorage.worthConverting(new long[100]));
    }

    @Test
    public void sparseSectionsAreKeptOnlyWhenTheyAreMuchSmaller()
    {
        SparseIntArray few = new SparseIntArray(4, 256, 64, 256);
        few.setAt(0, 1);
        assertTrue(CompactStorage.worthKeeping(few, 4));
        SparseIntArray full = new SparseIntArray(4, 16, 16, 16);
        full.setAt(0, 1);
        assertFalse(CompactStorage.worthKeeping(full, 4));
        CompactStorage.setForceForTest(true);
        assertTrue(CompactStorage.worthKeeping(full, 4));
    }

    @Test
    public void anArrayThatDoesNotFitTheBoxOrIsTooWideGivesNothing()
    {
        assertNull(SparseIntArray.fromPacked(4, 64, 64, 64, new long[10]));
        assertNull(SparseIntArray.fromPacked(17, 16, 16, 16, new long[4096]));
        assertNull(SparseIntArray.fromPacked(4, 16, 16, 16, null));
    }

    @Test
    public void boxesThatAreNotMultiplesOfSixteenKeepTheirEdges()
    {
        int[] cells = new int[19 * 3 * 18];
        cells[cells.length - 1] = 2;
        cells[0] = 1;
        SparseIntArray sparse = SparseIntArray.fromPacked(2, 19, 3, 18, pack(2, cells));
        assertEquals(2, sparse.getAt(cells.length - 1));
        assertEquals(1, sparse.getAt(0));
        assertEquals(0, sparse.getAt(cells.length));
        assertEquals(0, sparse.getAt(-1));
    }

    @Test
    public void theBudgetIsHalfTheMemoryButNeverUnderTheMinimum()
    {
        MemoryBudget.setMaxMemoryForTest(4L << 30);
        assertEquals(2L << 30, MemoryBudget.readBudget());
        assertTrue(MemoryBudget.fits(2L << 30));
        assertFalse(MemoryBudget.fits((2L << 30) + 1));
        MemoryBudget.setMaxMemoryForTest(64L << 20);
        assertEquals(MemoryBudget.MIN_BUDGET, MemoryBudget.readBudget());
    }

    @Test
    public void aFileThatCannotFitIsRefusedAndCounted() throws IOException
    {
        Path file = Files.createTempFile("turbo-gz", ".bin");

        try
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (GZIPOutputStream gz = new GZIPOutputStream(bytes))
            {
                gz.write(new byte[3 << 20]);
            }

            Files.write(file, bytes.toByteArray());
            assertEquals(3 << 20, MemoryBudget.gzipUnpackedSize(file));
            assertTrue(CompactStorage.allowRead(MemoryBudget.gzipUnpackedSize(file)));
            MemoryBudget.setMaxMemoryForTest(1 << 20);
            assertTrue(CompactStorage.allowRead(MemoryBudget.gzipUnpackedSize(file)));
            MemoryBudget.setMaxMemoryForTest(0);
            assertFalse(CompactStorage.allowRead(MemoryBudget.readBudget() + 1));
            assertEquals(1, CompactStorage.refusals());
            Files.write(file, new byte[100]);
            assertEquals(-1, MemoryBudget.gzipUnpackedSize(file));
        }
        finally
        {
            Files.delete(file);
        }
    }

    @Test
    public void anErrorSwitchesTheCompactStorageOffOnceAndTheSettingStillTurnsItOff()
    {
        assertTrue(CompactStorage.active());
        assertNull(CompactStorage.takeFailureWarning(true));
        CompactStorage.switchOff("test", new IllegalStateException("boom"));
        assertFalse(CompactStorage.active());
        assertNull(CompactStorage.takeFailureWarning(false));
        assertTrue(CompactStorage.takeFailureWarning(true).contains("boom"));
        assertNull(CompactStorage.takeFailureWarning(true));

        CompactStorage.resetForTest();
        TurboConfig.get().compactStorage = false;
        assertFalse(CompactStorage.active());
        TurboConfig.get().compactStorage = true;
        TurboConfig.get().setEnabled(false);
        assertFalse(CompactStorage.active());
    }

    @Test
    public void theSettingIsSavedAndLoaded() throws IOException
    {
        Path dir = Files.createTempDirectory("turbo-config");

        try
        {
            TurboConfig config = new TurboConfig();
            assertTrue(config.compactStorage);
            config.compactStorage = false;
            config.save(dir.resolve(TurboConfig.FILE_NAME));
            assertFalse(TurboConfig.load(dir).compactStorage);
        }
        finally
        {
            Files.deleteIfExists(dir.resolve(TurboConfig.FILE_NAME));
            Files.delete(dir);
        }
    }
}
