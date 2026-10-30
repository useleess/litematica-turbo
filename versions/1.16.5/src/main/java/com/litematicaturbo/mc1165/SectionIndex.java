package com.litematicaturbo.mc1165;

import net.minecraft.world.chunk.WorldChunk;

/** Index of a chunk section in its chunk (1.16: the section y itself). */
final class SectionIndex
{
    private SectionIndex()
    {
    }

    static int of(WorldChunk chunk, int sectionY)
    {
        return sectionY; // sections 0-15, no negative heights
    }
}
