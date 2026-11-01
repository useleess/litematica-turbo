package com.litematicaturbo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CompleteCopyTest
{
    private static final long MB = 1024L * 1024;
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
        CompleteCopy.resetForTest();
        TurboConfig.set(new TurboConfig());
    }

    @Test
    public void blocksCostTheEstimatedBytes()
    {
        assertEquals(0, CompleteCopy.estimateBytes(0));
        assertEquals(4096 * CompleteCopy.BYTES_PER_BLOCK, CompleteCopy.estimateBytes(4096));
    }

    @Test
    public void javaBudgetIsAnEighthOfTheMemoryAtLeastTheMinimum()
    {
        assertEquals(1024 * MB, CompleteCopy.javaBudget(8192 * MB));
        assertEquals(CompleteCopy.MIN_JAVA_BUDGET, CompleteCopy.javaBudget(512 * MB));
        assertEquals(CompleteCopy.MIN_JAVA_BUDGET, CompleteCopy.javaBudget(1));
    }

    @Test
    public void graphicsBudgetIsAQuarterOfWhatTheCardReportsFree()
    {
        assertEquals(1024 * MB, CompleteCopy.gpuBudget(4096 * 1024));
        assertEquals(0, CompleteCopy.gpuBudget(0));
        assertEquals(Long.MAX_VALUE, CompleteCopy.gpuBudget(-1));
    }

    @Test
    public void theBudgetIsTheSmallerOfJavaAndGraphics()
    {
        assertEquals(1024 * MB, CompleteCopy.budget(8192 * MB, 16384 * 1024));
        assertEquals(500 * MB, CompleteCopy.budget(8192 * MB, 2000 * 1024));
        assertEquals(1024 * MB, CompleteCopy.budget(8192 * MB, -1));
    }

    @Test
    public void aPieceFitsWhileTheUsedBytesStayWithinTheBudget()
    {
        assertTrue(CompleteCopy.fits(0, 100, 100));
        assertTrue(CompleteCopy.fits(60, 40, 100));
        assertFalse(CompleteCopy.fits(60, 41, 100));
        assertFalse(CompleteCopy.fits(101, 0, 100));
    }

    @Test
    public void graphicsMemoryIsLowBelowTheMinimumOnlyWhenKnown()
    {
        assertFalse(CompleteCopy.gpuLow(-1));
        assertFalse(CompleteCopy.gpuLow(CompleteCopy.MIN_GPU_FREE_BYTES / 1024));
        assertTrue(CompleteCopy.gpuLow(CompleteCopy.MIN_GPU_FREE_BYTES / 1024 - 1));
        assertTrue(CompleteCopy.gpuLow(0));
    }

    @Test
    public void memoryIsTightWhenJavaOrGraphicsMemoryIsLow()
    {
        assertFalse(CompleteCopy.memoryTight(-1));
        assertFalse(CompleteCopy.memoryTight(8192 * 1024));
        assertTrue(CompleteCopy.memoryTight(0));
        MemoryBudget.setMaxMemoryForTest(1);
        assertTrue(CompleteCopy.memoryTight(-1));
    }

    @Test
    public void aTestCanPretendTheCardMemory()
    {
        assertEquals(500, CompleteCopy.gpuFreeKb(500));
        CompleteCopy.setGpuFreeKbForTest(7);
        assertEquals(7, CompleteCopy.gpuFreeKb(500));
        CompleteCopy.setGpuFreeKbForTest(-1);
        assertEquals(-1, CompleteCopy.gpuFreeKb(500));
        CompleteCopy.resetForTest();
        assertEquals(500, CompleteCopy.gpuFreeKb(500));
    }

    @Test
    public void followsTheSettingAndNeedsGhostMoving()
    {
        TurboConfig c = new TurboConfig();
        TurboConfig.set(c);
        assertTrue(CompleteCopy.active());
        c.completeCopy = false;
        assertFalse(CompleteCopy.active());
        c.completeCopy = true;
        c.ghostMove = false;
        assertFalse(CompleteCopy.active());
        c.ghostMove = true;
        c.setEnabled(false);
        assertFalse(CompleteCopy.active());
    }

    @Test
    public void anErrorSwitchesItOffAndTheChatIsToldOnce()
    {
        TurboConfig.set(new TurboConfig());
        assertNull(CompleteCopy.takeFailureWarning(true));
        CompleteCopy.switchOff("a test", new IllegalStateException("boom"));
        assertFalse(CompleteCopy.active());
        assertNotNull(CompleteCopy.failure());
        assertNull(CompleteCopy.takeFailureWarning(false));
        String warning = CompleteCopy.takeFailureWarning(true);
        assertTrue(warning, warning.contains("boom"));
        assertNull(CompleteCopy.takeFailureWarning(true));
        CompleteCopy.resetForTest();
        assertTrue(CompleteCopy.active());
    }

    @Test
    public void aTestFaultThrowsUntilItIsEnded()
    {
        CompleteCopy.checkFaultForTest();
        CompleteCopy.setFaultForTest(true);

        try
        {
            CompleteCopy.checkFaultForTest();
            org.junit.Assert.fail("no fault");
        }
        catch (IllegalStateException expected)
        {
            assertTrue(expected.getMessage().contains("complete copy"));
        }

        CompleteCopy.setFaultForTest(false);
        CompleteCopy.checkFaultForTest();
    }

    @Test
    public void theCountersCountTheirOwnEvents()
    {
        CompleteCopy.noteMade();
        CompleteCopy.noteMade();
        CompleteCopy.noteSkippedLowMemory();
        CompleteCopy.noteDroppedTight();
        assertEquals(2, CompleteCopy.made());
        assertEquals(1, CompleteCopy.skippedLowMemory());
        assertEquals(1, CompleteCopy.droppedTight());
        CompleteCopy.resetForTest();
        assertEquals(0, CompleteCopy.made());
    }

    @Test
    public void theSettingIsInTheFileAndReadBack()
    {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("completeCopy", "false");
        TurboConfig c = new TurboConfig();
        c.read(props);
        assertFalse(c.completeCopy);
        assertTrue(new TurboConfig().completeCopy);
    }
}
