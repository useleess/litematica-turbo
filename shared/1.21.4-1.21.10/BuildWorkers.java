package com.litematicaturbo.shared;

import net.minecraft.util.profiler.DummyProfiler;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

/** Litematica's build worker on these versions (it takes a profiler; build threads get a no-op one). */
public final class BuildWorkers
{
    private BuildWorkers()
    {
    }

    public static ChunkRenderWorkerLitematica create(ChunkRenderDispatcherLitematica dispatcher)
    {
        return new ChunkRenderWorkerLitematica(dispatcher, DummyProfiler.INSTANCE);
    }
}
