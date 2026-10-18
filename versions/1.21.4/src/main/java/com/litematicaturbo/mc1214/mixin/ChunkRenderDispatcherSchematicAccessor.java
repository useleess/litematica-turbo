package com.litematicaturbo.mc1214.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

@Mixin(value = ChunkRenderDispatcherSchematic.class, remap = false)
public interface ChunkRenderDispatcherSchematicAccessor
{
    @Accessor("chunkRenderers")
    Long2ObjectOpenHashMap<ChunkRendererSchematicVbo> turbo$getChunkRenderers();
}
