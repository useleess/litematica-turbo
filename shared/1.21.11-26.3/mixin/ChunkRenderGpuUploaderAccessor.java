package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import fi.dy.masa.litematica.render.IWorldSchematicRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.GpuBufferCache;
import fi.dy.masa.litematica.world.WorldSchematic;

@Mixin(value = ChunkRenderGpuUploader.class, remap = false)
public interface ChunkRenderGpuUploaderAccessor
{
    /** Closes all GPU buffers of this chunk (render thread only). */
    @Invoker("clear")
    void turbo$clear();

    /** A new uploader that the GPU dispatcher does not know (a complete-copy piece's own). */
    @Invoker("<init>")
    static ChunkRenderGpuUploader turbo$create(WorldSchematic world, IWorldSchematicRenderer worldRenderer)
    {
        throw new AssertionError();
    }

    @Invoker("setPosition")
    void turbo$setPosition(int x, int y, int z);

    @Accessor("gpuBufferCache")
    GpuBufferCache turbo$gpuBufferCache();
}
