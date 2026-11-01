package com.litematicaturbo.shared;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Minecraft 1.21.4 has no chunk offset in its block shaders: a region is placed by moving the model-view matrix. */
public final class ModelViewPlacement
{
    private ModelViewPlacement()
    {
    }

    public static BatchPlacement of(RenderLayer layer, Matrix4f projection, ShaderProgram shader)
    {
        return (x, y, z, draw) -> {
            Matrix4fStack stack = RenderSystem.getModelViewStack();
            stack.pushMatrix();
            stack.translate(x, y, z);
            shader.initializeUniforms(layer.getDrawMode(), stack, projection, MinecraftClient.getInstance().getWindow());
            shader.bind();
            draw.run();
            shader.unbind();
            stack.popMatrix();
        };
    }
}
