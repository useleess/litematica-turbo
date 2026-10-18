package com.litematicaturbo.mc12111;

import java.util.concurrent.locks.ReentrantLock;

/** Added to Litematica's chunk renderer. */
public interface TurboRenderer
{
    /** Held while a build or resort of this chunk runs. */
    ReentrantLock turbo$buildLock();

    default boolean turbo$isBuilding()
    {
        return this.turbo$buildLock().isLocked();
    }

    /** Cancels a queued or running build, keeping the last finished one. */
    void turbo$cancelCompile();

    /** Moves where the already built buffers are drawn (ghost move). Keeps the chunk position. */
    void turbo$moveTo(int x, int y, int z);

    // Protected in Litematica

    boolean turbo$needsUpdate();

    void turbo$setNeedsUpdate(boolean immediate);

    void turbo$deleteGlResources();
}
