package com.litematicaturbo.mc12111.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.GpuBufferCache;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;

/**
 * Stock clears all of a chunk's GPU buffers when its rebuild starts, so uploads always create new
 * buffers (the branch for existing buffers closes the old vertex buffer and then skips writing to
 * it). Build threads cannot clear them there (render thread only, and the chunk would blank until
 * the upload), so each layer's old buffers are closed right before its new data is uploaded.
 */
@Mixin(value = ChunkRenderGpuUploader.class, remap = false)
public abstract class ChunkRenderGpuUploaderMixin
{
    @Shadow @Final private GpuBufferCache gpuBufferCache;

    @Inject(method = "uploadBuffersByLayer", at = @At("HEAD"))
    private void turbo$freshBlockBuffers(ChunkSectionLayer layer, MeshData meshData, boolean useResorting, CallbackInfo ci)
    {
        turbo$close(((GpuBufferCacheAccessor) this.gpuBufferCache).turbo$blockBuffers().remove(layer));
    }

    @Inject(method = "uploadBuffersByType", at = @At("HEAD"))
    private void turbo$freshOverlayBuffers(OverlayRenderType type, MeshData meshData, boolean useResorting, CallbackInfo ci)
    {
        turbo$close(((GpuBufferCacheAccessor) this.gpuBufferCache).turbo$overlayBuffers().remove(type));
    }

    @Unique
    private static void turbo$close(ChunkRenderGpuBuffers buffers)
    {
        if (buffers != null)
        {
            try
            {
                buffers.close();
            }
            catch (Exception ignored)
            {
            }
        }
    }
}
