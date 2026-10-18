package com.litematicaturbo.mc12110.mixin;

import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.RenderLayer;

import fi.dy.masa.litematica.render.schematic.ChunkRenderObjectBuffers;
import fi.dy.masa.litematica.render.schematic.GpuBufferCache;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;

@Mixin(value = GpuBufferCache.class, remap = false)
public interface GpuBufferCacheAccessor
{
    @Invoker("clearAll")
    void turbo$clearAll();

    @Accessor("blockBuffers")
    ConcurrentHashMap<BlockRenderLayer, ChunkRenderObjectBuffers> turbo$blockBuffers();

    @Accessor("layerBuffers")
    ConcurrentHashMap<RenderLayer, ChunkRenderObjectBuffers> turbo$layerBuffers();

    @Accessor("overlayBuffers")
    ConcurrentHashMap<OverlayRenderType, ChunkRenderObjectBuffers> turbo$overlayBuffers();
}
