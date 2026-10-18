package com.litematicaturbo.mc263.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.mc263.TurboBuildThread;
import com.litematicaturbo.mc263.TurboDispatcher;

/**
 * Litematica 0.26 builds every schematic chunk on the render thread (inside runChunkUploads).
 * Start Turbo's build threads; the render thread then only uploads finished builds.
 */
@Mixin(value = ChunkRenderDispatcherLitematica.class, remap = false)
public abstract class ChunkRenderDispatcherLitematicaMixin implements TurboDispatcher
{
    @Shadow @Final private PriorityBlockingQueue<ChunkRenderTaskSchematic> queueChunkUpdates;

    @Shadow protected abstract boolean updateChunkNow(ChunkRendererSchematicVbo renderer);
    @Shadow protected abstract boolean updateChunkLater(ChunkRendererSchematicVbo renderer);

    @Unique
    private final List<TurboBuildThread> turbo$threads = new ArrayList<>();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void turbo$startThreads(CallbackInfo ci)
    {
        int threads = TurboConfig.get().resolveBuildThreads(TurboConfig.CPU_COUNT);

        for (int i = 0; i < threads; ++i)
        {
            TurboBuildThread thread = new TurboBuildThread((ChunkRenderDispatcherLitematica) (Object) this, i + 1);
            thread.start();
            this.turbo$threads.add(thread);
        }

        if (threads > 0)
        {
            TurboLog.info("Started " + threads + " schematic build threads");
        }
    }

    /** With build threads the render thread only runs uploads here, it does not build. */
    @Redirect(method = "runChunkUploads",
              at = @At(value = "INVOKE", target = "Ljava/util/concurrent/PriorityBlockingQueue;poll()Ljava/lang/Object;"))
    private Object turbo$noBuildsOnRenderThread(PriorityBlockingQueue<ChunkRenderTaskSchematic> queue)
    {
        return this.turbo$threads.isEmpty() ? queue.poll() : null;
    }

    /** Called when the schematic world goes away (and before the renderers are recreated). */
    @Inject(method = "stopWorkerThreads", at = @At("HEAD"))
    private void turbo$stopThreads(CallbackInfo ci)
    {
        for (TurboBuildThread thread : this.turbo$threads)
        {
            thread.shutdown();
        }

        this.turbo$threads.clear();
    }

    @Override
    public int turbo$threadCount()
    {
        return this.turbo$threads.size();
    }

    @Override
    public int turbo$queuedTasks()
    {
        return this.queueChunkUpdates.size();
    }

    @Override
    public boolean turbo$updateChunkNow(ChunkRendererSchematicVbo renderer)
    {
        return this.updateChunkNow(renderer);
    }

    @Override
    public boolean turbo$updateChunkLater(ChunkRendererSchematicVbo renderer)
    {
        return this.updateChunkLater(renderer);
    }
}
