package com.litematicaturbo.shared;

import java.util.function.Function;
import net.minecraft.client.gl.GlUniform;

/** Minecraft 1.17.1 - 1.21: the block shaders take the place of a region from their chunk offset uniform. */
public final class ChunkOffsetPlacement
{
    private ChunkOffsetPlacement()
    {
    }

    /** The shader type changes between versions (Shader, ShaderProgram), so the caller says where its chunk offset is. */
    public static <S> BatchPlacement of(S shader, Function<S, GlUniform> chunkOffset)
    {
        GlUniform offset = chunkOffset.apply(shader);

        if (offset == null)
        {
            throw new IllegalStateException("the block shader has no chunk offset");
        }

        return (x, y, z, draw) -> {
            offset.set(x, y, z);
            offset.upload();
            draw.run();
            offset.set(0.0F, 0.0F, 0.0F);
        };
    }
}
