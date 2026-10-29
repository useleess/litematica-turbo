package com.litematicaturbo.mc263;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;

/**
 * The data a chunk build has just finished, on its way from rebuildChunk to the upload in
 * processTask (both run on the same thread). Stock Litematica publishes it on the build thread
 * and clears the old published data there while the render thread may still walk its block
 * entity list; here it is published on the render thread, once the upload is done.
 */
public final class TurboBuilt
{
    private static final ThreadLocal<ChunkRenderDataSchematic> BUILT = new ThreadLocal<>();

    private TurboBuilt()
    {
    }

    public static void set(ChunkRenderDataSchematic data)
    {
        BUILT.set(data);
    }

    /** Hands the finished data over once; null if this thread has no finished build. */
    public static ChunkRenderDataSchematic take()
    {
        ChunkRenderDataSchematic data = BUILT.get();
        BUILT.remove();
        return data;
    }
}
