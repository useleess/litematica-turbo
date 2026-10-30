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

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.shared.GhostMove;
import com.litematicaturbo.shared.GhostRenderers;
import com.litematicaturbo.shared.RebuildQueue;
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
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkNow(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;)Z"))
    private boolean turbo$updateNow(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer)
    {
        return this.turbo$rebuilds.updateNow(renderer, r -> dispatcher.updateChunkNow(r), r -> dispatcher.updateChunkLater(r));
    }

    @Redirect(method = "updateChunks",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkLater(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;)Z"))
    private boolean turbo$updateLater(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer)
    {
        return this.turbo$rebuilds.updateLater(renderer, r -> dispatcher.updateChunkLater(r));
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void turbo$requeueDeferred(long finishTimeNano, CallbackInfo ci)
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
