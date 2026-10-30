package com.litematicaturbo.shared;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

/** Litematica's build worker on these versions. */
public final class BuildWorkers
{
    private BuildWorkers()
    {
    }

    public static ChunkRenderWorkerLitematica create(ChunkRenderDispatcherLitematica dispatcher)
    {
        return new ChunkRenderWorkerLitematica(dispatcher);
    }
}
