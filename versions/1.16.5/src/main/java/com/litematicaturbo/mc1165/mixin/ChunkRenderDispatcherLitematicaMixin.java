package com.litematicaturbo.mc1165.mixin;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import fi.dy.masa.litematica.render.schematic.BufferBuilderCache;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.mc1165.TurboDispatcher;

/**
 * Litematica for 1.16.5 already builds schematic chunks on worker threads (later versions
 * dropped them). Turbo sets their number and fixes the shutdown deadlock.
 */
@Mixin(value = ChunkRenderDispatcherLitematica.class, remap = false)
public abstract class ChunkRenderDispatcherLitematicaMixin implements TurboDispatcher
{
    @Shadow @Final private List<Thread> listWorkerThreads;
    @Shadow @Final private PriorityBlockingQueue<ChunkRenderTaskSchematic> queueChunkUpdates;
    @Shadow @Final private BlockingQueue<BufferBuilderCache> queueFreeRenderBuilders;

    @Shadow public abstract boolean runChunkUploads(long finishTimeNano);
    @Shadow public abstract BufferBuilderCache allocateRenderBuilder() throws InterruptedException;

    /**
     * The thread count Litematica picks for itself (local 2: CPU count, limited by memory). Turbo's
     * setting replaces it; Litematica only starts worker threads for a count above 1.
     */
    @ModifyVariable(method = "<init>", at = @At("STORE"), index = 2)
    private int turbo$threadCount(int original)
    {
        TurboConfig config = TurboConfig.get();

        if (config.enabled == false)
        {
            return original;
        }

        if (config.buildThreads < 0)
        {
            return 1; // render thread only
        }

        int threads = Math.max(2, config.resolveBuildThreads(TurboConfig.CPU_COUNT));
        TurboLog.info("Litematica will use " + threads + " schematic build threads");
        return threads;
    }

    /**
     * stopChunkUpdates() waits for every buffer cache to come back, but a worker only returns its
     * cache after the render thread has run its uploads. Keep running uploads while waiting,
     * otherwise the game freezes when renderers are reloaded during a build.
     */
    @Redirect(method = "stopChunkUpdates",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;allocateRenderBuilder()Lfi/dy/masa/litematica/render/schematic/BufferBuilderCache;"))
    private BufferBuilderCache turbo$allocateWhileUploading(ChunkRenderDispatcherLitematica self) throws InterruptedException
    {
        if (this.listWorkerThreads.isEmpty())
        {
            return this.allocateRenderBuilder();
        }

        while (true)
        {
            BufferBuilderCache cache = this.queueFreeRenderBuilders.poll(5, TimeUnit.MILLISECONDS);

            if (cache != null)
            {
                return cache;
            }

            this.runChunkUploads(0L);
        }
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
