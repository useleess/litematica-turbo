package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.render.RenderLayer;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo.OverlayRenderType;

/**
 * Where a piece keeps its re-sort copies (see SortCopySweep): up to Litematica for 1.20.4 the build result holds a
 * BufferBuilder state of the translucent layer and one per overlay type (a full copy of the vertex bytes in 1.16.5, one
 * centre per quad from 1.17), through its public getters and setters.
 */
final class SortCopyData
{
    private SortCopyData()
    {
    }

    static Object data(ChunkRendererSchematicVbo piece)
    {
        return piece.getChunkRenderData();
    }

    static List<Object> copies(Object built)
    {
        ChunkRenderDataSchematic data = (ChunkRenderDataSchematic) built;
        List<Object> copies = new ArrayList<>();
        Object blocks = data.getBlockBufferState(RenderLayer.getTranslucent());

        if (blocks != null)
        {
            copies.add(blocks);
        }

        for (OverlayRenderType type : OverlayRenderType.values())
        {
            Object overlay = data.getOverlayBufferState(type);

            if (overlay != null)
            {
                copies.add(overlay);
            }
        }

        return copies;
    }

    /** Drops the copies; true when there were any. */
    static boolean drop(Object built)
    {
        ChunkRenderDataSchematic data = (ChunkRenderDataSchematic) built;
        boolean held = false;

        if (data.getBlockBufferState(RenderLayer.getTranslucent()) != null)
        {
            data.setBlockBufferState(RenderLayer.getTranslucent(), null);
            held = true;
        }

        for (OverlayRenderType type : OverlayRenderType.values())
        {
            if (data.getOverlayBufferState(type) != null)
            {
                data.setOverlayBufferState(type, null);
                held = true;
            }
        }

        return held;
    }
}
