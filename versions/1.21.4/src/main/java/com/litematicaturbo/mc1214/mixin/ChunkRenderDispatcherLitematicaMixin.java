package com.litematicaturbo.mc1214.mixin;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;

import fi.dy.masa.litematica.render.schematic.BufferAllocatorCache;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.mc1214.TurboDispatcher;

/**
 * Litematica 0.21 for 1.21.4 ships with its build worker threads commented out, so every
 * schematic chunk is built on the render thread. Start real worker threads.
 */
@Mixin(value = ChunkRenderDispatcherLitematica.class, remap = false)
public abstract class ChunkRenderDispatcherLitematicaMixin implements TurboDispatcher
{
    @Shadow @Final private List<Thread> listWorkerThreads;
    @Shadow @Final private List<ChunkRenderWorkerLitematica> listThreadedWorkers;
    @Shadow @Final private PriorityBlockingQueue<ChunkRenderTaskSchematic> queueChunkUpdates;
    @Shadow @Final private BlockingQueue<BufferAllocatorCache> queueFreeRenderAllocators;

    @Shadow protected abstract boolean runChunkUploads(long finishTimeNano, Profiler profiler);
    @Shadow public abstract BufferAllocatorCache allocateRenderAllocators() throws InterruptedException;
    @Shadow protected abstract boolean updateChunkNow(ChunkRendererSchematicVbo renderer, Profiler profiler);
    @Shadow protected abstract boolean updateChunkLater(ChunkRendererSchematicVbo renderer, Profiler profiler);

    @Unique
    private static int turbo$threadsToStart()
    {
        return TurboConfig.get().resolveBuildThreads(TurboConfig.CPU_COUNT);
    }

    /** One buffer cache per worker plus a spare (stock value: 2). */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 2))
    private int turbo$bufferCacheCount(int original)
    {
        int threads = turbo$threadsToStart();
        return threads > 0 ? threads + 1 : original;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void turbo$startWorkers(CallbackInfo ci)
    {
        int threads = turbo$threadsToStart();

        for (int i = 0; i < threads; ++i)
        {
            ChunkRenderWorkerLitematica worker = new ChunkRenderWorkerLitematica((ChunkRenderDispatcherLitematica) (Object) this, DummyProfiler.INSTANCE);
            Thread thread = new Thread(worker, "Litematica Turbo Builder " + (i + 1));
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            thread.start();
            this.listThreadedWorkers.add(worker);
            this.listWorkerThreads.add(thread);
        }

        if (threads > 0)
        {
            TurboLog.info("Started " + threads + " schematic build threads");
        }
    }

    /**
     * stopChunkUpdates() waits for every buffer cache to come back, but a worker only returns its
     * cache after the render thread has run its uploads. Keep running uploads while waiting,
     * otherwise the game freezes when renderers are reloaded during a build.
     */
    @Redirect(method = "stopChunkUpdates",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;allocateRenderAllocators()Lfi/dy/masa/litematica/render/schematic/BufferAllocatorCache;"))
    private BufferAllocatorCache turbo$allocateWhileUploading(ChunkRenderDispatcherLitematica self) throws InterruptedException
    {
        if (this.listWorkerThreads.isEmpty())
        {
            return this.allocateRenderAllocators();
        }

        while (true)
        {
            BufferAllocatorCache cache = this.queueFreeRenderAllocators.poll(5, TimeUnit.MILLISECONDS);

            if (cache != null)
            {
                return cache;
            }

            this.runChunkUploads(0L, DummyProfiler.INSTANCE);
        }
    }

    @Override
    public int turbo$threadCount()
    {
        return this.listWorkerThreads.size();
    }

    @Override
    public boolean turbo$updateChunkNow(ChunkRendererSchematicVbo renderer, Profiler profiler)
    {
        return this.updateChunkNow(renderer, profiler);
    }

    @Override
    public boolean turbo$updateChunkLater(ChunkRendererSchematicVbo renderer, Profiler profiler)
    {
        return this.updateChunkLater(renderer, profiler);
    }

    @Override
    public int turbo$queuedTasks()
    {
        return this.queueChunkUpdates.size();
    }
}
