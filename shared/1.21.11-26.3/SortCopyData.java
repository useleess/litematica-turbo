package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.shared.mixin.ChunkMeshDataSchematicSortAccessor;
import com.litematicaturbo.shared.mixin.ChunkRendererSchematicVboInvoker;

/**
 * Where a piece keeps its re-sort copies (see SortCopySweep): from Litematica for 1.21.11 the build result's mesh data
 * holds maps of MeshData.SortState (one centre per quad) per block layer and per overlay type.
 */
final class SortCopyData
{
    private SortCopyData()
    {
    }

    static Object data(ChunkRendererSchematicVbo piece)
    {
        return ((ChunkRendererSchematicVboInvoker) piece).turbo$getChunkRenderData().getMeshDataCache();
    }

    static List<Object> copies(Object built)
    {
        ChunkMeshDataSchematicSortAccessor data = (ChunkMeshDataSchematicSortAccessor) built;
        List<Object> copies = new ArrayList<>();
        SortCopyMaps.addValues(data.turbo$getBlockSortingData(), copies);
        SortCopyMaps.addValues(data.turbo$getOverlaySortingData(), copies);
        return copies;
    }

    /** Drops the copies; true when there were any. */
    static boolean drop(Object built)
    {
        ChunkMeshDataSchematicSortAccessor data = (ChunkMeshDataSchematicSortAccessor) built;
        boolean blocks = SortCopyMaps.clear(data.turbo$getBlockSortingData());
        return SortCopyMaps.clear(data.turbo$getOverlaySortingData()) | blocks;
    }
}
