package com.litematicaturbo.shared;

import java.util.Map;
import javax.annotation.Nullable;

import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Added to Litematica's schematic world renderer. */
public interface TurboWorldRenderer
{
    /** True when no schematic chunk is waiting to be built or uploaded. */
    boolean turbo$isIdle();

    /**
     * Takes all chunk renderers out of the renderer, together with the GPU uploaders that hold
     * their buffers (key: the renderer). New ones get created on demand.
     */
    @Nullable
    Map<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> turbo$detachChunkRenderers();
}
