package com.litematicaturbo.mc1165;

/** Added to Litematica's placement manager (0.11 has no hasQueuedChunks()). */
public interface TurboPlacementManager
{
    /** True while chunks are waiting to be copied into the schematic world. */
    boolean turbo$hasQueuedChunks();
}
