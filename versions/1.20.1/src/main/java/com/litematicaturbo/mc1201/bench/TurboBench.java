package com.litematicaturbo.mc1201.bench;

import java.io.File;
import org.joml.Matrix4f;
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
}
