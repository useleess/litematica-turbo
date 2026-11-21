package com.litematicaturbo.shared.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;

/** The re-sort copies of a built piece (see SortCopyData). */
@Mixin(value = ChunkMeshDataSchematic.class, remap = false)
public interface ChunkMeshDataSchematicSortAccessor
{
    @Accessor("blockSortingData")
    Map<?, ?> turbo$getBlockSortingData();

    @Accessor("overlaySortingData")
    Map<?, ?> turbo$getOverlaySortingData();
}
