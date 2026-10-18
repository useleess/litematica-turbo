package com.litematicaturbo.mc12110.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

@Mixin(value = ChunkRenderTaskSchematic.class, remap = false)
public interface ChunkRenderTaskSchematicInvoker
{
    @Invoker("isFinished")
    boolean turbo$isFinished();

    @Invoker("getRenderChunk")
    ChunkRendererSchematicVbo turbo$getRenderChunk();
}
