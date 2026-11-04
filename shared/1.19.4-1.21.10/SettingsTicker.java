package com.litematicaturbo.shared;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;

import com.litematicaturbo.core.BatchDrawing;
import com.litematicaturbo.core.BuildErrors;
import com.litematicaturbo.core.CompactStorage;
import com.litematicaturbo.core.CompleteCopy;
import com.litematicaturbo.core.FarDetail;

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
            mc.inGameHud.getChatHud().addMessage(Text.literal(warning).formatted(Formatting.GOLD));
        }

        String copyFailure = SchematicCopy.takeFailureWarning(mc.player != null);

        if (copyFailure != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.translatable("litematica_turbo.message.copy_off", copyFailure).formatted(Formatting.GOLD));
        }

        String storageFailure = CompactStorage.takeFailureWarning(mc.player != null);

        if (storageFailure != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.translatable("litematica_turbo.message.storage_off", storageFailure).formatted(Formatting.GOLD));
        }

        String batchFailure = BatchDrawing.takeFailureWarning(mc.player != null);

        if (batchFailure != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.translatable("litematica_turbo.message.batch_off", batchFailure).formatted(Formatting.GOLD));
        }

        String batchPause = BatchDrawing.takePauseWarning(mc.player != null);

        if (batchPause != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.translatable("litematica_turbo.message.batch_paused", batchPause).formatted(Formatting.GOLD));
        }

        String farFailure = FarDetail.takeFailureWarning(mc.player != null);

        if (farFailure != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.translatable("litematica_turbo.message.far_off", farFailure).formatted(Formatting.GOLD));
        }

        String completeFailure = CompleteCopy.takeFailureWarning(mc.player != null);

        if (completeFailure != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.translatable("litematica_turbo.message.complete_off", completeFailure).formatted(Formatting.GOLD));
        }

        String buildDropped = BuildErrors.takeWarning(mc.player != null);

        if (buildDropped != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.translatable("litematica_turbo.message.build_dropped", buildDropped).formatted(Formatting.GOLD));
        }

        TurboSettingsTab.applyPendingChanges();
    }
}
