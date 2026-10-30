package com.litematicaturbo.shared.mixin;

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
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.shared.GhostMove;
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

    @Unique
    private final RebuildQueue turbo$rebuilds = new RebuildQueue(false);

    /**
     * Stock Litematica rebuilds every chunk within 32 blocks of the camera right away on the render
     * thread; Turbo hands those to the build threads too (see RebuildQueue).
     */
    @Redirect(method = { "setupTerrain", "updateChunks" }, expect = 2,
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkNow(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;Lnet/minecraft/util/profiler/Profiler;)Z"))
    private boolean turbo$updateNow(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer, Profiler profiler)
    {
        return this.turbo$rebuilds.updateNow(renderer, r -> ((TurboDispatcher) dispatcher).turbo$updateChunkNow(r, profiler), r -> ((TurboDispatcher) dispatcher).turbo$updateChunkLater(r, profiler));
    }

    @Redirect(method = "updateChunks",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkLater(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;Lnet/minecraft/util/profiler/Profiler;)Z"))
    private boolean turbo$updateLater(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer, Profiler profiler)
    {
        return this.turbo$rebuilds.updateLater(renderer, r -> ((TurboDispatcher) dispatcher).turbo$updateChunkLater(r, profiler));
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
    public RebuildQueue turbo$rebuilds()
    {
        return this.turbo$rebuilds;
    }

    @Override
    public boolean turbo$isIdle()
    {
        return this.turbo$rebuilds.isIdle(this.renderDispatcher, this.chunksToUpdate, this.renderInfos);
    }
}
