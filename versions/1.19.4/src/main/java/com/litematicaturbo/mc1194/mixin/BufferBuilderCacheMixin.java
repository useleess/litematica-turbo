package com.litematicaturbo.mc1194.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;

import fi.dy.masa.litematica.render.schematic.BufferBuilderCache;
import fi.dy.masa.litematica.render.schematic.OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder;

/**
 * Litematica's buffer builders remember the last finished buffer and an upload uses it. Clearing a
 * cache (when it goes back to the pool) did not forget it, so a translucency re-sort that skipped
 * the overlay still uploaded the previous task's buffer - memory that another build has reset or
 * is writing again (crash with several build threads). Forget it with the rest of the cache.
 */
@Mixin(value = BufferBuilderCache.class, remap = false)
public abstract class BufferBuilderCacheMixin
{
    @Shadow @Final private Map<RenderLayer, OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder> blockBufferBuilders;
    @Shadow @Final private OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder[] overlayBufferBuilders;

    @Inject(method = "clear", at = @At("TAIL"))
    private void turbo$forgetLastBuffers(CallbackInfo ci)
    {
        for (BufferBuilder buffer : this.blockBufferBuilders.values())
        {
            turbo$forget(buffer);
        }

        for (BufferBuilder buffer : this.overlayBufferBuilders)
        {
            turbo$forget(buffer);
        }
    }

    @Unique
    private static void turbo$forget(BufferBuilder buffer)
    {
        if (buffer instanceof OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder)
        {
            OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder compat =
                    (OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder) buffer;
            compat.lastRenderBuildBuffer = null;
            compat.first = true; // its begin() only finishes a leftover build when this is false
        }
    }
}
