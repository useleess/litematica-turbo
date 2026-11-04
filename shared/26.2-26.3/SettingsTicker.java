package com.litematicaturbo.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;

import com.litematicaturbo.core.BuildErrors;
import com.litematicaturbo.core.CompactStorage;
import com.litematicaturbo.core.CompleteCopy;
import com.litematicaturbo.core.FarDetail;

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

        String storageFailure = CompactStorage.takeFailureWarning(mc.player != null);

        if (storageFailure != null)
        {
            mc.gui.chatListener().handleSystemMessage(Component.translatable("litematica_turbo.message.storage_off", storageFailure).withStyle(ChatFormatting.GOLD), false);
        }

        String farFailure = FarDetail.takeFailureWarning(mc.player != null);

        if (farFailure != null)
        {
            mc.gui.chatListener().handleSystemMessage(Component.translatable("litematica_turbo.message.far_off", farFailure).withStyle(ChatFormatting.GOLD), false);
        }

        String completeFailure = CompleteCopy.takeFailureWarning(mc.player != null);

        if (completeFailure != null)
        {
            mc.gui.chatListener().handleSystemMessage(Component.translatable("litematica_turbo.message.complete_off", completeFailure).withStyle(ChatFormatting.GOLD), false);
        }

        String buildDropped = BuildErrors.takeWarning(mc.player != null);

        if (buildDropped != null)
        {
            mc.gui.chatListener().handleSystemMessage(Component.translatable("litematica_turbo.message.build_dropped", buildDropped).withStyle(ChatFormatting.GOLD), false);
        }

        TurboSettingsTab.applyPendingChanges();
    }
}
