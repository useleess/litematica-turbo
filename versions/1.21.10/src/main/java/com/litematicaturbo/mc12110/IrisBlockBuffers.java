package com.litematicaturbo.mc12110;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;

/** Makes the buffer builders of Litematica's block layers (see {@link IrisBuilders}). */
public final class IrisBlockBuffers
{
    private IrisBlockBuffers()
    {
    }

    public static BufferBuilder create(BufferAllocator allocator, VertexFormat.DrawMode mode, VertexFormat format)
    {
        Object made = IrisBuilders.create(format, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, f -> new BufferBuilder(allocator, mode, (VertexFormat) f));
        return made != null ? (BufferBuilder) made : new BufferBuilder(allocator, mode, format);
    }

    /** Test hook (render thread, shaders on): see {@link IrisBuilders#selfCheck}. */
    public static String selfCheck(int rounds) throws Exception
    {
        return IrisBuilders.selfCheck(rounds, f -> made(f, true), f -> made(f, false));
    }

    private static Object[] made(Object format, boolean turbo)
    {
        BufferAllocator allocator = new BufferAllocator(1 << 12);
        BufferBuilder builder = turbo ? create(allocator, VertexFormat.DrawMode.QUADS, (VertexFormat) format) : new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, (VertexFormat) format);
        return new Object[] {builder, allocator};
    }
}
