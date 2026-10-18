package com.litematicaturbo.mc262.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.level.chunk.LevelChunk;

import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;

@Mixin(value = ChunkCacheSchematic.class, remap = false)
public interface ChunkCacheSchematicAccessor
{
    @Accessor("chunkArray")
    LevelChunk[][] turbo$getChunkArray();

    @Accessor("chunkStartX")
    int turbo$getChunkStartX();

    @Accessor("chunkStartZ")
    int turbo$getChunkStartZ();
}
