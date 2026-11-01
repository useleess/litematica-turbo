package com.litematicaturbo.shared;

import java.util.List;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/** The complete copy while moving does not exist for these Minecraft versions yet (it comes in a later step): a moved placement shows the ghosts. */
public final class CompletePieces
{
    private CompletePieces()
    {
    }

    public static void onMoveStart(SchematicPlacement target, List<ChunkRendererSchematicVbo> ghosts, List<int[]> ghostOrigins, int[] originBefore)
    {
    }

    public static void tick()
    {
    }

    public static boolean isBuilding()
    {
        return false;
    }

    public static void beforeWorldChange()
    {
    }

    public static List<ChunkRendererSchematicVbo> drawList(SchematicPlacement target, int[] ghostOrigin, List<ChunkRendererSchematicVbo> ghosts, List<int[]> ghostOrigins)
    {
        return ghosts;
    }

    public static void drop()
    {
    }
}
