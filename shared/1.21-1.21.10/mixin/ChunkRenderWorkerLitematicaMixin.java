package com.litematicaturbo.shared.mixin;

import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.profiler.Profiler;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BuildErrors;
import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.shared.CompletePiece;
import com.litematicaturbo.shared.DroppedBuilds;
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
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;rebuildChunk(Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;Lnet/minecraft/util/profiler/Profiler;)V"))
    private void turbo$rebuildChunk(ChunkRendererSchematicVbo renderer, ChunkRenderTaskSchematic task, Profiler profiler)
    {
        boolean onMainThread = MinecraftClient.getInstance().isOnThread();
        ReentrantLock lock = ((TurboRenderer) renderer).turbo$buildLock();
        lock.lock();
        long start = System.nanoTime();

        try
        {
            if (onMainThread == false)
            {
                BuildErrors.checkFaultForTest();
            }

            ((ChunkRendererSchematicVboInvoker) renderer).turbo$rebuildChunk(task, profiler);
        }
        catch (RuntimeException e)
        {
            if (onMainThread)
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
                BuildStats.recordBuild(System.nanoTime() - start, onMainThread);
            }
        }
    }

    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;resortTransparency(Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;Lnet/minecraft/util/profiler/Profiler;)V"))
    private void turbo$resortTransparency(ChunkRendererSchematicVbo renderer, ChunkRenderTaskSchematic task, Profiler profiler)
    {
        ReentrantLock lock = ((TurboRenderer) renderer).turbo$buildLock();
        lock.lock();

        try
        {
            ((ChunkRendererSchematicVboInvoker) renderer).turbo$resortTransparency(task, profiler);
        }
        finally
        {
            lock.unlock();
        }
    }
}
