package com.litematicaturbo.mc12111.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.litematica.gui.GuiConfigs;

import com.litematicaturbo.mc12111.TurboMixinGuard;
import com.litematicaturbo.mc12111.TurboSettingsTab;

/** Adds a "Turbo" tab after Litematica's own tabs in its config menu. */
@Mixin(value = GuiConfigs.class, remap = false)
public abstract class GuiConfigsMixin extends GuiConfigsBase
{
    /** Right edge of the last tab button Litematica created. */
    @Unique
    private int turbo$nextTabX;

    private GuiConfigsMixin()
    {
        super(0, 0, "", null, "");
    }

    @Inject(method = "createButton", at = @At("RETURN"))
    private void turbo$trackTabRow(int x, int y, int width, GuiConfigs.ConfigGuiTab tab, CallbackInfoReturnable<Integer> cir)
    {
        this.turbo$nextTabX = x + cir.getReturnValue();
    }

    /** Litematica greys out its selected tab; none of its tabs is selected while Turbo is. */
    @Redirect(method = "createButton", at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/gui/button/ButtonGeneric;setEnabled(Z)V"))
    private void turbo$tabEnabled(ButtonGeneric button, boolean enabled)
    {
        button.setEnabled(enabled || TurboSettingsTab.selected);
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void turbo$addTab(CallbackInfo ci)
    {
        if (this.turbo$nextTabX <= 0 || TurboMixinGuard.settingsTabOk() == false)
        {
            return; // Litematica opened its render layer screen instead
        }

        ButtonGeneric button = new ButtonGeneric(this.turbo$nextTabX, 26, -1, 20,
                StringUtils.translate("litematica_turbo.gui.button.config_gui.turbo"));
        button.setEnabled(TurboSettingsTab.selected == false);
        this.addButton(button, (b, mouseButton) -> {
            TurboSettingsTab.selected = true;
            TurboSettingsTab.syncFromConfig();
            this.reCreateListWidget();
            this.getListWidget().resetScrollbarPosition();
            this.initGui();
        });
        this.turbo$nextTabX = 0;
    }

    @Inject(method = "getConfigs", at = @At("HEAD"), cancellable = true)
    private void turbo$turboOptions(CallbackInfoReturnable<List<ConfigOptionWrapper>> cir)
    {
        if (TurboSettingsTab.selected)
        {
            TurboSettingsTab.syncFromConfig();
            cir.setReturnValue(ConfigOptionWrapper.createFor(TurboSettingsTab.OPTIONS));
        }
    }

    @Inject(method = "getConfigWidth", at = @At("HEAD"), cancellable = true)
    private void turbo$turboWidth(CallbackInfoReturnable<Integer> cir)
    {
        if (TurboSettingsTab.selected)
        {
            cir.setReturnValue(120);
        }
    }

    @Inject(method = "useKeybindSearch", at = @At("HEAD"), cancellable = true)
    private void turbo$noKeybindSearch(CallbackInfoReturnable<Boolean> cir)
    {
        if (TurboSettingsTab.selected)
        {
            cir.setReturnValue(false);
        }
    }

    /** Turbo settings are applied and saved as they change; skip Litematica's full re-render. */
    @Inject(method = "onSettingsChanged", at = @At("HEAD"), cancellable = true)
    private void turbo$ownSettings(CallbackInfo ci)
    {
        if (TurboSettingsTab.selected)
        {
            ci.cancel();
        }
    }
}
