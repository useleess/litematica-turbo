package com.litematicaturbo.mc1171.mixin;

import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.MinecraftClient;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.mc1171.TurboRenderer;

/**
 * Runs each build/resort under the chunk's own lock, so a cancelled build still finishing on one
 * thread never overlaps a new build of the same chunk on another (they share scratch fields).
 * Also times builds for the F3 statistics.
 */
@Mixin(value = ChunkRenderWorkerLitematica.class, remap = false)
public abstract class ChunkRenderWorkerLitematicaMixin
{
    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;rebuildChunk(Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;)V"))
    private void turbo$rebuildChunk(ChunkRendererSchematicVbo renderer, ChunkRenderTaskSchematic task)
    {
        boolean onMainThread = MinecraftClient.getInstance().isOnThread();
        ReentrantLock lock = ((TurboRenderer) renderer).turbo$buildLock();
        lock.lock();
        long start = System.nanoTime();

        try
        {
            renderer.rebuildChunk(task);
        }
        catch (RuntimeException e)
        {
            // The chunk renderer was discarded (world unloaded, out of range) while this
            // cancelled build was still running on a worker; its result is thrown away anyway.
            if (onMainThread || task.isFinished() == false)
            {
                throw e;
            }
        }
        finally
        {
            lock.unlock();
            BuildStats.recordBuild(System.nanoTime() - start, onMainThread);
        }
    }

    @Redirect(method = "processTask",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;resortTransparency(Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;)V"))
    private void turbo$resortTransparency(ChunkRendererSchematicVbo renderer, ChunkRenderTaskSchematic task)
    {
        ReentrantLock lock = ((TurboRenderer) renderer).turbo$buildLock();
        lock.lock();

        try
        {
            renderer.resortTransparency(task);
        }
        finally
        {
            lock.unlock();
        }
    }
}
