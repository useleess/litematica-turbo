package com.litematicaturbo.shared.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.BuiltBuffer;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;

/** The re-sort copies of a built piece (see SortCopyData). */
@Mixin(value = ChunkRenderDataSchematic.class, remap = false)
public interface ChunkRenderDataSchematicSortAccessor
{
    @Accessor("transparentSortingData")
    BuiltBuffer.SortState turbo$getTransparentSortingData();

    @Accessor("transparentSortingData")
    void turbo$setTransparentSortingData(BuiltBuffer.SortState state);

    @Accessor("overlaySortingData")
    Map<?, ?> turbo$getOverlaySortingData();
}
