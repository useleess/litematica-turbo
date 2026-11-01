package com.litematicaturbo.shared;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Added to Litematica's schematic world renderer: what the complete copy needs of it. */
public interface CompleteWorldRenderer
{
    /** A new chunk renderer at this position that the world renderer does not know (so it is never listed or rebuilt by it). */
    ChunkRendererSchematicVbo turbo$newRenderer(int x, int y, int z);

    /** Litematica's build queue; null before the renderers are loaded. */
    ChunkRenderDispatcherLitematica turbo$dispatcher();
}
