package com.litematicaturbo.mc1165;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.WorldSchematic;

/** SchematicCopy's world and chunk calls on 1.16 (Litematica 0.0.0-dev). */
final class CopyCalls
{
    private CopyCalls()
    {
    }

    /** The schematic world's chunk there, or null when it is not loaded. */
    static ChunkSchematic loadedChunk(WorldSchematic world, ChunkPos pos)
    {
        return world.getChunkProvider().isChunkLoaded(pos.x, pos.z) ? world.getChunkProvider().getChunk(pos.x, pos.z) : null;
    }

    static boolean outsideHeight(World world, BlockPos pos)
    {
        return World.isOutOfBuildLimitVertically(pos);
    }

    static boolean place(ChunkSchematic chunk, BlockPos pos, BlockState state)
    {
        return chunk.setBlockState(pos, state, false) != null;
    }
}
