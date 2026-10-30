package com.litematicaturbo.shared;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

/**
 * The placement manager's queue of chunks to copy into the schematic world. Litematica for these versions has no
 * hasQueuedChunks(); Turbo adds one (TurboPlacementManager).
 */
public final class PlacementQueue
{
    private PlacementQueue()
    {
    }

    /** True while chunks are waiting to be copied into the schematic world. */
    public static boolean hasQueuedChunks(SchematicPlacementManager manager)
    {
        return ((TurboPlacementManager) manager).turbo$hasQueuedChunks();
    }
}
