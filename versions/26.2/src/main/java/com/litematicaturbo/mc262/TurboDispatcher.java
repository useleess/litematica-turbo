package com.litematicaturbo.mc262;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Added to Litematica's build dispatcher (build threads, F3 statistics, protected methods). */
public interface TurboDispatcher
{
    int turbo$threadCount();

    int turbo$queuedTasks();

    boolean turbo$updateChunkNow(ChunkRendererSchematicVbo renderer);

    boolean turbo$updateChunkLater(ChunkRendererSchematicVbo renderer);
}
