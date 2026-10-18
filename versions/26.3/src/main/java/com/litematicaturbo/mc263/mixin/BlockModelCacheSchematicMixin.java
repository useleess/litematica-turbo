package com.litematicaturbo.mc263.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

import fi.dy.masa.litematica.render.schematic.BlockModelCacheSchematic;

/**
 * Every chunk build gets its own model renderer, but they all seed and use one static random
 * generator (to pick block model variants), which is not thread-safe. Give each thread its own.
 */
@Mixin(value = BlockModelCacheSchematic.class, remap = false)
public abstract class BlockModelCacheSchematicMixin
{
    @Unique
    private static final ThreadLocal<SingleThreadedRandomSource> TURBO_RANDOM = ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(0L));

    @Inject(method = "rand", at = @At("HEAD"), cancellable = true)
    private void turbo$threadRandom(CallbackInfoReturnable<RandomSource> cir)
    {
        cir.setReturnValue(TURBO_RANDOM.get());
    }
}
