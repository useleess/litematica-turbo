package com.litematicaturbo.mc262.mixin;

import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.systems.RenderSystem;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.mc262.TurboRenderer;

/**
 * Runs each build/resort under the chunk's own lock, so a cancelled build still finishing on one
 * thread never overlaps a new build of the same chunk on another (they share scratch fields).
 * Also times builds for the F3 statistics.
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

        try
        {
            ((ChunkRendererSchematicVboInvoker) renderer).turbo$rebuildChunk(task, pack);
        }
        catch (RuntimeException e)
        {
            // The chunk renderer was discarded (world unloaded, out of range) while this
            // cancelled build was still running on a worker; its result is thrown away anyway.
            if (onRenderThread || ((ChunkRenderTaskSchematicInvoker) task).turbo$isFinished() == false)
            {
                throw e;
            }
        }
        finally
        {
            lock.unlock();
            BuildStats.recordBuild(System.nanoTime() - start, onRenderThread);
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
