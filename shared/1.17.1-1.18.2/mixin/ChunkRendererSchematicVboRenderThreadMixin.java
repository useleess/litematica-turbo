package com.litematicaturbo.shared.mixin;

import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.Shader;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Calls in Litematica's chunk building that only belong on the render thread. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboRenderThreadMixin
{
    /**
     * Build code sets the shader / model-view matrix (no effect on the built buffers). Off the
     * render thread those calls get queued and replayed at a random point of a later frame, so
     * only make them on the render thread.
     */
    @Redirect(method = { "preRenderOverlay*", "renderOverlay", "resortTransparency" }, expect = 4,
              at = @At(value = "INVOKE", remap = true,
                       target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V"))
    private void turbo$setShaderOnRenderThread(Supplier<Shader> shader)
    {
        if (RenderSystem.isOnRenderThread())
        {
            RenderSystem.setShader(shader);
        }
    }

    @Redirect(method = "postRenderOverlay",
              at = @At(value = "INVOKE", remap = true, target = "Lcom/mojang/blaze3d/systems/RenderSystem;applyModelViewMatrix()V"))
    private void turbo$applyModelViewOnRenderThread()
    {
        if (RenderSystem.isOnRenderThread())
        {
            RenderSystem.applyModelViewMatrix();
        }
    }
}
