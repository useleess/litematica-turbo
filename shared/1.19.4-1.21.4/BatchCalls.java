package com.litematicaturbo.shared;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL20;

/** The Mojang calls batch drawing needs, as they are in Minecraft 1.19.4 - 1.21.4 (the names and the vertex buffer's binding changed in 1.19). */
final class BatchCalls
{
    private BatchCalls()
    {
    }

    static int stride(VertexFormat format)
    {
        return format.getVertexSizeByte();
    }

    /** Points the vertex attributes at the bound vertex array and buffer. */
    static void setup(VertexFormat format)
    {
        format.setupState();
    }

    static boolean open(VertexBuffer buffer)
    {
        return buffer.isClosed() == false;
    }

    /** The GL name of the buffer's vertex data: the one its vertex array points the first attribute at (binds the vertex array). */
    static int id(VertexBuffer buffer)
    {
        buffer.bind();
        return GL20.glGetVertexAttribi(0, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
    }
}
