package com.litematicaturbo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FarDetailTest
{
    /** Plenty of memory: the real test JVM's use never counts as low. */
    private static final long PLENTY = 1L << 40;

    @Before
    public void plentyOfMemory()
    {
        MemoryBudget.setMaxMemoryForTest(PLENTY);
    }

    @After
    public void reset()
    {
        FarDetail.resetForTest();
        TurboConfig.set(new TurboConfig());
    }

    @Test
    public void farIsHalfTheRenderDistanceAtLeastThreePieces()
    {
        assertEquals(6, FarDetail.farPieces(12));
        assertEquals(16, FarDetail.farPieces(32));
        assertEquals(3, FarDetail.farPieces(4));
        assertEquals(3, FarDetail.farPieces(2));
    }

    @Test
    public void distanceIsTheLargerOfTheTwoAxes()
    {
        assertFalse(FarDetail.isFar(5, 0, 0, 0, 12));
        assertTrue(FarDetail.isFar(6, 0, 0, 0, 12));
        assertTrue(FarDetail.isFar(0, -6, 0, 0, 12));
        assertFalse(FarDetail.isFar(5, 5, 0, 0, 12));
        assertTrue(FarDetail.isFar(-3, 10, 2, 4, 12));
        assertFalse(FarDetail.isFar(-2, 2, 0, 0, 2));
        assertTrue(FarDetail.isFar(-3, 2, 0, 0, 2));
    }

    @Test
    public void memoryIsLowWithLessThanAQuarterFree()
    {
        assertFalse(FarDetail.javaMemoryLow(1000, 0));
        assertFalse(FarDetail.javaMemoryLow(1000, 750));
        assertTrue(FarDetail.javaMemoryLow(1000, 751));
        assertTrue(FarDetail.javaMemoryLow(1000, 2000));
    }

    @Test
    public void farPiecesLoseTheirOutlinesNearOnesKeepThem()
    {
        long near = FarDetail.nearDrawn();
        long skipped = FarDetail.farSkipped();
        long drawn = FarDetail.farDrawn();

        assertFalse(FarDetail.hideOutlines(16, 16, 8.5, 8.5, 12));
        assertTrue(FarDetail.hideOutlines(6 * 16, 0, 8.5, 8.5, 12));
        assertTrue(FarDetail.hideOutlines(-7 * 16, 0, -0.5, 0, 12));
        assertFalse(FarDetail.hideOutlines(-5 * 16, 0, 0, 0, 12));

        assertEquals(near + 2, FarDetail.nearDrawn());
        assertEquals(skipped + 2, FarDetail.farSkipped());
        assertEquals(drawn, FarDetail.farDrawn());
    }

    @Test
    public void settingOffOrMasterOffDrawsEveryOutline()
    {
        TurboConfig.get().farDetail = false;
        assertFalse(FarDetail.active());
        assertFalse(FarDetail.hideOutlines(10 * 16, 0, 0, 0, 12));

        TurboConfig.get().farDetail = true;
        TurboConfig.get().enabled = false;
        assertFalse(FarDetail.active());
        long drawn = FarDetail.farDrawn();
        assertFalse(FarDetail.hideOutlines(10 * 16, 0, 0, 0, 12));
        assertEquals(drawn + 1, FarDetail.farDrawn());
    }

    @Test
    public void lowMemorySwitchesItOffUntilMemoryIsFine()
    {
        assertTrue(FarDetail.active());
        MemoryBudget.setMaxMemoryForTest(1);
        assertFalse(FarDetail.active());
        assertFalse(FarDetail.hideOutlines(10 * 16, 0, 0, 0, 12));
        MemoryBudget.setMaxMemoryForTest(PLENTY);
        assertTrue(FarDetail.active());
    }

    @Test
    public void anErrorSwitchesItOffForTheSessionWithOneWarning()
    {
        FarDetail.setFaultForTest(true);

        try
        {
            FarDetail.hideOutlines(10 * 16, 0, 0, 0, 12);
        }
        catch (IllegalStateException e)
        {
            FarDetail.switchOff("test", e);
        }

        assertNotNull(FarDetail.failure());
        assertFalse(FarDetail.active());
        assertFalse(FarDetail.hideOutlines(10 * 16, 0, 0, 0, 12));
        assertNull(FarDetail.takeFailureWarning(false));
        assertNotNull(FarDetail.takeFailureWarning(true));
        assertNull(FarDetail.takeFailureWarning(true));

        FarDetail.resetForTest();
        MemoryBudget.setMaxMemoryForTest(PLENTY);
        assertTrue(FarDetail.active());
    }

    @Test
    public void onByDefault()
    {
        assertTrue(new TurboConfig().farDetail);
        assertTrue(new TurboConfig().farDetailEnabled());
    }
}
