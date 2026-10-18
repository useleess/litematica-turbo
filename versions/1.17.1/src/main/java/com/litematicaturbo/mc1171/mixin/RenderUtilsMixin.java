package com.litematicaturbo.mc1171.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Random;

import fi.dy.masa.litematica.render.RenderUtils;

/** Same as the model renderer: the overlay's model-outline helpers share one static random. */
@Mixin(value = RenderUtils.class, remap = false)
public abstract class RenderUtilsMixin
{
    @Unique
    private static final ThreadLocal<Random> TURBO_RANDOM = ThreadLocal.withInitial(Random::new);

    @Redirect(method = { "renderModelQuadOutlines", "drawBlockModelQuadOverlayBatched*" }, expect = 2,
              at = @At(value = "FIELD", target = "Lfi/dy/masa/litematica/render/RenderUtils;RAND", opcode = Opcodes.GETSTATIC))
    private static Random turbo$threadRandom()
    {
        return TURBO_RANDOM.get();
    }
}
