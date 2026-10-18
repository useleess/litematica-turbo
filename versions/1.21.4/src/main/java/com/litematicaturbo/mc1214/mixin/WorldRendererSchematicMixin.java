package com.litematicaturbo.mc1214.mixin;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.mc1214.GhostMove;
import com.litematicaturbo.mc1214.TurboDispatcher;
import com.litematicaturbo.mc1214.TurboRenderer;
import com.litematicaturbo.mc1214.TurboWorldRenderer;

@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicMixin implements TurboWorldRenderer
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;
    @Shadow private Set<ChunkRendererSchematicVbo> chunksToUpdate;
    @Shadow private ChunkRenderDispatcherLitematica renderDispatcher;
    @Shadow private ChunkRenderDispatcherSchematic chunkRendererDispatcher;
    @Shadow private boolean displayListEntitiesDirty;
    @Shadow private double lastTranslucentSortX;
    @Shadow private double lastTranslucentSortY;
    @Shadow private double lastTranslucentSortZ;

    /** The real visible chunks while the ghosts are being drawn in their place. */
    @Unique
    private final List<ChunkRendererSchematicVbo> turbo$savedRenderInfos = new ArrayList<>();
    @Unique
    private double turbo$savedSortX, turbo$savedSortY, turbo$savedSortZ;

    /** Chunks whose rebuild was requested while a worker was still building them (render thread only). */
    @Unique
    private final Set<ChunkRendererSchematicVbo> turbo$deferred = new LinkedHashSet<>();

    /**
     * Stock Litematica rebuilds every chunk within 32 blocks of the camera right away on the render
     * thread - a whole column per chunk, which is the stutter when standing in a big schematic.
     * Hand those to the worker threads too (nearest chunks are still built first).
     */
    @Redirect(method = { "setupTerrain", "updateChunks" }, expect = 2,
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkNow(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;Lnet/minecraft/util/profiler/Profiler;)Z"))
    private boolean turbo$updateNow(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer, Profiler profiler)
    {
        TurboConfig config = TurboConfig.get();

        if (config.threadingEnabled() == false)
        {
            return ((TurboDispatcher) dispatcher).turbo$updateChunkNow(renderer, profiler);
        }

        if (this.turbo$deferIfBuilding(renderer))
        {
            return true;
        }

        return config.asyncNearRebuildsEnabled() ? ((TurboDispatcher) dispatcher).turbo$updateChunkLater(renderer, profiler) : ((TurboDispatcher) dispatcher).turbo$updateChunkNow(renderer, profiler);
    }

    @Redirect(method = "updateChunks",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkLater(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;Lnet/minecraft/util/profiler/Profiler;)Z"))
    private boolean turbo$updateLater(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer, Profiler profiler)
    {
        if (TurboConfig.get().threadingEnabled() && this.turbo$deferIfBuilding(renderer))
        {
            return true;
        }

        return ((TurboDispatcher) dispatcher).turbo$updateChunkLater(renderer, profiler);
    }

    /**
     * Scheduling a rebuild waits on a lock that a running build of the same chunk holds for its whole
     * duration, which would freeze the render thread. Retry once that build has finished instead.
     */
    @Unique
    private boolean turbo$deferIfBuilding(ChunkRendererSchematicVbo renderer)
    {
        if (((TurboRenderer) renderer).turbo$isBuilding())
        {
            this.turbo$deferred.add(renderer);
            return true;
        }

        return false;
    }

    /** Block rendering asks for the render thread's profiler; build threads get a no-op one. */
    @Inject(method = "getProfiler", at = @At("HEAD"), cancellable = true)
    private void turbo$profilerOffThread(CallbackInfoReturnable<Profiler> cir)
    {
        if (RenderSystem.isOnRenderThread() == false)
        {
            cir.setReturnValue(DummyProfiler.INSTANCE);
        }
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void turbo$requeueDeferred(long finishTimeNano, Profiler profiler, CallbackInfo ci)
    {
        GhostMove.tick(this);

        if (this.turbo$deferred.isEmpty())
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
    public List<ChunkRendererSchematicVbo> turbo$detachChunkRenderers()
    {
        if (this.chunkRendererDispatcher == null)
        {
            return null;
        }

        Long2ObjectOpenHashMap<ChunkRendererSchematicVbo> map = ((ChunkRenderDispatcherSchematicAccessor) this.chunkRendererDispatcher).turbo$getChunkRenderers();
        List<ChunkRendererSchematicVbo> detached = new ArrayList<>(map.values());
        map.clear();
        this.renderInfos.clear();
        this.chunksToUpdate.clear();
        this.turbo$deferred.clear();
        this.displayListEntitiesDirty = true;
        return detached;
    }

    // While a placement is moved, the block layers and the overlay draw the shifted ghosts
    // instead of the real chunks (which are not rebuilt yet). Translucent re-sorting is paused
    // for that time (NaN never passes its distance check).

    @Inject(method = "renderBlockLayer*", at = @At("HEAD"))
    private void turbo$ghostsForLayer(CallbackInfoReturnable<Integer> cir)
    {
        this.turbo$swapInGhosts();
    }

    @Inject(method = "renderBlockLayer*", at = @At("RETURN"))
    private void turbo$restoreAfterLayer(CallbackInfoReturnable<Integer> cir)
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

    @Unique
    private void turbo$swapInGhosts()
    {
        if (GhostMove.isActive())
        {
            this.turbo$savedRenderInfos.clear();
            this.turbo$savedRenderInfos.addAll(this.renderInfos);
            this.renderInfos.clear();
            this.renderInfos.addAll(GhostMove.renderers());
            this.turbo$savedSortX = this.lastTranslucentSortX;
            this.turbo$savedSortY = this.lastTranslucentSortY;
            this.turbo$savedSortZ = this.lastTranslucentSortZ;
            this.lastTranslucentSortX = Double.NaN;
            this.lastTranslucentSortY = Double.NaN;
            this.lastTranslucentSortZ = Double.NaN;
        }
    }

    @Unique
    private void turbo$swapOutGhosts()
    {
        if (GhostMove.isActive() && Double.isNaN(this.lastTranslucentSortX))
        {
            this.renderInfos.clear();
            this.renderInfos.addAll(this.turbo$savedRenderInfos);
            this.turbo$savedRenderInfos.clear();
            this.lastTranslucentSortX = this.turbo$savedSortX;
            this.lastTranslucentSortY = this.turbo$savedSortY;
            this.lastTranslucentSortZ = this.turbo$savedSortZ;
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
