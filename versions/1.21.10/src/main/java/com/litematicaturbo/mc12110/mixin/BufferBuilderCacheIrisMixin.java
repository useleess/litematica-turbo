package com.litematicaturbo.mc12110.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.BufferAllocator;

import fi.dy.masa.litematica.render.schematic.BufferBuilderCache;

import com.litematicaturbo.mc12110.IrisBlockBuffers;

/**
 * Litematica makes the buffer builder of a block layer on whichever thread builds the chunk. With Iris shaders on, the
 * builder's vertex format and whether it fills Iris's extra elements are decided at two moments from a flag the render
 * thread changes (crash "Missing elements in vertex: mc_midTexCoord, at_tangent"); see {@code IrisBuilders}.
 */
@Mixin(value = BufferBuilderCache.class, remap = false)
public abstract class BufferBuilderCacheIrisMixin
{
    @Redirect(method = "lambda$getBufferByBlockLayer$0", at = @At(value = "NEW", target = "net/minecraft/client/render/BufferBuilder"))
    private static BufferBuilder turbo$irisConsistentBuilder(BufferAllocator allocator, VertexFormat.DrawMode mode, VertexFormat format)
    {
        return IrisBlockBuffers.create(allocator, mode, format);
    }
}
