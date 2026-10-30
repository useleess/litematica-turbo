package com.litematicaturbo.shared.mixin;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.render.schematic.BufferBuilderCache;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

import com.litematicaturbo.core.BuildThreads;
import com.litematicaturbo.shared.BuildWorkers;
import com.litematicaturbo.shared.TurboDispatcher;

/**
 * Litematica for these versions ships with its build worker threads commented out, so every
 * schematic chunk is built on the render thread. Start real worker threads.
 */
@Mixin(value = ChunkRenderDispatcherLitematica.class, remap = false)
public abstract class ChunkRenderDispatcherLitematicaMixin implements TurboDispatcher
{
    @Shadow @Final private List<Thread> listWorkerThreads;
    @Shadow @Final private List<ChunkRenderWorkerLitematica> listThreadedWorkers;
    @Shadow @Final private PriorityBlockingQueue<ChunkRenderTaskSchematic> queueChunkUpdates;
    @Shadow @Final private BlockingQueue<BufferBuilderCache> queueFreeRenderBuilders;

    @Shadow public abstract boolean runChunkUploads(long finishTimeNano);
    @Shadow public abstract BufferBuilderCache allocateRenderBuilder() throws InterruptedException;

    /** One buffer cache per worker plus a spare (stock value: 2). */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 2))
    private int turbo$bufferCacheCount(int original)
    {
        return BuildThreads.bufferCaches(original);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void turbo$startWorkers(CallbackInfo ci)
    {
        ChunkRenderDispatcherLitematica self = (ChunkRenderDispatcherLitematica) (Object) this;
        BuildThreads.start(() -> BuildWorkers.create(self), this.listThreadedWorkers, this.listWorkerThreads);
    }

    /** stopChunkUpdates() waits for every buffer cache to come back: keep running uploads meanwhile (BuildThreads). */
    @Redirect(method = "stopChunkUpdates",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;allocateRenderBuilder()Lfi/dy/masa/litematica/render/schematic/BufferBuilderCache;"))
    private BufferBuilderCache turbo$allocateWhileUploading(ChunkRenderDispatcherLitematica self) throws InterruptedException
    {
        if (this.listWorkerThreads.isEmpty())
        {
            return this.allocateRenderBuilder();
        }

        return BuildThreads.takeWhileUploading(this.queueFreeRenderBuilders, () -> this.runChunkUploads(0L));
    }

    @Override
    public int turbo$threadCount()
    {
        return this.listWorkerThreads.size();
    }

    @Override
    public int turbo$queuedTasks()
    {
        return this.queueChunkUpdates.size();
    }
}
