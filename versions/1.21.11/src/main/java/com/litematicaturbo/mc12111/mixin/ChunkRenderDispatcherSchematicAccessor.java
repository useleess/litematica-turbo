package com.litematicaturbo.mc12111.mixin;

import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

@Mixin(value = ChunkRenderDispatcherSchematic.class, remap = false)
public interface ChunkRenderDispatcherSchematicAccessor
{
    @Accessor("chunkRenderers")
    ConcurrentHashMap<Long, ChunkRendererSchematicVbo> turbo$getChunkRenderers();
}
