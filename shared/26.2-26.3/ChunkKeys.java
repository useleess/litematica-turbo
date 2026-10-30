package com.litematicaturbo.shared;

import net.minecraft.world.level.ChunkPos;

/** A chunk position packed into one long (renamed between these versions). */
public final class ChunkKeys
{
    private ChunkKeys()
    {
    }

    public static long of(int x, int z)
    {
        return ChunkPos.pack(x, z);
    }

    public static long of(ChunkPos pos)
    {
        return ChunkPos.pack(pos.x(), pos.z());
    }
}
