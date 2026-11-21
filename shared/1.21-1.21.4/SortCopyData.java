package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.shared.mixin.ChunkRenderDataSchematicSortAccessor;
import com.litematicaturbo.shared.mixin.ChunkRendererSchematicVboInvoker;

/**
 * Where a piece keeps its re-sort copies (see SortCopySweep): in Litematica for 1.21 - 1.21.4 the build result holds one
 * BuiltBuffer.SortState (one centre per quad) of the translucent layer and a map of them per overlay type. A missing one
 * is made again by Litematica's next re-sort of the piece.
 */
final class SortCopyData
{
    private SortCopyData()
    {
    }

    static Object data(ChunkRendererSchematicVbo piece)
    {
        return ((ChunkRendererSchematicVboInvoker) piece).turbo$getChunkRenderData();
    }

    static List<Object> copies(Object built)
    {
        ChunkRenderDataSchematicSortAccessor data = (ChunkRenderDataSchematicSortAccessor) built;
        List<Object> copies = new ArrayList<>();

        if (data.turbo$getTransparentSortingData() != null)
        {
            copies.add(data.turbo$getTransparentSortingData());
        }

        SortCopyMaps.addValues(data.turbo$getOverlaySortingData(), copies);
        return copies;
    }

    /** Drops the copies; true when there were any. */
    static boolean drop(Object built)
    {
        ChunkRenderDataSchematicSortAccessor data = (ChunkRenderDataSchematicSortAccessor) built;
        boolean held = data.turbo$getTransparentSortingData() != null;
        data.turbo$setTransparentSortingData(null);
        return SortCopyMaps.clear(data.turbo$getOverlaySortingData()) | held;
    }
}
