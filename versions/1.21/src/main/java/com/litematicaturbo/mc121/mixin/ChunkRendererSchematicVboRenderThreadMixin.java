package com.litematicaturbo.mc121.mixin;

import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Calls in Litematica's chunk building that only belong on the render thread. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboRenderThreadMixin
{
    /**
     * The translucency re-sort sets the shader (no effect on the built buffers). Off the render
     * thread that call gets queued and replayed at a random point of a later frame, so only make
     * it on the render thread.
     */
    @Redirect(method = "resortTransparency",
              at = @At(value = "INVOKE", remap = true,
                       target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V"))
    private void turbo$setShaderOnRenderThread(Supplier<ShaderProgram> shader)
    {
        if (RenderSystem.isOnRenderThread())
        {
            RenderSystem.setShader(shader);
        }
    }
}
