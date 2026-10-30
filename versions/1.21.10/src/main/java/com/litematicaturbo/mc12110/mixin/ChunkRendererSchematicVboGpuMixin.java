package com.litematicaturbo.mc12110.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.systems.RenderSystem;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderObjectBuffers;
import fi.dy.masa.litematica.render.schematic.GpuBufferCache;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import com.litematicaturbo.mc12110.TurboGpuBuffers;

/** Litematica 0.24's GPU buffers of a chunk, kept while a build thread rebuilds it. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboGpuMixin implements TurboGpuBuffers
{
    @Shadow protected abstract ChunkRenderDataSchematic getChunkRenderData();
    @Shadow protected abstract GpuBufferCache getGpuBufferCache();

    /**
     * Litematica 0.24 closes the chunk's GPU buffers when a rebuild starts. That is render-thread
     * only, and on a build thread it would also blank the chunk until the upload. The upload
     * replaces each layer's buffers anyway; layers the new build no longer has are closed once
     * it is in place ({@link #turbo$dropUnusedGpuBuffers}).
     */
    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/GpuBufferCache;clearAll()V"))
    private void turbo$keepGpuBuffersWhileBuilding(GpuBufferCache cache)
    {
        if (RenderSystem.isOnRenderThread())
        {
            ((GpuBufferCacheAccessor) cache).turbo$clearAll();
        }
    }

    @Override
    public void turbo$dropUnusedGpuBuffers()
    {
        ChunkRenderDataSchematic data = this.getChunkRenderData();
        GpuBufferCacheAccessor cache = (GpuBufferCacheAccessor) this.getGpuBufferCache();
        cache.turbo$blockBuffers().entrySet().removeIf(e -> data.isBlockLayerEmpty(e.getKey()) && turbo$close(e.getValue()));
        cache.turbo$layerBuffers().entrySet().removeIf(e -> data.isLayerEmpty(e.getKey()) && turbo$close(e.getValue()));
        cache.turbo$overlayBuffers().entrySet().removeIf(e -> data.isOverlayTypeEmpty(e.getKey()) && turbo$close(e.getValue()));
    }

    @Unique
    private static boolean turbo$close(ChunkRenderObjectBuffers buffers)
    {
        try
        {
            buffers.close();
        }
        catch (Exception ignored)
        {
        }

        return true;
    }
}
