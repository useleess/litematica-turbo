package com.litematicaturbo.shared;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Lets the render thread be the first to use the vertex formats of schematic builds. Sodium 0.5 lists a format the first
 * time a buffer builder meets it, and two threads meeting it at once list it twice: ids then repeat, and Sodium's
 * converters (found by id) write block quads in another format's layout, for the rest of the game. Sodium's own chunk
 * building does not use these builders, so without this Turbo's build threads would be the first, all at once.
 */
public final class BuildFormats
{
    private static boolean met;

    private BuildFormats()
    {
    }

    /** Render thread, before build threads start. Each builder is new because a finished one cannot begin without being built. */
    public static void meetBeforeThreads()
    {
        if (met == false)
        {
            met = true;
            new BufferBuilder(256).begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
            new BufferBuilder(256).begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        }
    }
}
