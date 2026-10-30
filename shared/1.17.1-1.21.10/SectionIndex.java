package com.litematicaturbo.shared;

import net.minecraft.world.chunk.WorldChunk;

/** Index of a chunk section in its chunk (the world may start below 0). */
final class SectionIndex
{
    private SectionIndex()
    {
    }

    static int of(WorldChunk chunk, int sectionY)
    {
        return chunk.sectionCoordToIndex(sectionY);
    }
}
