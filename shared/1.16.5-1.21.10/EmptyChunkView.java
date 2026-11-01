package com.litematicaturbo.shared;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;

/** The "client world" a complete-copy piece compares with: all air, so every schematic block is built. */
public final class EmptyChunkView extends ChunkCacheSchematic
{
    public EmptyChunkView(World world, ClientWorld clientWorld, BlockPos pos)
    {
        super(world, clientWorld, pos, 0);
        this.empty = true;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return Blocks.AIR.getDefaultState();
    }
}
