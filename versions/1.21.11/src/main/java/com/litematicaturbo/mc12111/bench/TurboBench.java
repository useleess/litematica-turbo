package com.litematicaturbo.mc12111.bench;

import java.io.File;
import org.joml.Matrix4f;
import net.minecraft.client.Screenshot;

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
    public void onRenderWorldLast(Matrix4f posMatrix, Matrix4f projMatrix)
    {
        this.run.frame();
    }

    @Override
    public void screenshot(String fileName)
    {
        Screenshot.grab(mc().gameDirectory, fileName, mc().getMainRenderTarget(), 1, msg -> {});
    }
}
