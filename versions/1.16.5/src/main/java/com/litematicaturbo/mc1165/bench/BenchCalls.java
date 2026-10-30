package com.litematicaturbo.mc1165.bench;

import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.client.world.ClientWorld;

import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/** The benchmark calls that Minecraft renamed after 1.16. */
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
        player.abilities.flying = true;
    }

    static int bottomY(ClientWorld world)
    {
        return 0; // the world starts at 0 before 1.17
    }

    static void screenshot(MinecraftClient mc, String fileName)
    {
        ScreenshotUtils.saveScreenshot(mc.runDirectory, fileName, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), mc.getFramebuffer(), msg -> {});
    }
}
