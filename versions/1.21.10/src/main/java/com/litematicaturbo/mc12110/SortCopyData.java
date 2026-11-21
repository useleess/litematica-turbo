package com.litematicaturbo.mc12110;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.mc12110.mixin.ChunkRenderDataSchematicSortAccessor;
import com.litematicaturbo.mc12110.mixin.ChunkRendererSchematicVboInvoker;

/**
 * Where a piece keeps its re-sort copies (see SortCopySweep): in Litematica for 1.21.10 the build result holds maps of
 * BuiltBuffer.SortState (one centre per quad) per block layer, per render layer and per overlay type.
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
        SortCopyMaps.addValues(data.turbo$getBlockSortingData(), copies);
        SortCopyMaps.addValues(data.turbo$getLayerSortingData(), copies);
        SortCopyMaps.addValues(data.turbo$getOverlaySortingData(), copies);
        return copies;
    }

    /** Drops the copies; true when there were any. */
    static boolean drop(Object built)
    {
        ChunkRenderDataSchematicSortAccessor data = (ChunkRenderDataSchematicSortAccessor) built;
        boolean blocks = SortCopyMaps.clear(data.turbo$getBlockSortingData());
        boolean layers = SortCopyMaps.clear(data.turbo$getLayerSortingData());
        return SortCopyMaps.clear(data.turbo$getOverlaySortingData()) | blocks | layers;
    }
}
