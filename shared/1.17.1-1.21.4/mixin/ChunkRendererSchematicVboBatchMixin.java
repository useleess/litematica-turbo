package com.litematicaturbo.shared.mixin;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.shared.BatchPiece;

@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboBatchMixin implements BatchPiece
{
    @Shadow public abstract ChunkRenderDataSchematic getChunkRenderData();
    @Shadow public abstract VertexBuffer getBlocksVertexBufferByLayer(RenderLayer layer);

    @Unique
    private int turbo$batchVersion;

    @Inject(method = "setChunkRenderData", at = @At("HEAD"))
    private void turbo$newData(CallbackInfo ci)
    {
        this.turbo$batchVersion++;
    }

    @Override
    public int turbo$batchVersion()
    {
        return this.turbo$batchVersion;
    }

    @Override
    public VertexBuffer turbo$buffer(RenderLayer layer)
    {
        return this.getChunkRenderData().isBlockLayerEmpty(layer) ? null : this.getBlocksVertexBufferByLayer(layer);
    }
}
