package com.litematicaturbo.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tells one reader whether a change came in while it was reading: any thread may report changes, the reader marks
 * where it starts and asks at its end. Used where Litematica clears its "changed" flag after a read and would lose a
 * change reported in between.
 */
public final class ChangeWatch
{
    private final AtomicInteger changes = new AtomicInteger();
    private int atStart;

    /** A change (any thread). */
    public void changed()
    {
        this.changes.incrementAndGet();
    }

    /** The reader starts reading (reader thread only). */
    public void start()
    {
        this.atStart = this.changes.get();
    }

    /** True when a change came in since the reader started (reader thread only). */
    public boolean changedSinceStart()
    {
        return this.changes.get() != this.atStart;
    }
}
