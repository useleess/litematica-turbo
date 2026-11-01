package com.litematicaturbo.shared;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/** The Mojang calls batch drawing needs, as they are in Minecraft 1.17.1 - 1.18.2 (the names and the vertex buffer's binding changed in 1.19). */
final class BatchCalls
{
    private BatchCalls()
    {
    }

    static int stride(VertexFormat format)
    {
        return format.getVertexSize();
    }

    /** Points the vertex attributes at the bound vertex array and buffer. */
    static void setup(VertexFormat format)
    {
        format.startDrawing();
    }

    /** These versions cannot tell a closed vertex buffer; a closed one has the name 0 and copies as nothing. */
    static boolean open(VertexBuffer buffer)
    {
        return true;
    }

    /** The GL name of the buffer's vertex data (binds the buffer: a vertex array has to be bound). */
    static int id(VertexBuffer buffer)
    {
        buffer.bind();
        return GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    }
}
