package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuDispatcher;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;

@Mixin(value = ChunkRenderGpuDispatcher.class, remap = false)
public interface ChunkRenderGpuDispatcherInvoker
{
    @Invoker("addOrGetUploader")
    ChunkRenderGpuUploader turbo$addOrGetUploader(int chunkX, int chunkZ);
}
