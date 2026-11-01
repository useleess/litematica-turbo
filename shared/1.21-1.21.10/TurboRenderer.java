package com.litematicaturbo.shared;

import java.util.concurrent.locks.ReentrantLock;

import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.shared.mixin.ChunkRendererSchematicVboInvoker;

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

    // Protected in Litematica 0.19+: reached through ChunkRendererSchematicVboInvoker

    default boolean turbo$needsUpdate()
    {
        return ((ChunkRendererSchematicVboInvoker) this).turbo$callNeedsUpdate();
    }

    default void turbo$setNeedsUpdate(boolean immediate)
    {
        ((ChunkRendererSchematicVboInvoker) this).turbo$callSetNeedsUpdate(immediate);
    }

    default void turbo$clearNeedsUpdate()
    {
        ((ChunkRendererSchematicVboInvoker) this).turbo$callClearNeedsUpdate();
    }

    default void turbo$deleteGlResources()
    {
        ((ChunkRendererSchematicVboInvoker) this).turbo$callDeleteGlResources();
    }
}
