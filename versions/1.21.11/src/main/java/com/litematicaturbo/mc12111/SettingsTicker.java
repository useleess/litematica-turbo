package com.litematicaturbo.mc12111;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;

import com.litematicaturbo.core.CompactStorage;

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
            mc.gui.getChat().addMessage(Component.literal(warning).withStyle(ChatFormatting.GOLD));
        }

        String storageFailure = CompactStorage.takeFailureWarning(mc.player != null);

        if (storageFailure != null)
        {
            mc.gui.getChat().addMessage(Component.translatable("litematica_turbo.message.storage_off", storageFailure).withStyle(ChatFormatting.GOLD));
        }

        TurboSettingsTab.applyPendingChanges();
    }
}
