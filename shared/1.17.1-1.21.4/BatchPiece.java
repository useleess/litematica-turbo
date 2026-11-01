package com.litematicaturbo.shared;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;

/** Added to Litematica's chunk renderer: what batch drawing needs to know about a built piece. */
public interface BatchPiece
{
    /** Counts how often the piece got new built data, so batch drawing sees a rebuilt piece. */
    int turbo$batchVersion();

    /** The vertex buffer Litematica draws for the layer; null when the piece has nothing to draw there. */
    VertexBuffer turbo$buffer(RenderLayer layer);
}
