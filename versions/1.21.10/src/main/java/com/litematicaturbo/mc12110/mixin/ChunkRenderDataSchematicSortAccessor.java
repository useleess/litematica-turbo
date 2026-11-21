package com.litematicaturbo.mc12110.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;

/** The re-sort copies of a built piece (see SortCopyData). */
@Mixin(value = ChunkRenderDataSchematic.class, remap = false)
public interface ChunkRenderDataSchematicSortAccessor
{
    @Accessor("blockSortingData")
    Map<?, ?> turbo$getBlockSortingData();

    @Accessor("layerSortingData")
    Map<?, ?> turbo$getLayerSortingData();

    @Accessor("overlaySortingData")
    Map<?, ?> turbo$getOverlaySortingData();
}
