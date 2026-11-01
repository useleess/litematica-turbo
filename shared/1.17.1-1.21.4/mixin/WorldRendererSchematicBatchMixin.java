package com.litematicaturbo.shared.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.shared.BatchDraw;
import com.litematicaturbo.shared.BlockLayers;

/** The end of batch drawing's part in a layer (the pieces drawn in regions come back into the list), and the reload. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicBatchMixin
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;

    @Inject(method = BlockLayers.METHOD, at = @At("RETURN"), cancellable = true)
    private void turbo$restorePieces(CallbackInfoReturnable<Integer> cir)
    {
        cir.setReturnValue(BatchDraw.end(this.renderInfos, cir.getReturnValue()));
    }

    @Inject(method = { "setWorldAndLoadRenderers", "loadRenderers" }, at = @At("HEAD"))
    private void turbo$freeRegions(CallbackInfo ci)
    {
        BatchDraw.clear();
    }
}
