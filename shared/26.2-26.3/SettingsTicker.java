package com.litematicaturbo.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;

/** The settings tab's work on each client tick (see TurboSettingsTab), and its chat warning. */
final class SettingsTicker implements IClientTickHandler
{
    static final SettingsTicker INSTANCE = new SettingsTicker();

    private SettingsTicker()
    {
    }

    @Override
    public void onClientTick(Minecraft mc)
    {
        String warning = TurboSettingsTab.takeIncompatibilityWarning(mc.player != null);

        if (warning != null)
        {
            mc.gui.chatListener().handleSystemMessage(Component.literal(warning).withStyle(ChatFormatting.GOLD), false);
        }

        TurboSettingsTab.applyPendingChanges();
    }
}
