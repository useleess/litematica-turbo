package com.litematicaturbo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class SortCopiesTest
{
    private static final long SECOND = 1_000_000_000L;

    @After
    public void reset()
    {
        SortCopies.resetForTest();
        TurboConfig.set(new TurboConfig());
    }

    private static double sq(double d)
    {
        return d * d;
    }

    @Test
    public void farPiecesDropNearPiecesKeepWithRoomBetween()
    {
        assertFalse(SortCopies.drops(sq(10), true));
        assertFalse(SortCopies.drops(sq(64), true));
        assertTrue(SortCopies.drops(sq(64.5), true));

        assertTrue(SortCopies.rebuilds(sq(48), true));
        assertFalse(SortCopies.rebuilds(sq(48.5), true));

        // between 48 and 64 a piece neither drops its copies nor is rebuilt for them
        assertFalse(SortCopies.drops(sq(56), true));
        assertFalse(SortCopies.rebuilds(sq(56), true));
    }

    @Test
    public void withoutResortingEveryCopyIsDroppedAndNothingRebuilt()
    {
        assertTrue(SortCopies.drops(0, false));
        assertTrue(SortCopies.drops(sq(30), false));
        assertFalse(SortCopies.rebuilds(0, false));
    }

    @Test
    public void sweepWhenTheCameraMovedSixteenBlocks()
    {
        assertTrue(SortCopies.sweepDue(0, 0, 0, 5, 0));
        SortCopies.swept(0, 0, 0, 5, 0);
        assertFalse(SortCopies.sweepDue(15, 0, 0, 5, 10 * SECOND));
        assertTrue(SortCopies.sweepDue(10, 10, 10, 5, 1));
        assertTrue(SortCopies.sweepDue(0, -16, 0, 5, 1));
    }

    @Test
    public void sweepAtMostOnceASecondWhileBuildsLand()
    {
        SortCopies.swept(0, 0, 0, 5, 0);
        assertFalse(SortCopies.sweepDue(0, 0, 0, 6, SECOND - 1));
        assertTrue(SortCopies.sweepDue(0, 0, 0, 6, SECOND));
        SortCopies.swept(0, 0, 0, 6, SECOND);
        assertFalse(SortCopies.sweepDue(0, 0, 0, 6, 5 * SECOND));
    }

    @Test
    public void sweepSoonForcesTheNextOne()
    {
        SortCopies.swept(0, 0, 0, 5, 0);
        SortCopies.sweepSoon();
        assertTrue(SortCopies.sweepDue(0, 0, 0, 5, 0));
    }

    @Test
    public void followsTheSettingAndTurboItself()
    {
        assertTrue(SortCopies.active());
        TurboConfig.get().sortCopies = false;
        assertFalse(SortCopies.active());
        TurboConfig.get().sortCopies = true;
        TurboConfig.get().setEnabled(false);
        assertFalse(SortCopies.active());
    }

    @Test
    public void anErrorSwitchesOffWithOneWarning()
    {
        SortCopies.setFaultForTest(true);

        try
        {
            SortCopies.checkFaultForTest();
        }
        catch (IllegalStateException e)
        {
            SortCopies.switchOff("sweep", e);
        }

        assertFalse(SortCopies.active());
        assertNotNull(SortCopies.failure());
        assertNull(SortCopies.takeFailureWarning(false));
        assertEquals(SortCopies.failure(), SortCopies.takeFailureWarning(true));
        assertNull(SortCopies.takeFailureWarning(true));

        SortCopies.resetForTest();
        assertTrue(SortCopies.active());
        SortCopies.checkFaultForTest();
    }
}
