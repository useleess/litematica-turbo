package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.shared.BlockLayers;
import com.litematicaturbo.shared.GhostMove;

/** Draws the ghosts of a placement being moved (GhostMove) in place of the real chunks. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicGhostMixin
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;
    @Shadow private double lastTranslucentSortX;
    @Shadow private double lastTranslucentSortY;
    @Shadow private double lastTranslucentSortZ;

    /** The real visible chunks while the ghosts are being drawn in their place. */
    @Unique
    private final List<ChunkRendererSchematicVbo> turbo$savedRenderInfos = new ArrayList<>();
    @Unique
    private double turbo$savedSortX, turbo$savedSortY, turbo$savedSortZ;

    // While a placement is moved, the block layers and the overlay draw the shifted ghosts
    // instead of the real chunks (which are not rebuilt yet). Translucent re-sorting is paused
    // for that time (NaN never passes its distance check).

    @Inject(method = BlockLayers.METHOD, at = @At("HEAD"))
    private void turbo$ghostsForLayer(CallbackInfoReturnable<Integer> cir)
    {
        this.turbo$swapInGhosts();
    }

    @Inject(method = BlockLayers.METHOD, at = @At("RETURN"))
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
}
