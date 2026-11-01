package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.shared.FarOutlines;

/** Less detail far away (see FarOutlines); the overlay type is its own class from Litematica for 1.21. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicFarMixin
{
    @Shadow private double lastCameraX;
    @Shadow private double lastCameraZ;
    @Shadow private int renderDistanceChunks;

    /** The piece whose overlay Litematica is looking at. */
    @Unique
    private ChunkRendererSchematicVbo turbo$overlayPiece;

    @Redirect(method = "renderBlockOverlay", at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;hasOverlay()Z"))
    private boolean turbo$notePiece(ChunkRendererSchematicVbo piece)
    {
        this.turbo$overlayPiece = piece;
        return piece.hasOverlay();
    }

    @Redirect(method = "renderBlockOverlay", at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDataSchematic;isOverlayTypeEmpty(Lfi/dy/masa/litematica/render/schematic/OverlayRenderType;)Z"))
    private boolean turbo$hideFarOutlines(ChunkRenderDataSchematic data, OverlayRenderType type)
    {
        return data.isOverlayTypeEmpty(type)
            || (type == OverlayRenderType.OUTLINE && FarOutlines.hide(this.turbo$overlayPiece, this.lastCameraX, this.lastCameraZ, this.renderDistanceChunks));
    }
}
