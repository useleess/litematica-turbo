package com.litematicaturbo.mc1204.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.util.math.random.LocalRandom;

import fi.dy.masa.litematica.render.RenderUtils;

/** Same as the model renderer: the overlay's model-outline helpers share one static random. */
@Mixin(value = RenderUtils.class, remap = false)
public abstract class RenderUtilsMixin
{
    @Unique
    private static final ThreadLocal<LocalRandom> TURBO_RANDOM = ThreadLocal.withInitial(() -> new LocalRandom(0));

    @Redirect(method = { "renderModelQuadOutlines", "drawBlockModelQuadOverlayBatched*" }, expect = 2,
              at = @At(value = "FIELD", target = "Lfi/dy/masa/litematica/render/RenderUtils;RAND", opcode = Opcodes.GETSTATIC))
    private static LocalRandom turbo$threadRandom()
    {
        return TURBO_RANDOM.get();
    }
}
