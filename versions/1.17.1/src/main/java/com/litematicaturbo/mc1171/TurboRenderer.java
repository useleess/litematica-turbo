package com.litematicaturbo.mc1171;

import java.util.concurrent.locks.ReentrantLock;

import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

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

    /** Moves where the already built buffers are drawn (ghost move). */
    void turbo$moveTo(int x, int y, int z);

    /** Removes this chunk's global block entities from the world renderer. */
    void turbo$forgetBlockEntities(WorldRendererSchematic worldRenderer);
}
