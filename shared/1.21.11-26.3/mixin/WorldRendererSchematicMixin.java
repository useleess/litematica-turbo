package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
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

import com.litematicaturbo.shared.GhostMove;
import com.litematicaturbo.shared.ChunkKeys;
import com.litematicaturbo.shared.GhostRenderers;
import com.litematicaturbo.shared.RebuildQueue;
import com.litematicaturbo.shared.TurboDispatcher;
import com.litematicaturbo.shared.TurboWorldRenderer;

@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicMixin implements TurboWorldRenderer
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;
    @Shadow private Set<ChunkRendererSchematicVbo> chunksToUpdate;
    @Shadow private ChunkRenderDispatcherLitematica renderDispatcher;
    @Shadow private ChunkRenderDispatcherSchematic chunkRendererDispatcher;
    @Shadow private ChunkRenderGpuDispatcher chunkRendererGpuDispatcher;
    /** Renamed needsUpdate in Litematica 0.27+. */
    @Shadow(aliases = "needsUpdate") private boolean displayListEntitiesDirty;

    /** The real visible chunks while the ghosts are being drawn in their place. */
    @Unique
    private final List<ChunkRendererSchematicVbo> turbo$savedRenderInfos = new ArrayList<>();

    @Unique
    private final RebuildQueue turbo$rebuilds = new RebuildQueue(true);

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
     * thread; Turbo hands those to the build threads too (see RebuildQueue).
     */
    @Redirect(method = { "setupTerrain", "updateChunks" }, expect = 2,
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkNow(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;)Z"))
    private boolean turbo$updateNow(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer)
    {
        TurboDispatcher turbo = (TurboDispatcher) dispatcher;
        return this.turbo$rebuilds.updateNow(renderer, turbo::turbo$updateChunkNow, turbo::turbo$updateChunkLater);
    }

    @Redirect(method = "updateChunks",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkLater(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;)Z"))
    private boolean turbo$updateLater(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer)
    {
        TurboDispatcher turbo = (TurboDispatcher) dispatcher;
        return this.turbo$rebuilds.updateLater(renderer, turbo::turbo$updateChunkLater);
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void turbo$requeueDeferred(long finishTimeNano, ProfilerFiller profiler, CallbackInfo ci)
    {
        GhostRenderers.tick(this);
        this.turbo$rebuilds.requeue(this.chunksToUpdate);
    }

    @Inject(method = { "setWorldAndLoadRenderers", "loadRenderers" }, at = @At("HEAD"))
    private void turbo$clearDeferred(CallbackInfo ci)
    {
        this.turbo$rebuilds.clear();
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
            detached.put(renderer, uploaders.remove(ChunkKeys.of(pos)));
        }

        renderers.clear();
        this.renderInfos.clear();
        this.chunksToUpdate.clear();
        this.turbo$rebuilds.clear();
        this.displayListEntitiesDirty = true;
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
        ChunkRenderGpuUploader ghost = GhostRenderers.ghostUploader(chunkX, chunkZ);
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
            GhostRenderers.setDrawingGhosts(true);
        }
    }

    @Unique
    private void turbo$swapOutGhosts()
    {
        if (GhostMove.isActive())
        {
            GhostRenderers.setDrawingGhosts(false);
            this.renderInfos.clear();
            this.renderInfos.addAll(this.turbo$savedRenderInfos);
            this.turbo$savedRenderInfos.clear();
        }
    }

    @Override
    public boolean turbo$isIdle()
    {
        return this.turbo$rebuilds.isIdle(this.renderDispatcher, this.chunksToUpdate, this.renderInfos);
    }
}
