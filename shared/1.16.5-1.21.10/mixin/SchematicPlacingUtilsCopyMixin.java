package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.util.SchematicPlacingUtils;

import com.litematicaturbo.shared.SchematicCopy;

/** Copying a placement into Litematica's schematic world (SchematicCopy); SchematicPlacingUtilsMixin starts each chunk. */
@Mixin(value = SchematicPlacingUtils.class, remap = false)
public abstract class SchematicPlacingUtilsCopyMixin
{
    @Inject(method = "placeBlocksWithinChunk", at = @At("RETURN"))
    private static void turbo$reset(CallbackInfoReturnable<Boolean> cir)
    {
        SchematicCopy.end();
    }

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private static BlockState turbo$getBlockState(World world, BlockPos pos)
    {
        return SchematicCopy.getBlockState(world, pos);
    }

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;getBlockEntity(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/entity/BlockEntity;"))
    private static BlockEntity turbo$getBlockEntity(World world, BlockPos pos)
    {
        return SchematicCopy.getBlockEntity(world, pos);
    }

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private static boolean turbo$setBlockState(World world, BlockPos pos, BlockState state, int flags)
    {
        return SchematicCopy.setBlockState(world, pos, state, flags);
    }

    @Redirect(method = "placeBlocksWithinChunk",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/container/LitematicaBlockStateContainer;get"))
    private static BlockState turbo$skipAirIntoEmptyChunk(LitematicaBlockStateContainer container, int x, int y, int z)
    {
        return SchematicCopy.skipAir(container.get(x, y, z));
    }
}
