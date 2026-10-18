package com.litematicaturbo.mc1165;

import java.util.List;
import javax.annotation.Nullable;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Added to Litematica's schematic world renderer. */
public interface TurboWorldRenderer
{
    /** True when no schematic chunk is waiting to be built or uploaded. */
    boolean turbo$isIdle();

    /** Takes all chunk renderers out of the renderer (they keep their buffers); new ones get created on demand. */
    @Nullable
    List<ChunkRendererSchematicVbo> turbo$detachChunkRenderers();
}
