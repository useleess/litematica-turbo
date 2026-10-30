package com.litematicaturbo.shared;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.WorldSchematic;

/** SchematicCopy's world and chunk calls on these versions. */
final class CopyCalls
{
    private CopyCalls()
    {
    }

    /** The schematic world's chunk there, or null when it is not loaded. */
    static ChunkSchematic loadedChunk(WorldSchematic world, ChunkPos pos)
    {
        return world.getChunkProvider().getChunkIfExists(pos.x, pos.z);
    }

    static boolean outsideHeight(World world, BlockPos pos)
    {
        return pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY();
    }

    static boolean place(ChunkSchematic chunk, BlockPos pos, BlockState state)
    {
        return chunk.setBlockState(pos, state, false) != null;
    }
}
