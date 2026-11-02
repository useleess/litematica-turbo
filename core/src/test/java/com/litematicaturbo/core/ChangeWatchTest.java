package com.litematicaturbo.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChangeWatchTest
{
    @Test
    public void aChangeBeforeTheReadIsNotCountedAgain()
    {
        ChangeWatch watch = new ChangeWatch();
        watch.changed();
        watch.start();
        assertFalse(watch.changedSinceStart());
    }

    @Test
    public void aChangeDuringTheReadIsSeenAtItsEnd()
    {
        ChangeWatch watch = new ChangeWatch();
        watch.start();
        watch.changed();
        assertTrue(watch.changedSinceStart());
        watch.start();
        assertFalse(watch.changedSinceStart());
    }

    @Test
    public void aChangeFromAnotherThreadDuringTheReadIsSeen() throws InterruptedException
    {
        ChangeWatch watch = new ChangeWatch();
        watch.start();
        Thread other = new Thread(watch::changed);
        other.start();
        other.join();
        assertTrue(watch.changedSinceStart());
    }
}
