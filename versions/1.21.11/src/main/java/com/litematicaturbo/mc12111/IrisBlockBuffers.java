package com.litematicaturbo.mc12111;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Makes the buffer builders of Litematica's block layers (see {@link IrisBuilders}). */
public final class IrisBlockBuffers
{
    private IrisBlockBuffers()
    {
    }

    public static BufferBuilder create(ByteBufferBuilder allocator, VertexFormat.Mode mode, VertexFormat format)
    {
        Object made = IrisBuilders.create(format, DefaultVertexFormat.BLOCK, f -> new BufferBuilder(allocator, mode, (VertexFormat) f));
        return made != null ? (BufferBuilder) made : new BufferBuilder(allocator, mode, format);
    }

    /** Test hook (render thread, shaders on): see {@link IrisBuilders#selfCheck}. */
    public static String selfCheck(int rounds) throws Exception
    {
        return IrisBuilders.selfCheck(rounds, f -> made(f, true), f -> made(f, false));
    }

    private static Object[] made(Object format, boolean turbo)
    {
        ByteBufferBuilder allocator = new ByteBufferBuilder(1 << 12);
        BufferBuilder builder = turbo ? create(allocator, VertexFormat.Mode.QUADS, (VertexFormat) format) : new BufferBuilder(allocator, VertexFormat.Mode.QUADS, (VertexFormat) format);
        return new Object[] {builder, allocator};
    }
}
