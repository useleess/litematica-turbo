package com.litematicaturbo.mc263.mixin;

import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuDispatcher;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;

@Mixin(value = ChunkRenderGpuDispatcher.class, remap = false)
public interface ChunkRenderGpuDispatcherAccessor
{
    @Accessor("uploaders")
    ConcurrentHashMap<Long, ChunkRenderGpuUploader> turbo$uploaders();
}
