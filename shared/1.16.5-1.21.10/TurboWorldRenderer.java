package com.litematicaturbo.shared;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Added to Litematica's schematic world renderer. */
public interface TurboWorldRenderer
{
    /** True when no schematic chunk is waiting to be built or uploaded. */
    boolean turbo$isIdle();

    /** Every chunk renderer the world renderer holds now, in view or not (a copy of the list). */
    List<ChunkRendererSchematicVbo> turbo$chunkRenderers();

    /** Turbo's rebuild scheduling of this world renderer. */
    RebuildQueue turbo$rebuilds();

    /**
     * Takes the chunk renderers of these chunk columns (GhostRenderers.column keys) out of the renderer (they keep their
     * buffers); new ones get created on demand.
     */
    @Nullable
    List<ChunkRendererSchematicVbo> turbo$detachChunkRenderers(Set<Long> columns);
}
