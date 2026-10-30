package com.litematicaturbo.shared;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;

/** The settings tab's work on each client tick (see TurboSettingsTab), and its chat warning. */
final class SettingsTicker implements IClientTickHandler
{
    static final SettingsTicker INSTANCE = new SettingsTicker();

    private SettingsTicker()
    {
    }

    @Override
    public void onClientTick(MinecraftClient mc)
    {
        String warning = TurboSettingsTab.takeIncompatibilityWarning(mc.player != null);

        if (warning != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.literal(warning).formatted(Formatting.GOLD));
        }

        TurboSettingsTab.applyPendingChanges();
    }
}
