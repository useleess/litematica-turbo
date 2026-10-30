package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

@Mixin(value = ChunkRenderWorkerLitematica.class, remap = false)
public interface ChunkRenderWorkerLitematicaInvoker
{
    @Invoker("processTask")
    void turbo$processTask(ChunkRenderTaskSchematic task) throws InterruptedException;
}
