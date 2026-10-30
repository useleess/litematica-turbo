package com.litematicaturbo.mc262.bench;

import java.io.File;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;

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
    public void onRenderWorldLast(RenderTarget target, Matrix4fc posMatrix, CameraRenderState camera, Frustum frustum,
                                  RenderBuffers buffers, GpuBufferSlice fog, Vector4f fogColor, ProfilerFiller profiler)
    {
        this.run.frame();
    }

    @Override
    public void screenshot(String fileName)
    {
        Screenshot.grab(mc().gameDirectory, fileName, mc().gameRenderer.mainRenderTarget(), 1, msg -> {});
    }
}
