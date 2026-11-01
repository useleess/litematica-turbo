package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonExecutor;

import com.litematicaturbo.shared.GhostRenderers;

/** While the complete copy reads the old place, Litematica's background threads take no task (GhostRenderers.isCopyHeld). */
@Mixin(value = PlacementManagerDaemonExecutor.class, remap = false)
public abstract class PlacementManagerDaemonExecutorMixin
{
    /** A short wait per turn; false keeps the thread awake (a paused one would sleep until the next task is added). */
    @Inject(method = "loopSafe", at = @At("HEAD"), cancellable = true)
    private void turbo$waitForTheCopy(CallbackInfoReturnable<Boolean> cir)
    {
        if (GhostRenderers.isCopyHeld())
        {
            try
            {
                Thread.sleep(5);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            cir.setReturnValue(false);
        }
    }
}
