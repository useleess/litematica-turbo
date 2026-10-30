package com.litematicaturbo.core;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * The cells of a box limited to some y ranges (see SectionSlices), as positions of the game: one mutable
 * position per iteration, moved to each cell in turn (like Minecraft's own box iteration).
 */
public final class BoxCells<P> implements Iterable<P>
{
    /** A mutable game position. */
    public interface Cursor<P>
    {
        P moveTo(int x, int y, int z);
    }

    private final int minX, minZ, maxX, maxZ;
    private final int[] yRanges;
    private final Supplier<Cursor<P>> newCursor;

    public BoxCells(int minX, int minZ, int maxX, int maxZ, int[] yRanges, Supplier<Cursor<P>> newCursor)
    {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.yRanges = yRanges;
        this.newCursor = newCursor;
    }

    @Override
    public Iterator<P> iterator()
    {
        BoxCellIterator cells = new BoxCellIterator(this.minX, this.minZ, this.maxX, this.maxZ, this.yRanges);
        Cursor<P> cursor = this.newCursor.get();
        int[] xyz = new int[3];

        return new Iterator<P>()
        {
            @Override
            public boolean hasNext()
            {
                return cells.hasNext();
            }

            @Override
            public P next()
            {
                cells.advance(xyz);
                return cursor.moveTo(xyz[0], xyz[1], xyz[2]);
            }
        };
    }
}
