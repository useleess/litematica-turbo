package com.litematicaturbo.mc1204;

import java.util.Iterator;
import net.minecraft.util.math.BlockPos;

import com.litematicaturbo.core.BoxCellIterator;

/** The cells of a box limited to some y ranges, as BlockPos (one mutable instance, like BlockPos.iterate). */
public final class CellIterable implements Iterable<BlockPos>
{
    private final int minX, minZ, maxX, maxZ;
    private final int[] yRanges;

    public CellIterable(int minX, int minZ, int maxX, int maxZ, int[] yRanges)
    {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.yRanges = yRanges;
    }

    @Override
    public Iterator<BlockPos> iterator()
    {
        BoxCellIterator cells = new BoxCellIterator(this.minX, this.minZ, this.maxX, this.maxZ, this.yRanges);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int[] xyz = new int[3];

        return new Iterator<BlockPos>()
        {
            @Override
            public boolean hasNext()
            {
                return cells.hasNext();
            }

            @Override
            public BlockPos next()
            {
                cells.advance(xyz);
                return pos.set(xyz[0], xyz[1], xyz[2]);
            }
        };
    }
}
