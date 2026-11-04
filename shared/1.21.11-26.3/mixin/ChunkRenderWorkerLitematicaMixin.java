package com.litematicaturbo.shared.mixin;

import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuDispatcher;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BuildErrors;
import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.shared.CompletePiece;
import com.litematicaturbo.shared.DroppedBuilds;
import com.litematicaturbo.shared.CompleteUploads;
import com.litematicaturbo.shared.TurboBuilt;
import com.litematicaturbo.shared.TurboRenderer;

/**
 * Runs each build/resort under the chunk's own lock, so a cancelled build still finishing on one
 * thread never overlaps a new build of the same chunk on another (they share scratch fields).
 * Also times builds of the view for the F3 statistics.
 */
@Mixin(value = ChunkRenderWorkerLitematica.class, remap = false)
public abstract class ChunkRenderWorkerLitematicaMixin
{
    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;rebuildChunk(Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherBuffers;)V"))
    private void turbo$rebuildChunk(ChunkRendererSchematicVbo renderer, ChunkRenderTaskSchematic task, ChunkRenderDispatcherBuffers pack)
    {
        boolean onRenderThread = RenderSystem.isOnRenderThread();
        ReentrantLock lock = ((TurboRenderer) renderer).turbo$buildLock();
        lock.lock();
        long start = System.nanoTime();
        TurboBuilt.take();

        try
        {
            if (onRenderThread == false)
            {
                BuildErrors.checkFaultForTest();
            }

            ((ChunkRendererSchematicVboInvoker) renderer).turbo$rebuildChunk(task, pack);
        }
        catch (RuntimeException e)
        {
            if (onRenderThread)
            {
                throw e;
            }

            // A finished task: the chunk renderer was discarded (world unloaded, out of range) while this cancelled
            // build was still running on a worker; its result is thrown away anyway. Any other error on a build thread
            // (another mod's vertex writer, a broken block model) drops this build and builds the chunk again.
            if (((ChunkRenderTaskSchematicInvoker) task).turbo$isFinished() == false)
            {
                DroppedBuilds.drop((TurboRenderer) renderer, e);
            }
        }
        finally
        {
            lock.unlock();

            if (((CompletePiece) renderer).turbo$isComplete() == false)
            {
                BuildStats.recordBuild(System.nanoTime() - start, onRenderThread);
            }
        }
    }

    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;resortTransparency(Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherBuffers;)V"))
    private void turbo$resortTransparency(ChunkRendererSchematicVbo renderer, ChunkRenderTaskSchematic task, ChunkRenderDispatcherBuffers pack)
    {
        ReentrantLock lock = ((TurboRenderer) renderer).turbo$buildLock();
        lock.lock();

        try
        {
            ((ChunkRendererSchematicVboInvoker) renderer).turbo$resortTransparency(task, pack);
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * The upload takes the data the build just finished (stock reads it back from the chunk, where
     * the build thread had published it). onSuccess then publishes it on the render thread.
     */
    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;getChunkRenderData()Lfi/dy/masa/litematica/render/schematic/ChunkRenderDataSchematic;"))
    private ChunkRenderDataSchematic turbo$uploadBuiltData(ChunkRenderTaskSchematic task)
    {
        ChunkRenderDataSchematic built = TurboBuilt.take();
        return built != null ? built : ((ChunkRenderTaskSchematicInvoker) task).turbo$getChunkRenderData();
    }

    /** A complete-copy piece uploads into its own uploader, not the one of the view's chunk there. */
    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderGpuDispatcher;addOrGetUploader(II)Lfi/dy/masa/litematica/render/schematic/ChunkRenderGpuUploader;"))
    private ChunkRenderGpuUploader turbo$pieceUploader(ChunkRenderGpuDispatcher dispatcher, int chunkX, int chunkZ, @Local(argsOnly = true) ChunkRenderTaskSchematic task)
    {
        ChunkRendererSchematicVbo renderer = ((ChunkRenderTaskSchematicInvoker) task).turbo$getRenderChunk();
        ChunkRenderGpuUploader own = ((CompleteUploads) renderer).turbo$completeUploader();
        return own != null ? own : ((ChunkRenderGpuDispatcherInvoker) dispatcher).turbo$addOrGetUploader(chunkX, chunkZ);
    }

    /**
     * A rebuild starts by closing the chunk's GPU buffers. That is render-thread only, and on a
     * build thread it would also blank the chunk until the upload. The upload replaces each
     * layer's buffers anyway; layers the new build no longer has are closed once it is in place
     * (ChunkRenderUploadCallbackMixin).
     */
    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderGpuUploader;clear()V"))
    private void turbo$keepGpuBuffersWhileBuilding(ChunkRenderGpuUploader uploader)
    {
        if (RenderSystem.isOnRenderThread())
        {
            ((ChunkRenderGpuUploaderAccessor) uploader).turbo$clear();
        }
    }
}
