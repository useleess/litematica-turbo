package com.litematicaturbo.shared;

import fi.dy.masa.malilib.util.position.IntBoundingBox;

/** Litematica's placement boxes for the complete copy; the box class is in malilib's position package here. */
public final class CompleteBoxes
{
    private CompleteBoxes()
    {
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} of one of Litematica's boxes. */
    public static int[] bounds(Object box)
    {
        IntBoundingBox b = (IntBoundingBox) box;
        return new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }
}
