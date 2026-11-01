package com.litematicaturbo.shared;

import java.util.List;

import fi.dy.masa.malilib.util.IntBoundingBox;

/** Added to Litematica's chunk renderer: what the complete copy needs of a piece. */
public interface CompletePiece
{
    /** The piece builds every schematic block of these boxes, compared with an all-air world, and has no overlay. */
    void turbo$makeComplete(List<IntBoundingBox> boxes);

    /** A piece of the complete copy, not of the view: its builds are not view rebuilds and stay out of the build statistics. */
    boolean turbo$isComplete();

    /** The boxes the piece was last built from. */
    List<IntBoundingBox> turbo$boxes();

    /** Counts how often the piece got finished build data. */
    int turbo$dataGeneration();

    /** How many blocks the piece's last build drew: the schematic's blocks in its boxes where the world compared with has air. */
    long turbo$drawnBlocks();
}
