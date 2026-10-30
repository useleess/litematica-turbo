package com.litematicaturbo.mc121.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;

import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

/** Litematica 0.19's build worker asks the client for its profiler. */
@Mixin(value = ChunkRenderWorkerLitematica.class, remap = false)
public abstract class ChunkRenderWorkerProfilerMixin
{
    /** The client's profiler belongs to the render thread; build threads get a no-op one. */
    @Redirect(method = "run", at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/client/MinecraftClient;getProfiler()Lnet/minecraft/util/profiler/Profiler;"))
    private Profiler turbo$workerProfiler(MinecraftClient mc)
    {
        return DummyProfiler.INSTANCE;
    }
}
