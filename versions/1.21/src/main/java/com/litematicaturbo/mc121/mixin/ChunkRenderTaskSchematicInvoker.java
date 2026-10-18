package com.litematicaturbo.mc121.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;

@Mixin(value = ChunkRenderTaskSchematic.class, remap = false)
public interface ChunkRenderTaskSchematicInvoker
{
    @Invoker("isFinished")
    boolean turbo$isFinished();
}
