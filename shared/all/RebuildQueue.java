package com.litematicaturbo.shared;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.TurboConfig;

/**
 * Turbo's rebuild scheduling in Litematica's world renderer (each version's WorldRendererSchematicMixin hands
 * its calls here, with the dispatcher calls of its version).
 */
public final class RebuildQueue
{
    /** Chunks whose rebuild was requested while a worker was still building them (render thread only). */
    private final Set<ChunkRendererSchematicVbo> deferred = new LinkedHashSet<>();
    /** Rebuilds also wait while a placement is being moved (ghost move on the Mojang-named versions). */
    private final boolean holdWhileMoving;

    public RebuildQueue(boolean holdWhileMoving)
    {
        this.holdWhileMoving = holdWhileMoving;
    }

    /**
     * Stock Litematica rebuilds every chunk within 32 blocks of the camera right away on the render
     * thread - a whole column per chunk, which is the stutter when standing in a big schematic.
     * Hand those to the build threads too (nearest chunks are still built first).
     */
    public boolean updateNow(ChunkRendererSchematicVbo renderer, Predicate<ChunkRendererSchematicVbo> now,
                             Predicate<ChunkRendererSchematicVbo> later)
    {
        TurboConfig config = TurboConfig.get();

        if (config.threadingEnabled() == false)
        {
            return now.test(renderer);
        }

        if (this.deferIfBuilding(renderer))
        {
            return true;
        }

        return config.asyncNearRebuildsEnabled() ? later.test(renderer) : now.test(renderer);
    }

    public boolean updateLater(ChunkRendererSchematicVbo renderer, Predicate<ChunkRendererSchematicVbo> later)
    {
        if (TurboConfig.get().threadingEnabled() && this.deferIfBuilding(renderer))
        {
            return true;
        }

        return later.test(renderer);
    }

    /**
     * Scheduling a rebuild waits on a lock that a running build of the same chunk holds for its whole
     * duration, which would freeze the render thread. Retry once that build has finished instead.
     */
    private boolean deferIfBuilding(ChunkRendererSchematicVbo renderer)
    {
        // While a placement moves the ghosts are shown; rebuilds wait until it has settled
        if (((TurboRenderer) renderer).turbo$isBuilding() || (this.holdWhileMoving && GhostMove.isHoldingWork()))
        {
            this.deferred.add(renderer);
            return true;
        }

        return false;
    }

    /** Start of Litematica's chunk updates: deferred chunks whose build has finished are queued again. */
    public void requeue(Set<ChunkRendererSchematicVbo> chunksToUpdate)
    {
        if (this.deferred.isEmpty() || (this.holdWhileMoving && GhostMove.isHoldingWork()))
        {
            return;
        }

        Iterator<ChunkRendererSchematicVbo> it = this.deferred.iterator();

        while (it.hasNext())
        {
            ChunkRendererSchematicVbo renderer = it.next();

            if (((TurboRenderer) renderer).turbo$isBuilding() == false)
            {
                ((TurboRenderer) renderer).turbo$setNeedsUpdate(false);
                chunksToUpdate.add(renderer);
                it.remove();
            }
        }
    }

    public void clear()
    {
        this.deferred.clear();
    }

    /** True when no schematic chunk is waiting to be built or uploaded. */
    public boolean isIdle(ChunkRenderDispatcherLitematica dispatcher, Set<ChunkRendererSchematicVbo> chunksToUpdate,
                          List<ChunkRendererSchematicVbo> renderInfos)
    {
        if (dispatcher == null || chunksToUpdate.isEmpty() == false || this.deferred.isEmpty() == false ||
            dispatcher.hasChunkUpdates() == false) // stock name: true means the queues are empty
        {
            return false;
        }

        for (ChunkRendererSchematicVbo renderer : renderInfos)
        {
            if (((TurboRenderer) renderer).turbo$needsUpdate() || ((TurboRenderer) renderer).turbo$isBuilding())
            {
                return false;
            }
        }

        return true;
    }
}
