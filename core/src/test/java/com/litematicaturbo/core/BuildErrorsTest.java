package com.litematicaturbo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class BuildErrorsTest
{
    @After
    public void reset()
    {
        BuildErrors.resetForTest();
    }

    @Test
    public void aDroppedBuildIsCountedAndTheChunkIsRebuilt()
    {
        BuildErrors.drop(new IllegalStateException("Missing elements in vertex: mc_midTexCoord"));
        assertEquals(1, BuildErrors.dropped());
        assertTrue(BuildErrors.mayRebuild());
    }

    @Test
    public void thePlayerIsToldOnceAndOnlyWithAPlayerInTheWorld()
    {
        assertNull(BuildErrors.takeWarning(true));
        BuildErrors.drop(new IllegalStateException("first"));
        assertNull(BuildErrors.takeWarning(false));
        assertTrue(BuildErrors.takeWarning(true).contains("first"));
        BuildErrors.drop(new IllegalStateException("second"));
        assertNull(BuildErrors.takeWarning(true));
    }

    @Test
    public void anErrorThatRepeatsStopsQueueingRebuilds()
    {
        for (int i = 0; i <= BuildErrors.MAX_REBUILDS; ++i)
        {
            assertTrue(BuildErrors.mayRebuild());
            BuildErrors.drop(new IllegalStateException("again"));
        }

        assertFalse(BuildErrors.mayRebuild());
    }
}
