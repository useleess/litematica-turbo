package com.litematicaturbo.mc1165.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.litematicaturbo.mc1165.TurboSettingsTab;

/** Clicking one of Litematica's own tabs leaves the Turbo tab. */
@Mixin(targets = "fi.dy.masa.litematica.gui.GuiConfigs$ButtonListener", remap = false)
public abstract class GuiConfigsTabListenerMixin
{
    @Inject(method = "actionPerformedWithButton", at = @At("HEAD"))
    private void turbo$leaveTurboTab(CallbackInfo ci)
    {
        TurboSettingsTab.selected = false;
    }
}
