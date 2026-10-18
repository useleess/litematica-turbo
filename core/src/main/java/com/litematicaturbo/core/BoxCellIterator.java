package com.litematicaturbo.core;

import java.util.NoSuchElementException;

/**
 * Walks the cells of a box but only inside the given y ranges, in the same order as vanilla's
 * BlockPos.iterate (x fastest, then y, then z). Keeping that order keeps the built vertex data in
 * the same order as stock Litematica, which matters for GPU cost (overdraw of the overlay).
 * Version adapters wrap it into an Iterator of their own BlockPos type.
 */
public final class BoxCellIterator
{
    private final int minX, maxX, minZ, maxZ;
    private final int[] yRanges;
    private int range;
    private int x, y, z;
    private boolean hasNext;

    public BoxCellIterator(int minX, int minZ, int maxX, int maxZ, int[] yRanges)
    {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.yRanges = yRanges;
        this.hasNext = yRanges.length >= 2 && maxX >= minX && maxZ >= minZ;

        if (this.hasNext)
        {
            this.x = minX;
            this.z = minZ;
            this.y = yRanges[0];
        }
    }

    public boolean hasNext()
    {
        return this.hasNext;
    }

    /** Writes the current cell into {@code outXyz} and moves on to the next one. */
    public void advance(int[] outXyz)
    {
        if (this.hasNext == false)
        {
            throw new NoSuchElementException();
        }

        outXyz[0] = this.x;
        outXyz[1] = this.y;
        outXyz[2] = this.z;

        if (++this.x > this.maxX)
        {
            this.x = this.minX;

            if (++this.y > this.yRanges[this.range * 2 + 1])
            {
                ++this.range;

                if (this.range * 2 >= this.yRanges.length)
                {
                    this.range = 0;

                    if (++this.z > this.maxZ)
                    {
                        this.hasNext = false;
                        return;
                    }
                }

                this.y = this.yRanges[this.range * 2];
            }
        }
    }
}
