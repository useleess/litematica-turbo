package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.shared.BatchPiece;

@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboBatchMixin implements BatchPiece
{
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
}
