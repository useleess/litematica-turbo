package com.litematicaturbo.shared.mixin;

import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.BatchDrawing;
import com.litematicaturbo.shared.BatchDraw;
import com.litematicaturbo.shared.BlockLayers;
import com.litematicaturbo.shared.ChunkOffsetPlacement;

/** The two points of batch drawing in a block layer: before the pieces are drawn, and while the shader is still set up. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicBatchLayerMixin
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;

    @Inject(method = BlockLayers.METHOD, at = @At("HEAD"))
    private void turbo$planRegions(RenderLayer layer, MatrixStack matrices, Camera camera, Matrix4f projMatrix, CallbackInfoReturnable<Integer> cir)
    {
        BatchDraw.begin(layer, camera.getPos(), this.renderInfos);
    }

    @Inject(method = BlockLayers.METHOD,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/client/gl/ShaderProgram;unbind()V"))
    private void turbo$drawRegions(CallbackInfoReturnable<Integer> cir)
    {
        BatchDraw.drawRegions(ChunkOffsetPlacement.of(RenderSystem.getShader(), shader -> shader.chunkOffset));
    }

    /** Counts the draw calls Litematica itself makes (pieces no region holds, see-through layers), for the tests. */
    @Inject(method = BlockLayers.METHOD, require = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/client/gl/VertexBuffer;draw()V"))
    private void turbo$countStockDraw(CallbackInfoReturnable<Integer> cir)
    {
        BatchDrawing.countDrawCall();
    }
}
