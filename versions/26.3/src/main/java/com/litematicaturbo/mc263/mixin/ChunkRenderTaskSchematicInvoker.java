package com.litematicaturbo.mc263.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

@Mixin(value = ChunkRenderTaskSchematic.class, remap = false)
public interface ChunkRenderTaskSchematicInvoker
{
    @Invoker("isFinished")
    boolean turbo$isFinished();

    @Invoker("getRenderChunk")
    ChunkRendererSchematicVbo turbo$getRenderChunk();

    @Invoker("getChunkPos")
    ChunkPos turbo$getChunkPos();

    @Invoker("getChunkRenderData")
    ChunkRenderDataSchematic turbo$getChunkRenderData();
}
