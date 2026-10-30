package com.litematicaturbo.shared;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;

import com.litematicaturbo.core.BoxCells;
import com.litematicaturbo.core.SectionSlices;
import com.litematicaturbo.shared.mixin.ChunkCacheSchematicAccessor;

/**
 * Each render chunk is a whole 16 x worldHeight x 16 column and stock Litematica looks at every
 * cell of the schematic boxes in it (two block lookups per cell, air included). Only visit the
 * 16-high sections where the schematic has blocks, or where the world has blocks that the
 * "extra block" overlay would mark. Skipped cells would have produced nothing; the cells that are
 * left are visited in vanilla's order. Minecraft's side of it; the maths is in core SectionSlices.
 */
public final class EmptySections
{
    private EmptySections()
    {
    }

    /** The cells from..to (one chunk column) that can hold something to draw. */
    public static Iterable<BlockPos> iterate(BlockPos from, BlockPos to, ChunkCacheSchematic schematicView, ChunkCacheSchematic clientView)
    {
        if (SectionSlices.skipApplies(from.getX(), from.getZ(), to.getX(), to.getZ()) == false)
        {
            return BlockPos.MutableBlockPos.betweenClosed(from, to);
        }

        int chunkX = from.getX() >> 4;
        int chunkZ = from.getZ() >> 4;
        LevelChunk schematicChunk = chunkAt(schematicView, chunkX, chunkZ);
        LevelChunk clientChunk = chunkAt(clientView, chunkX, chunkZ);
        boolean extraOverlay = Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.getBooleanValue() &&
                               Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_EXTRA.getBooleanValue();

        int[] ranges = SectionSlices.neededRanges(from.getY(), to.getY(), sectionY ->
                hasBlocks(schematicChunk, sectionY) || (extraOverlay && hasBlocks(clientChunk, sectionY)));

        return new BoxCells<>(from.getX(), from.getZ(), to.getX(), to.getZ(), ranges, () -> new BlockPos.MutableBlockPos()::set);
    }

    private static LevelChunk chunkAt(ChunkCacheSchematic view, int chunkX, int chunkZ)
    {
        if (view == null)
        {
            return null;
        }

        ChunkCacheSchematicAccessor cache = (ChunkCacheSchematicAccessor) view;
        return SectionSlices.cell(cache.turbo$getChunkArray(), chunkX - cache.turbo$getChunkStartX(), chunkZ - cache.turbo$getChunkStartZ());
    }

    private static boolean hasBlocks(LevelChunk chunk, int sectionY)
    {
        return chunk != null && SectionSlices.hasBlocks(chunk.getSections(), chunk.getSectionIndexFromSectionY(sectionY), section -> section.hasOnlyAir());
    }
}
