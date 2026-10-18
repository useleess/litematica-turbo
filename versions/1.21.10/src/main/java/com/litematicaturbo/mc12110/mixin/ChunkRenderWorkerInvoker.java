package com.litematicaturbo.mc12110.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

@Mixin(value = ChunkRenderWorkerLitematica.class, remap = false)
public interface ChunkRenderWorkerInvoker
{
    /** Clears the task's buffer cache and returns it to the dispatcher's pool. */
    @Invoker("clearRenderAllocators")
    void turbo$clearRenderAllocators(ChunkRenderTaskSchematic task);
}
