package com.litematicaturbo.mc1204.bench;

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

    /** -Dlitematicaturbo.shots=true: the gallery screenshots (F3 stats while building, settings tab). */
    @Override
    public boolean takesGalleryShots()
    {
        return true;
    }

    @Override
    public boolean debugHudShown()
    {
        return mc().getDebugHud().shouldShowDebugHud();
    }

    @Override
    public void toggleDebugHud()
    {
        mc().getDebugHud().toggleDebugHud();
    }
}
