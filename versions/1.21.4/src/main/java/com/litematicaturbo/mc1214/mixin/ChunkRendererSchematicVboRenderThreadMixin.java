package com.litematicaturbo.mc1214.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Calls in Litematica's chunk building that only belong on the render thread. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboRenderThreadMixin
{
    /**
     * Build code sets the shader (no effect on the built buffers); that is render-thread only
     * state, so only make the call on the render thread. (renderOverlay: Litematica before 0.21.7)
     */
    @Redirect(method = { "resortTransparency", "preRenderOverlay", "renderOverlay" }, expect = 2,
              at = @At(value = "INVOKE", remap = true,
                       target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Lnet/minecraft/client/gl/ShaderProgramKey;)Lnet/minecraft/client/gl/ShaderProgram;"))
    private ShaderProgram turbo$setShaderOnRenderThread(ShaderProgramKey shader)
    {
        return RenderSystem.isOnRenderThread() ? RenderSystem.setShader(shader) : null;
    }
}
