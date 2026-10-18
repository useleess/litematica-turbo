package com.litematicaturbo.mc262.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuDispatcher;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.TurboConfig;

/**
 * The upload of a build finished (render thread) and its data is in place: close the GPU buffers
 * of layers it no longer has (a build thread does not clear them when the build starts).
 */
@Mixin(targets = "fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica$2", remap = false)
public abstract class ChunkRenderUploadCallbackMixin
{
    @Shadow @Final ChunkRenderTaskSchematic val$task;

    @Inject(method = "onSuccess(Ljava/util/List;)V", at = @At("RETURN"))
    private void turbo$dropOldLayers(List<Object> results, CallbackInfo ci)
    {
        if (TurboConfig.get().threadingEnabled() == false)
        {
            return;
        }

        ChunkRenderGpuDispatcher gpu = LitematicaRenderer.getInstance().getWorldRenderer().getChunkRendererGpuDispatcher();
        ChunkRendererSchematicVbo renderer = ((ChunkRenderTaskSchematicInvoker) this.val$task).turbo$getRenderChunk();

        if (gpu == null || renderer == null)
        {
            return;
        }

        ChunkPos pos = ((ChunkRenderTaskSchematicInvoker) this.val$task).turbo$getChunkPos();
        ChunkRenderGpuUploader uploader = ((ChunkRenderGpuDispatcherAccessor) gpu).turbo$uploaders().get(ChunkPos.pack(pos.x(), pos.z()));

        if (uploader == null)
        {
            return;
        }

        ChunkRenderDataSchematic data = ((ChunkRendererSchematicVboInvoker) renderer).turbo$getChunkRenderData();
        GpuBufferCacheAccessor cache = (GpuBufferCacheAccessor) ((ChunkRenderGpuUploaderAccessor) uploader).turbo$gpuBufferCache();
        cache.turbo$blockBuffers().entrySet().removeIf(e -> data.isBlockLayerEmpty(e.getKey()) && turbo$close(e.getValue()));
        cache.turbo$overlayBuffers().entrySet().removeIf(e -> data.isOverlayTypeEmpty(e.getKey()) && turbo$close(e.getValue()));
    }

    @Unique
    private static boolean turbo$close(ChunkRenderGpuBuffers buffers)
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
