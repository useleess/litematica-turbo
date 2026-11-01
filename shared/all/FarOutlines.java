package com.litematicaturbo.shared;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.FarDetail;

/**
 * Less detail far away, game side: Litematica asks each listed piece whether it has outline lines to draw
 * ({@code WorldRendererSchematic.renderBlockOverlay}, every frame); a far piece answers no. Nothing is rebuilt, so a piece
 * that comes near again gets its lines back in the next frame. An error switches it off (see {@link FarDetail}).
 */
public final class FarOutlines
{
    private FarOutlines()
    {
    }

    /** Whether the outline lines of this piece, which has some, are left out this frame. */
    public static boolean hide(ChunkRendererSchematicVbo piece, double cameraX, double cameraZ, int renderDistanceChunks)
    {
        try
        {
            return FarDetail.hideOutlines(piece.getOrigin().getX(), piece.getOrigin().getZ(), cameraX, cameraZ, renderDistanceChunks);
        }
        catch (RuntimeException | LinkageError e)
        {
            FarDetail.switchOff("renderBlockOverlay", e);
            return false;
        }
    }
}
