package com.litematicaturbo.mc12110.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.mc12110.TurboRenderer;

/**
 * Litematica 0.24 no longer hands a build's buffer cache back to the pool once its upload has
 * succeeded (only on failure). Its single render-thread worker has a cache of its own so stock
 * never notices, but pooled build threads would run out of caches after a few chunks and stall.
 */
@Mixin(targets = "fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica$2", remap = false)
public abstract class ChunkRenderUploadCallbackMixin
{
    @Shadow @Final ChunkRenderTaskSchematic val$task;
    @Shadow @Final ChunkRenderWorkerLitematica this$0;

    @Inject(method = "onSuccess(Ljava/util/List;)V", at = @At("HEAD"))
    private void turbo$returnBufferCache(List<Object> results, CallbackInfo ci)
    {
        if (TurboConfig.get().threadingEnabled())
        {
            ((ChunkRenderWorkerInvoker) this.this$0).turbo$clearRenderAllocators(this.val$task);
        }
    }

    /** The new build data is in place now; old GPU buffers of layers it does not have can go. */
    @Inject(method = "onSuccess(Ljava/util/List;)V", at = @At("RETURN"))
    private void turbo$dropOldLayers(List<Object> results, CallbackInfo ci)
    {
        if (TurboConfig.get().threadingEnabled())
        {
            ((TurboRenderer) ((ChunkRenderTaskSchematicInvoker) this.val$task).turbo$getRenderChunk()).turbo$dropUnusedGpuBuffers();
        }
    }
}
