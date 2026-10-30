package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.shared.TurboDispatcher;

/** Turbo's build statistics on the [Litematica] line of the F3 screen. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicStatsMixin
{
    @Shadow private ChunkRenderDispatcherLitematica renderDispatcher;

    @Inject(method = "getDebugInfoRenders", at = @At("RETURN"), cancellable = true)
    private void turbo$appendStats(CallbackInfoReturnable<String> cir)
    {
        if (TurboConfig.get().showStats && TurboConfig.get().enabled && this.renderDispatcher != null)
        {
            TurboDispatcher info = (TurboDispatcher) this.renderDispatcher;
            cir.setReturnValue(cir.getReturnValue() + " | " + BuildStats.summary(info.turbo$threadCount(), info.turbo$queuedTasks()));
        }
    }
}
