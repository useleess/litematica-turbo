package com.litematicaturbo.mc12111.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;

/**
 * The model renderer is shared by every build thread, but its random generator (seeded per block
 * to pick model variants, also through getRandom()/setSeed()) is not thread-safe. Give each
 * thread its own.
 */
@Mixin(value = BlockModelRendererSchematic.class, remap = false)
public abstract class BlockModelRendererSchematicMixin
{
    @Unique
    private static final ThreadLocal<SingleThreadedRandomSource> TURBO_RANDOM = ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(42L));

    @Inject(method = "getRandom", at = @At("HEAD"), cancellable = true)
    private void turbo$threadRandom(CallbackInfoReturnable<SingleThreadedRandomSource> cir)
    {
        cir.setReturnValue(TURBO_RANDOM.get());
    }

    @Inject(method = "setSeed", at = @At("HEAD"), cancellable = true)
    private void turbo$threadSeed(long seed, CallbackInfo ci)
    {
        TURBO_RANDOM.get().setSeed(seed);
        ci.cancel();
    }

    @Redirect(method = "renderQuadsModel", expect = 2,
              at = @At(value = "FIELD", target = "Lfi/dy/masa/litematica/render/schematic/BlockModelRendererSchematic;random:Lnet/minecraft/world/level/levelgen/SingleThreadedRandomSource;", opcode = Opcodes.GETFIELD))
    private SingleThreadedRandomSource turbo$threadRandomField(BlockModelRendererSchematic self)
    {
        return TURBO_RANDOM.get();
    }
}
