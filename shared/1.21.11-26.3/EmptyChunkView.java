package com.litematicaturbo.shared;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;

/** The "client world" a complete-copy piece compares with: all air, so every schematic block is built. */
public final class EmptyChunkView extends ChunkCacheSchematic
{
    public EmptyChunkView(Level world, ClientLevel clientWorld, BlockPos pos)
    {
        super(world, clientWorld, pos, 0);
        this.empty = true;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return Blocks.AIR.defaultBlockState();
    }
}
