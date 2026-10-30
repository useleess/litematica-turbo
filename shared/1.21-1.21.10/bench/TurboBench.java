package com.litematicaturbo.shared.bench;

import java.io.File;
import org.joml.Matrix4f;

import com.litematicaturbo.core.TurboLog;

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
    public void startProfiler()
    {
        mc().toggleDebugProfiler(msg -> TurboLog.info("BENCH profiler: " + msg.getString()));
    }
}
