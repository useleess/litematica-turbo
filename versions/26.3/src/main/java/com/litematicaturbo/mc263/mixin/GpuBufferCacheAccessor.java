package com.litematicaturbo.mc263.mixin;

import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuBuffers;
import fi.dy.masa.litematica.render.schematic.GpuBufferCache;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;

@Mixin(value = GpuBufferCache.class, remap = false)
public interface GpuBufferCacheAccessor
{
    @Accessor("blockBuffers")
    ConcurrentHashMap<ChunkSectionLayer, ChunkRenderGpuBuffers> turbo$blockBuffers();

    @Accessor("overlayBuffers")
    ConcurrentHashMap<OverlayRenderType, ChunkRenderGpuBuffers> turbo$overlayBuffers();
}
