package com.litematicaturbo.mc1201.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.tick.OrderedTick;

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.ReplaceBehavior;
import fi.dy.masa.litematica.util.SchematicPlacingUtils;
import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.TurboConfig;

/**
 * Copying a placement into Litematica's schematic world visits every cell of the region boxes in a
 * chunk, air included, and each cell costs several hash-map lookups (block entity map, chunk map,
 * block entity checks). That runs on the render thread and was most of the remaining stutter.
 *
 * When the target chunk is still completely empty (freshly loaded, which is the normal case), an
 * air cell can never change anything: every cell maps to its own world position, so there is no
 * block or block entity there to replace. Those cells are handed to the loop as structure void,
 * which it already skips.
 */
@Mixin(value = SchematicPlacingUtils.class, remap = false)
public abstract class SchematicPlacingUtilsMixin
{
    @Unique
    private static final ThreadLocal<Boolean> TURBO_SKIP_AIR = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** The schematic chunk being filled; every position the loop touches lies inside it. */
    @Unique
    private static final ThreadLocal<ChunkSchematic> TURBO_CHUNK = new ThreadLocal<>();

    @Inject(method = "placeBlocksWithinChunk", at = @At("HEAD"))
    private static void turbo$checkEmptyChunk(World world, ChunkPos chunkPos, String regionName,
                                              LitematicaBlockStateContainer container, Map<BlockPos, NbtCompound> blockEntityMap,
                                              BlockPos origin, SchematicPlacement schematicPlacement, SubRegionPlacement placement,
                                              Map<BlockPos, OrderedTick<Block>> scheduledBlockTicks,
                                              Map<BlockPos, OrderedTick<Fluid>> scheduledFluidTicks,
                                              ReplaceBehavior replace, boolean notifyNeighbors,
                                              CallbackInfoReturnable<Boolean> cir)
    {
        ChunkSchematic chunk = null;

        if (TurboConfig.get().enabled && world instanceof WorldSchematic schematicWorld)
        {
            chunk = schematicWorld.getChunkProvider().getChunkIfExists(chunkPos.x, chunkPos.z);
        }

        boolean skip = chunk != null && chunk.isEmpty();
        TURBO_CHUNK.set(chunk);
        TURBO_SKIP_AIR.set(skip);
        BuildStats.placeCalls.incrementAndGet();

        if (skip)
        {
            BuildStats.placeCallsFast.incrementAndGet();
        }
    }

    @Inject(method = "placeBlocksWithinChunk", at = @At("RETURN"))
    private static void turbo$reset(CallbackInfoReturnable<Boolean> cir)
    {
        TURBO_SKIP_AIR.set(Boolean.FALSE);
        TURBO_CHUNK.remove();
    }

    @Unique
    private static ChunkSchematic turbo$chunkFor(BlockPos pos)
    {
        ChunkSchematic chunk = TURBO_CHUNK.get();

        if (chunk != null && (pos.getX() >> 4) == chunk.getPos().x && (pos.getZ() >> 4) == chunk.getPos().z)
        {
            return chunk;
        }

        return null;
    }

    // The loop's world calls each look the chunk up in a hash map again (and getBlockEntity also
    // checks height limits and dimension). Go to the already known chunk directly; this is exactly
    // what WorldSchematic does after its lookup.

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private static BlockState turbo$getBlockState(World world, BlockPos pos)
    {
        ChunkSchematic chunk = turbo$chunkFor(pos);
        return chunk != null ? chunk.getBlockState(pos) : world.getBlockState(pos);
    }

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getBlockEntity(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/entity/BlockEntity;"))
    private static BlockEntity turbo$getBlockEntity(World world, BlockPos pos)
    {
        ChunkSchematic chunk = turbo$chunkFor(pos);

        // The schematic chunk creates a block entity whenever it places a block that has one,
        // so with none in the map there is none to find or to create here either.
        if (chunk != null && chunk.getBlockEntities().isEmpty())
        {
            return null;
        }

        return world.getBlockEntity(pos);
    }

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private static boolean turbo$setBlockState(World world, BlockPos pos, BlockState state, int flags)
    {
        ChunkSchematic chunk = turbo$chunkFor(pos);

        if (chunk == null)
        {
            return world.setBlockState(pos, state, flags);
        }

        if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY())
        {
            return false;
        }

        return chunk.setBlockState(pos, state, false) != null;
    }

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/container/LitematicaBlockStateContainer;get"))
    private static BlockState turbo$skipAirIntoEmptyChunk(LitematicaBlockStateContainer container, int x, int y, int z)
    {
        BlockState state = container.get(x, y, z);

        if (state.isAir() && TURBO_SKIP_AIR.get())
        {
            return Blocks.STRUCTURE_VOID.getDefaultState();
        }

        return state;
    }
}
