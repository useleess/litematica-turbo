package com.litematicaturbo.shared.mixin;

import java.util.List;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.BatchDrawing;
import com.litematicaturbo.shared.BatchDraw;
import com.litematicaturbo.shared.BlockLayers;

/** Draws the opaque block layers region by region (BatchDraw); every other layer, and any error, keeps the stock drawing. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicBatchMixin
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;

    @Inject(method = BlockLayers.METHOD, at = @At("HEAD"), cancellable = true)
    private void turbo$drawBatched(RenderLayer layer, MatrixStack matrices, Camera camera, CallbackInfoReturnable<Integer> cir)
    {
        Integer drawn = BatchDraw.drawLayer(layer, matrices, camera, this.renderInfos);

        if (drawn != null)
        {
            cir.setReturnValue(drawn);
        }
    }

    /** Counts the stock draw calls (see-through layers, batch drawing off) for the tests. */
    @Inject(method = BlockLayers.METHOD, require = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/client/gl/VertexBuffer;draw(Lnet/minecraft/util/math/Matrix4f;I)V"))
    private void turbo$countStockDraw(RenderLayer layer, MatrixStack matrices, Camera camera, CallbackInfoReturnable<Integer> cir)
    {
        BatchDrawing.countDrawCall();
    }

    @Inject(method = { "setWorldAndLoadRenderers", "loadRenderers" }, at = @At("HEAD"))
    private void turbo$freeRegions(CallbackInfo ci)
    {
        BatchDraw.clear();
    }
}
