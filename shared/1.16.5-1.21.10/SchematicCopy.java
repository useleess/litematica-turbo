package com.litematicaturbo.shared;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Set;
import com.google.common.collect.ArrayListMultimap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;

/**
 * Copying a placement into Litematica's schematic world visits every cell of the region boxes in a
 * chunk, air included, and each cell costs several hash-map lookups (block entity map, chunk map,
 * block entity checks). That runs on the render thread and was most of the remaining stutter.
 *
 * When the target chunk is still completely empty (freshly loaded, which is the normal case), an
 * air cell can never change anything: every cell maps to its own world position, so there is no
 * block or block entity there to replace. Those cells are handed to the loop as structure void,
 * which it already skips.
 *
 * Safety: if one of these calls into the game throws, the copy path switches itself off for the session (the
 * player is told once in the chat, the log gets the details) and that call and all later ones go the plain
 * Litematica way, so the placement still ends up exactly as without Turbo.
 */
public final class SchematicCopy
{
    private static final ThreadLocal<Boolean> SKIP_AIR = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** The schematic chunk being filled; every position the loop touches lies inside it. */
    private static final ThreadLocal<ChunkSchematic> CHUNK = new ThreadLocal<>();

    /** Why the copy path switched itself off this session; null while it works. */
    private static volatile String failure;
    private static volatile boolean warned;

    private SchematicCopy()
    {
    }

    /** placeBlocksWithinChunk starts filling this chunk. */
    public static void begin(World world, ChunkPos chunkPos)
    {
        ChunkSchematic chunk = null;

        if (active() && world instanceof WorldSchematic)
        {
            try
            {
                chunk = CopyCalls.loadedChunk((WorldSchematic) world, chunkPos);
            }
            catch (RuntimeException | LinkageError e)
            {
                switchOff("begin", e);
            }
        }

        boolean skip = chunk != null && chunk.isEmpty();
        CHUNK.set(chunk);
        SKIP_AIR.set(skip);
        BuildStats.placeCalls.incrementAndGet();

        if (skip)
        {
            BuildStats.placeCallsFast.incrementAndGet();
        }
    }

    /** placeBlocksWithinChunk returns. */
    public static void end()
    {
        SKIP_AIR.set(Boolean.FALSE);
        CHUNK.remove();
    }

    private static boolean active()
    {
        return TurboConfig.get().enabled && failure == null;
    }

    private static void switchOff(String call, Throwable t)
    {
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        TurboLog.error("The fast placement copy failed in " + call + " and is off for this session; placements are copied the plain Litematica way:\n" + trace);
        failure = t.toString();
        CHUNK.remove();
        SKIP_AIR.set(Boolean.FALSE);
    }

    /** The reason for the one chat message after the copy path switched itself off; null when none is due. */
    static String takeFailureWarning(boolean playerPresent)
    {
        if (warned == false && failure != null && playerPresent)
        {
            warned = true;
            return failure;
        }

        return null;
    }

    private static ChunkSchematic chunkFor(BlockPos pos)
    {
        ChunkSchematic chunk = CHUNK.get();

        if (chunk != null && (pos.getX() >> 4) == chunk.getPos().x && (pos.getZ() >> 4) == chunk.getPos().z)
        {
            return chunk;
        }

        return null;
    }

    // The loop's world calls each look the chunk up in a hash map again (and getBlockEntity also
    // checks height limits and dimension). Go to the already known chunk directly; this is exactly
    // what WorldSchematic does after its lookup.

    public static BlockState getBlockState(World world, BlockPos pos)
    {
        ChunkSchematic chunk = chunkFor(pos);

        if (chunk != null)
        {
            try
            {
                return chunk.getBlockState(pos);
            }
            catch (RuntimeException | LinkageError e)
            {
                switchOff("getBlockState", e);
            }
        }

        return world.getBlockState(pos);
    }

    public static BlockEntity getBlockEntity(World world, BlockPos pos)
    {
        ChunkSchematic chunk = chunkFor(pos);

        // The schematic chunk creates a block entity whenever it places a block that has one,
        // so with none in the map there is none to find or to create here either.
        if (chunk != null)
        {
            try
            {
                if (chunk.getBlockEntities().isEmpty())
                {
                    return null;
                }
            }
            catch (RuntimeException | LinkageError e)
            {
                switchOff("getBlockEntity", e);
            }
        }

        return world.getBlockEntity(pos);
    }

    public static boolean setBlockState(World world, BlockPos pos, BlockState state, int flags)
    {
        ChunkSchematic chunk = chunkFor(pos);

        if (chunk == null)
        {
            return world.setBlockState(pos, state, flags);
        }

        try
        {
            if (CopyCalls.outsideHeight(world, pos))
            {
                return false;
            }

            return CopyCalls.place(chunk, pos, state);
        }
        catch (RuntimeException | LinkageError e)
        {
            switchOff("setBlockState", e);
            return world.setBlockState(pos, state, flags);
        }
    }

/**
     * Every chunk packet the client receives queues that chunk, and the queue wipes the schematic chunk
     * and copies all placements into it again - even when it is already loaded and nothing about the
     * placements changed (flying around, chunk resends). Only the render needs refreshing then, because
     * the real-world blocks it is compared against changed. Explicit rebuild requests (placement moved,
     * schematic edited, ...) are in chunksToRebuild and still take the full path.
     */
    public static void refreshInsteadOfReplacing(Set<ChunkPos> rebuildQueue, Set<ChunkPos> toRebuild,
                                                 ArrayListMultimap<ChunkPos, SchematicPlacement> touching, WorldSchematic world)
    {
        if (active() == false || rebuildQueue.isEmpty())
        {
            return;
        }

        if (world == null)
        {
            return;
        }

        Iterator<ChunkPos> it = rebuildQueue.iterator();

        while (it.hasNext())
        {
            ChunkPos pos = it.next();

            if (toRebuild.contains(pos) == false &&
                touching.containsKey(pos) &&
                CopyCalls.loadedChunk(world, pos) != null)
            {
                world.scheduleChunkRenders(pos.x, pos.z);
                BuildStats.placeCallsSkipped.incrementAndGet();
                it.remove();
            }
        }
    }

    /** Air copied into a still empty chunk changes nothing: the loop skips it as structure void. */
    public static BlockState skipAir(BlockState state)
    {
        if (state.isAir() && SKIP_AIR.get())
        {
            return Blocks.STRUCTURE_VOID.getDefaultState();
        }

        return state;
    }
}
