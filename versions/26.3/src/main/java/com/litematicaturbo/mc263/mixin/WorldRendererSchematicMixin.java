package com.litematicaturbo.mc263.mixin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuDispatcher;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.mc263.GhostMove;
import com.litematicaturbo.mc263.TurboDispatcher;
import com.litematicaturbo.mc263.TurboRenderer;
import com.litematicaturbo.mc263.TurboWorldRenderer;

@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicMixin implements TurboWorldRenderer
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;
    @Shadow private Set<ChunkRendererSchematicVbo> chunksToUpdate;
    @Shadow private ChunkRenderDispatcherLitematica renderDispatcher;
    @Shadow private ChunkRenderDispatcherSchematic chunkRendererDispatcher;
    @Shadow private ChunkRenderGpuDispatcher chunkRendererGpuDispatcher;
    @Shadow private boolean needsUpdate;

    /** The real visible chunks while the ghosts are being drawn in their place. */
    @Unique
    private final List<ChunkRendererSchematicVbo> turbo$savedRenderInfos = new ArrayList<>();

    /** Chunks whose rebuild was requested while a worker was still building them (render thread only). */
    @Unique
    private final Set<ChunkRendererSchematicVbo> turbo$deferred = new LinkedHashSet<>();

    /** Block rendering asks for the render thread's profiler; build threads get a no-op one. */
    @Inject(method = "getProfiler", at = @At("HEAD"), cancellable = true)
    private void turbo$profilerOffThread(CallbackInfoReturnable<ProfilerFiller> cir)
    {
        if (RenderSystem.isOnRenderThread() == false)
        {
            cir.setReturnValue(InactiveProfiler.INSTANCE);
        }
    }

    /**
     * Stock Litematica rebuilds every chunk within 32 blocks of the camera right away on the render
     * thread - a whole column per chunk, which is the stutter when standing in a big schematic.
     * Hand those to the build threads too (nearest chunks are still built first).
     */
    @Redirect(method = { "setupTerrain", "updateChunks" }, expect = 2,
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkNow(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;)Z"))
    private boolean turbo$updateNow(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer)
    {
        TurboConfig config = TurboConfig.get();
        TurboDispatcher turbo = (TurboDispatcher) dispatcher;

        if (config.threadingEnabled() == false)
        {
            return turbo.turbo$updateChunkNow(renderer);
        }

        if (this.turbo$deferIfBuilding(renderer))
        {
            return true;
        }

        return config.asyncNearRebuildsEnabled() ? turbo.turbo$updateChunkLater(renderer) : turbo.turbo$updateChunkNow(renderer);
    }

    @Redirect(method = "updateChunks",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkLater(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;)Z"))
    private boolean turbo$updateLater(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer)
    {
        if (TurboConfig.get().threadingEnabled() && this.turbo$deferIfBuilding(renderer))
        {
            return true;
        }

        return ((TurboDispatcher) dispatcher).turbo$updateChunkLater(renderer);
    }

    /**
     * Scheduling a rebuild waits on a lock that a running build of the same chunk holds for its whole
     * duration, which would freeze the render thread. Retry once that build has finished instead.
     * Also holds rebuilds back while a placement is being moved (ghost move).
     */
    @Unique
    private boolean turbo$deferIfBuilding(ChunkRendererSchematicVbo renderer)
    {
        // While a placement moves the ghosts are shown; rebuilds wait until it has settled
        if (((TurboRenderer) renderer).turbo$isBuilding() || GhostMove.isHoldingWork())
        {
            this.turbo$deferred.add(renderer);
            return true;
        }

        return false;
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void turbo$requeueDeferred(long finishTimeNano, ProfilerFiller profiler, CallbackInfo ci)
    {
        GhostMove.tick(this);

        if (this.turbo$deferred.isEmpty() || GhostMove.isHoldingWork())
        {
            return;
        }

        Iterator<ChunkRendererSchematicVbo> it = this.turbo$deferred.iterator();

        while (it.hasNext())
        {
            ChunkRendererSchematicVbo renderer = it.next();

            if (((TurboRenderer) renderer).turbo$isBuilding() == false)
            {
                ((TurboRenderer) renderer).turbo$setNeedsUpdate(false);
                this.chunksToUpdate.add(renderer);
                it.remove();
            }
        }
    }

    @Inject(method = { "setWorldAndLoadRenderers", "loadRenderers" }, at = @At("HEAD"))
    private void turbo$clearDeferred(CallbackInfo ci)
    {
        this.turbo$deferred.clear();
        GhostMove.reset();
    }

    @Override
    public Map<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> turbo$detachChunkRenderers()
    {
        if (this.chunkRendererDispatcher == null || this.chunkRendererGpuDispatcher == null)
        {
            return null;
        }

        ConcurrentHashMap<Long, ChunkRendererSchematicVbo> renderers = ((ChunkRenderDispatcherSchematicAccessor) this.chunkRendererDispatcher).turbo$getChunkRenderers();
        ConcurrentHashMap<Long, ChunkRenderGpuUploader> uploaders = ((ChunkRenderGpuDispatcherAccessor) this.chunkRendererGpuDispatcher).turbo$uploaders();
        Map<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> detached = new IdentityHashMap<>();

        for (ChunkRendererSchematicVbo renderer : new ArrayList<>(renderers.values()))
        {
            ChunkPos pos = ((ChunkRendererSchematicVboInvoker) renderer).turbo$getChunkPos();
            detached.put(renderer, uploaders.remove(ChunkPos.pack(pos.x(), pos.z())));
        }

        renderers.clear();
        this.renderInfos.clear();
        this.chunksToUpdate.clear();
        this.turbo$deferred.clear();
        this.needsUpdate = true;
        return detached;
    }

    // While a placement is moved, the block layers and the overlay draw the shifted ghosts (with the
    // GPU uploaders they were detached with) instead of the real chunks, which are not rebuilt yet.

    @Inject(method = "prepareBlockLayers", at = @At("HEAD"))
    private void turbo$ghostsForLayers(CallbackInfoReturnable<Integer> cir)
    {
        this.turbo$swapInGhosts();
    }

    @Inject(method = "prepareBlockLayers", at = @At("RETURN"))
    private void turbo$restoreAfterLayers(CallbackInfoReturnable<Integer> cir)
    {
        this.turbo$swapOutGhosts();
    }

    @Inject(method = "renderBlockOverlay", at = @At("HEAD"))
    private void turbo$ghostsForOverlay(CallbackInfo ci)
    {
        this.turbo$swapInGhosts();
    }

    @Inject(method = "renderBlockOverlay", at = @At("RETURN"))
    private void turbo$restoreAfterOverlay(CallbackInfo ci)
    {
        this.turbo$swapOutGhosts();
    }

    @Redirect(method = { "prepareBlockLayers", "renderBlockOverlay" }, expect = 2,
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderGpuDispatcher;addOrGetUploader(II)Lfi/dy/masa/litematica/render/schematic/ChunkRenderGpuUploader;"))
    private ChunkRenderGpuUploader turbo$ghostUploader(ChunkRenderGpuDispatcher dispatcher, int chunkX, int chunkZ)
    {
        ChunkRenderGpuUploader ghost = GhostMove.ghostUploader(chunkX, chunkZ);
        return ghost != null ? ghost : ((ChunkRenderGpuDispatcherInvoker) dispatcher).turbo$addOrGetUploader(chunkX, chunkZ);
    }

    @Unique
    private void turbo$swapInGhosts()
    {
        if (GhostMove.isActive())
        {
            this.turbo$savedRenderInfos.clear();
            this.turbo$savedRenderInfos.addAll(this.renderInfos);
            this.renderInfos.clear();
            this.renderInfos.addAll(GhostMove.renderers());
            GhostMove.setDrawingGhosts(true);
        }
    }

    @Unique
    private void turbo$swapOutGhosts()
    {
        if (GhostMove.isActive())
        {
            GhostMove.setDrawingGhosts(false);
            this.renderInfos.clear();
            this.renderInfos.addAll(this.turbo$savedRenderInfos);
            this.turbo$savedRenderInfos.clear();
        }
    }

    @Inject(method = "getDebugInfoRenders", at = @At("RETURN"), cancellable = true)
    private void turbo$appendStats(CallbackInfoReturnable<String> cir)
    {
        if (TurboConfig.get().showStats && TurboConfig.get().enabled && this.renderDispatcher != null)
        {
            TurboDispatcher info = (TurboDispatcher) this.renderDispatcher;
            cir.setReturnValue(cir.getReturnValue() + " | " + BuildStats.summary(info.turbo$threadCount(), info.turbo$queuedTasks()));
        }
    }

    @Override
    public boolean turbo$isIdle()
    {
        if (this.renderDispatcher == null || this.chunksToUpdate.isEmpty() == false || this.turbo$deferred.isEmpty() == false ||
            this.renderDispatcher.hasChunkUpdates() == false) // stock name: true means the queues are empty
        {
            return false;
        }

        for (ChunkRendererSchematicVbo renderer : this.renderInfos)
        {
            if (((TurboRenderer) renderer).turbo$needsUpdate() || ((TurboRenderer) renderer).turbo$isBuilding())
            {
                return false;
            }
        }

        return true;
    }
}
