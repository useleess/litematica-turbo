package com.litematicaturbo.mc1171.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.ScheduledTick;

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.ReplaceBehavior;
import fi.dy.masa.litematica.util.SchematicPlacingUtils;

import com.litematicaturbo.mc1171.SchematicCopy;

/** Starts each chunk of a placement copy (SchematicCopy); the arguments follow Litematica's method. */
@Mixin(value = SchematicPlacingUtils.class, remap = false)
public abstract class SchematicPlacingUtilsMixin
{
    @Inject(method = "placeBlocksWithinChunk", at = @At("HEAD"))
    private static void turbo$checkEmptyChunk(World world, ChunkPos chunkPos, String regionName,
                                              LitematicaBlockStateContainer container, Map<BlockPos, NbtCompound> blockEntityMap,
                                              BlockPos origin, SchematicPlacement schematicPlacement, SubRegionPlacement placement,
                                              Map<BlockPos, ScheduledTick<Block>> scheduledBlockTicks,
                                              Map<BlockPos, ScheduledTick<Fluid>> scheduledFluidTicks,
                                              ReplaceBehavior replace, boolean notifyNeighbors,
                                              CallbackInfoReturnable<Boolean> cir)
    {
        SchematicCopy.begin(world, chunkPos);
    }
}
