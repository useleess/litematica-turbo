package com.litematicaturbo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class BatchBookTest
{
    private static final Object PIECE = new Object();

    @Before
    public void reset()
    {
        BatchDrawing.resetForTest();
        TurboConfig.get().enabled = true;
        TurboConfig.get().batchDraw = true;
    }

    private static void frames(BatchBook<Object> book, int n)
    {
        for (int i = 0; i < n; i++)
        {
            book.nextFrame();
        }
    }

    @Test
    public void sixteenNeighbourPiecesAcrossFourAxesShareOneRegion()
    {
        long key = BatchBook.regionKey(4, 8, 12);

        for (int x = 4; x < 8; x++)
        {
            for (int y = 8; y < 12; y++)
            {
                for (int z = 12; z < 16; z++)
                {
                    assertEquals(key, BatchBook.regionKey(x, y, z));
                }
            }
        }

        assertNotEquals(key, BatchBook.regionKey(8, 8, 12));
        assertNotEquals(key, BatchBook.regionKey(4, 12, 12));
        assertNotEquals(key, BatchBook.regionKey(4, 8, 16));
        assertNotEquals(key, BatchBook.regionKey(3, 8, 12));
    }

    @Test
    public void negativePieceCoordinatesGetTheirOwnRegions()
    {
        assertEquals(BatchBook.regionKey(-4, 0, -1), BatchBook.regionKey(-1, 3, -4));
        assertNotEquals(BatchBook.regionKey(-1, 0, 0), BatchBook.regionKey(0, 0, 0));
        assertEquals(-64, BatchBook.regionOrigin(-1));
        assertEquals(-64, BatchBook.regionOrigin(-4));
        assertEquals(-128, BatchBook.regionOrigin(-5));
    }

    @Test
    public void aRegionOriginIsTheCornerOfItsFourPieces()
    {
        assertEquals(0, BatchBook.regionOrigin(0));
        assertEquals(0, BatchBook.regionOrigin(3));
        assertEquals(64, BatchBook.regionOrigin(4));
        assertEquals(64, BatchBook.regionOrigin(7));
        assertEquals(128, BatchBook.regionOrigin(8));
    }

    @Test
    public void vertexPositionsMoveByTheOffsetAndNothingElseChanges()
    {
        int stride = 32;
        ByteBuffer data = ByteBuffer.allocate(stride * 2).order(ByteOrder.nativeOrder());
        data.putFloat(0, 1f).putFloat(4, 2f).putFloat(8, 3f).putInt(12, 0x11223344);
        data.putFloat(stride, 4f).putFloat(stride + 4, 5f).putFloat(stride + 8, 6f).putInt(stride + 12, 0x55667788);

        BatchBook.shiftPositions(data, 0, 2, stride, 16f, 32f, -16f);

        assertEquals(17f, data.getFloat(0), 0f);
        assertEquals(34f, data.getFloat(4), 0f);
        assertEquals(-13f, data.getFloat(8), 0f);
        assertEquals(20f, data.getFloat(stride), 0f);
        assertEquals(37f, data.getFloat(stride + 4), 0f);
        assertEquals(-10f, data.getFloat(stride + 8), 0f);
        assertEquals(0x11223344, data.getInt(12));
        assertEquals(0x55667788, data.getInt(stride + 12));
    }

    @Test
    public void aPieceIsQuietOnlyAfterStayingUnchangedForTheQuietFrames()
    {
        BatchBook<Object> book = new BatchBook<>(1);
        book.nextFrame();
        assertFalse(book.see(0, PIECE, 5L, 1));

        for (int i = 0; i < BatchBook.QUIET_FRAMES - 1; i++)
        {
            book.nextFrame();
            assertFalse(book.see(0, PIECE, 5L, 1));
        }

        book.nextFrame();
        assertTrue(book.see(0, PIECE, 5L, 1));
    }

    @Test
    public void aRebuiltOrMovedPieceStartsWaitingAgain()
    {
        BatchBook<Object> book = new BatchBook<>(1);
        book.nextFrame();
        book.see(0, PIECE, 5L, 1);
        frames(book, BatchBook.QUIET_FRAMES);
        assertTrue(book.see(0, PIECE, 5L, 1));

        assertFalse(book.see(0, PIECE, 5L, 2));
        frames(book, BatchBook.QUIET_FRAMES);
        assertTrue(book.see(0, PIECE, 5L, 2));

        assertFalse(book.see(0, PIECE, 6L, 2));
    }

    @Test
    public void layersKeepTheirOwnState()
    {
        BatchBook<Object> book = new BatchBook<>(2);
        book.nextFrame();
        book.see(0, PIECE, 5L, 1);
        frames(book, BatchBook.QUIET_FRAMES);
        assertTrue(book.see(0, PIECE, 5L, 1));
        assertFalse(book.see(1, PIECE, 5L, 1));
    }

    @Test
    public void aRegionHoldsAPieceOnlyWhileItIsUnchanged()
    {
        BatchBook<Object> book = new BatchBook<>(1);
        BatchBook.Region<Object> region = new BatchBook.Region<>();
        region.add(PIECE, 5L, 3, 0, 100);

        assertTrue(book.holds(region, PIECE, 5L, 3));
        assertFalse(book.holds(region, PIECE, 5L, 4));
        assertFalse(book.holds(region, PIECE, 6L, 3));
        assertFalse(book.holds(region, new Object(), 5L, 3));
        assertFalse(book.holds(null, PIECE, 5L, 3));
        assertEquals(100, region.member(PIECE).count);
        assertNull(region.member(new Object()));
    }

    @Test
    public void atMostOneRegionIsMadePerFrame()
    {
        BatchBook<Object> book = new BatchBook<>(1);
        book.nextFrame();
        assertTrue(book.mayMake());
        book.made(0, 1L, new BatchBook.Region<>(), 1000);
        assertFalse(book.mayMake());
        book.nextFrame();
        assertTrue(book.mayMake());
    }

    @Test
    public void theReadBackBudgetStopsFurtherRegionsInTheFrame()
    {
        BatchBook<Object> book = new BatchBook<>(1);
        book.nextFrame();
        assertTrue(book.mayMake());
        book.made(0, 1L, new BatchBook.Region<>(), BatchBook.MAX_READBACK_BYTES_PER_FRAME);
        assertFalse(book.mayMake());
    }

    @Test
    public void aRegionNobodyDrawsGivesItsBufferBackAndADrawnOneStays()
    {
        BatchBook<Object> book = new BatchBook<>(1);
        book.nextFrame();
        BatchBook.Region<Object> idle = new BatchBook.Region<>();
        BatchBook.Region<Object> busy = new BatchBook.Region<>();
        book.made(0, 1L, idle, 10);
        book.made(0, 2L, busy, 10);
        assertEquals(2, book.regionCount());

        List<BatchBook.Region<Object>> evicted = null;

        for (int i = 0; i < BatchBook.EVICT_FRAMES + 200; i++)
        {
            List<BatchBook.Region<Object>> now = book.nextFrame();
            book.used(busy);

            if (now.isEmpty() == false)
            {
                evicted = now;
                break;
            }
        }

        assertEquals(1, evicted.size());
        assertTrue(evicted.get(0) == idle);
        assertNull(book.region(0, 1L));
        assertTrue(book.region(0, 2L) == busy);
    }

    @Test
    public void clearReturnsEveryRegionToFree()
    {
        BatchBook<Object> book = new BatchBook<>(2);
        book.nextFrame();
        book.made(0, 1L, new BatchBook.Region<>(), 10);
        book.nextFrame();
        book.made(1, 2L, new BatchBook.Region<>(), 10);

        assertEquals(2, book.clear().size());
        assertEquals(0, book.regionCount());
        assertTrue(book.clear().isEmpty());
    }

    @Test
    public void anErrorSwitchesBatchDrawingOffOnceAndTheSettingStillTurnsItOff() throws Exception
    {
        assertTrue(BatchDrawing.active());
        assertNull(BatchDrawing.takeFailureWarning(true));
        BatchDrawing.switchOff("test", new IllegalStateException("boom"));
        assertFalse(BatchDrawing.active());
        assertNull(BatchDrawing.takeFailureWarning(false));
        assertTrue(BatchDrawing.takeFailureWarning(true).contains("boom"));
        assertNull(BatchDrawing.takeFailureWarning(true));

        BatchDrawing.resetForTest();
        assertTrue(BatchDrawing.active());
        TurboConfig.get().batchDraw = false;
        assertFalse(BatchDrawing.active());
        TurboConfig.get().batchDraw = true;
        TurboConfig.get().enabled = false;
        assertFalse(BatchDrawing.active());
    }

    @Test
    public void aTestFaultThrowsUntilItIsCleared()
    {
        BatchDrawing.checkFaultForTest();
        BatchDrawing.setFaultForTest(true);

        try
        {
            BatchDrawing.checkFaultForTest();
            throw new AssertionError("the fault did not throw");
        }
        catch (IllegalStateException expected)
        {
            BatchDrawing.setFaultForTest(false);
            BatchDrawing.checkFaultForTest();
        }
    }

    @Test
    public void thePauseHookIsClearedWithTheSession()
    {
        BatchDrawing.setPausedForTest(true);
        assertTrue(BatchDrawing.pausedForTest());
        BatchDrawing.resetForTest();
        assertFalse(BatchDrawing.pausedForTest());
    }

    @Test
    public void theCountersOnlyGoUp()
    {
        long calls = BatchDrawing.drawCalls();
        long regions = BatchDrawing.batchedRegions();
        long frames = BatchDrawing.frames();
        BatchDrawing.countDrawCall();
        BatchDrawing.countDrawCall();
        BatchDrawing.countRegion();
        BatchDrawing.countFrame();
        assertEquals(frames + 1, BatchDrawing.frames());
        assertEquals(calls + 2, BatchDrawing.drawCalls());
        assertEquals(regions + 1, BatchDrawing.batchedRegions());
    }

    @Test
    public void theSettingIsSavedAndLoaded() throws Exception
    {
        Path dir = Files.createTempDirectory("turbo-batch");

        try
        {
            TurboConfig config = TurboConfig.load(dir);
            assertTrue(config.batchDraw);
            config.batchDraw = false;
            config.save(dir.resolve(TurboConfig.FILE_NAME));
            assertFalse(TurboConfig.load(dir).batchDraw);
        }
        finally
        {
            try (java.util.stream.Stream<Path> files = Files.walk(dir))
            {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
