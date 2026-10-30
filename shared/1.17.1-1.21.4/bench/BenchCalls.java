package com.litematicaturbo.shared.bench;

import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.world.ClientWorld;

import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/** The benchmark calls that Minecraft renamed between the Yarn-named versions (here: 1.17 - 1.21.4). */
final class BenchCalls
{
    private BenchCalls()
    {
    }

    static LitematicaSchematic load(File file)
    {
        return SchematicHolder.getInstance().getOrLoad(file);
    }

    static void setFlying(ClientPlayerEntity player)
    {
        player.getAbilities().flying = true;
    }

    static int bottomY(ClientWorld world)
    {
        return world.getBottomY();
    }

    static void screenshot(MinecraftClient mc, String fileName)
    {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, fileName, mc.getFramebuffer(), msg -> {});
    }
}
