package com.litematicaturbo.shared.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import fi.dy.masa.litematica.util.SchematicPlacingUtils;

import com.litematicaturbo.shared.SchematicCopy;

/** Starts each chunk of a placement copy (SchematicCopy); the arguments follow Litematica's method. */
@Mixin(value = SchematicPlacingUtils.class, remap = false)
public abstract class SchematicPlacingUtilsMixin
{
    // Only the arguments it needs (as locals): fits Litematica 0.19 - 0.24, also both signatures of 0.21 (0.21.3 added a parameter)
    @Inject(method = "placeBlocksWithinChunk", at = @At("HEAD"))
    private static void turbo$checkEmptyChunk(CallbackInfoReturnable<Boolean> cir,
                                              @Local(argsOnly = true) World world, @Local(argsOnly = true) ChunkPos chunkPos)
    {
        SchematicCopy.begin(world, chunkPos);
    }
}
