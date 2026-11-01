package com.litematicaturbo.shared;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Added to Litematica's build dispatcher for the F3 statistics. */
public interface TurboDispatcher
{
    int turbo$threadCount();

    int turbo$queuedTasks();

    default boolean turbo$queueLater(ChunkRendererSchematicVbo renderer)
    {
        return ((ChunkRenderDispatcherLitematica) this).updateChunkLater(renderer);
    }
}
