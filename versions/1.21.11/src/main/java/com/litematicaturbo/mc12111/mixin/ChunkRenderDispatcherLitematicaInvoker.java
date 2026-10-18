package com.litematicaturbo.mc12111.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;

@Mixin(value = ChunkRenderDispatcherLitematica.class, remap = false)
public interface ChunkRenderDispatcherLitematicaInvoker
{
    @Invoker("getNextChunkUpdate")
    ChunkRenderTaskSchematic turbo$getNextChunkUpdate() throws InterruptedException;
}
