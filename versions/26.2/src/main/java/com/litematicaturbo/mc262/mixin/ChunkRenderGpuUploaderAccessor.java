package com.litematicaturbo.mc262.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.GpuBufferCache;

@Mixin(value = ChunkRenderGpuUploader.class, remap = false)
public interface ChunkRenderGpuUploaderAccessor
{
    /** Closes all GPU buffers of this chunk (render thread only). */
    @Invoker("clear")
    void turbo$clear();

    @Accessor("gpuBufferCache")
    GpuBufferCache turbo$gpuBufferCache();
}
