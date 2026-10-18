package com.litematicaturbo.mc121;

import net.minecraft.util.profiler.Profiler;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Added to Litematica's build dispatcher (F3 statistics, protected methods). */
public interface TurboDispatcher
{
    int turbo$threadCount();

    int turbo$queuedTasks();

    // Protected in Litematica 0.19+

    boolean turbo$updateChunkNow(ChunkRendererSchematicVbo renderer, Profiler profiler);

    boolean turbo$updateChunkLater(ChunkRendererSchematicVbo renderer, Profiler profiler);
}
