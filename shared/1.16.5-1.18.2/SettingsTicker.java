package com.litematicaturbo.shared;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;

import com.litematicaturbo.core.CompactStorage;

/** The settings tab's work on each client tick (see TurboSettingsTab), and the chat warnings of a switched-off Turbo. */
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
            mc.inGameHud.getChatHud().addMessage(new LiteralText(warning).formatted(Formatting.GOLD));
        }

        String copyFailure = SchematicCopy.takeFailureWarning(mc.player != null);

        if (copyFailure != null)
        {
            mc.inGameHud.getChatHud().addMessage(new TranslatableText("litematica_turbo.message.copy_off", copyFailure).formatted(Formatting.GOLD));
        }

        String storageFailure = CompactStorage.takeFailureWarning(mc.player != null);

        if (storageFailure != null)
        {
            mc.inGameHud.getChatHud().addMessage(new TranslatableText("litematica_turbo.message.storage_off", storageFailure).formatted(Formatting.GOLD));
        }

        TurboSettingsTab.applyPendingChanges();
    }
}
