package com.litematicaturbo.shared;

import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;

import com.litematicaturbo.core.SparseIntArray;

/**
 * Litematica's block-id storage of a region, kept in sparse 16 x 16 x 16 sections instead of one packed array for the
 * whole box. Reading and writing a block gives what the dense array gives; only {@link #getBackingLongArray} (saving)
 * has to build the packed array again.
 */
public final class SparseBitArray extends LitematicaBitArray
{
    private final int bits;
    private final SparseIntArray sparse;

    SparseBitArray(int bits, SparseIntArray sparse)
    {
        super(bits, 0L);
        this.bits = bits;
        this.sparse = sparse;
    }

    @Override
    public int getAt(long index)
    {
        return this.sparse.getAt(index);
    }

    @Override
    public void setAt(long index, int value)
    {
        this.sparse.setAt(index, value);
    }

    @Override
    public long[] getBackingLongArray()
    {
        return this.sparse.toPacked(this.bits);
    }

    @Override
    public long size()
    {
        return this.sparse.size();
    }

    /** Bytes held by the sections that exist (what the memory tests count). */
    public long allocatedBytes()
    {
        return this.sparse.allocatedBytes();
    }

    /** Bytes the packed array of the whole box would take. */
    public long denseBytes()
    {
        return this.sparse.denseBytes(this.bits);
    }
}
