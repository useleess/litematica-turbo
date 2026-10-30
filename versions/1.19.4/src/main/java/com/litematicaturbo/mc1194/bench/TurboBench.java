package com.litematicaturbo.mc1194.bench;

import java.io.File;
import org.joml.Matrix4f;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;

/** The dev-only benchmark on this version (see BenchBase): its frame hook and what changed on it. */
public final class TurboBench extends BenchHost
{
    private TurboBench(File schematic)
    {
        super(schematic);
    }

    public static void startIfRequested()
    {
        start(TurboBench::new);
    }

    @Override
    public void onRenderWorldLast(MatrixStack matrixStack, Matrix4f projMatrix)
    {
        this.run.frame();
    }

    /** No quick play before 1.20: open the bench world from the title screen. */
    @Override
    public boolean openBenchWorld()
    {
        MinecraftClient mc = mc();

        if (mc.world == null && mc.currentScreen instanceof TitleScreen)
        {
            mc.createIntegratedServerLoader().start(mc.currentScreen, "bench");
            return true;
        }

        return false;
    }
}
