package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder;

import com.litematicaturbo.core.TurboLog;

/** Uploads of schematic chunks that Litematica 0.14 - 0.17 would get wrong or crash on. */
@Mixin(value = ChunkRenderDispatcherLitematica.class, remap = false)
public abstract class ChunkRenderDispatcherUploadMixin
{
    /**
     * An upload of a buffer builder that this task did not fill (a translucency re-sort without
     * overlay data) has nothing new to upload; stock would upload a stale buffer or fail. Keep the
     * chunk's previous data instead.
     */
    @Inject(method = "uploadVertexBuffer", at = @At("HEAD"), cancellable = true)
    private void turbo$skipEmptyUpload(BufferBuilder buffer, VertexBuffer vertexBuffer, CallbackInfo ci)
    {
        if (buffer instanceof OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder &&
            ((OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder) buffer).lastRenderBuildBuffer == null &&
            buffer.isBuilding() == false)
        {
            ci.cancel();
        }
    }

    @Unique
    private static boolean turbo$uploadErrorLogged;

    /**
     * Last line of defence: a vertex buffer whose builder was disturbed while its upload was
     * pending fails here (IllegalArgumentException from the buffer slice). Stock would crash the
     * game; the chunk keeps its previous geometry instead and is rebuilt the next time it changes.
     */
    @Redirect(method = "uploadVertexBuffer",
              at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/client/gl/VertexBuffer;upload(Lnet/minecraft/client/render/BufferBuilder$BuiltBuffer;)V"))
    private void turbo$safeUpload(VertexBuffer vertexBuffer, BufferBuilder.BuiltBuffer builtBuffer)
    {
        try
        {
            vertexBuffer.upload(builtBuffer);
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            if (turbo$uploadErrorLogged == false)
            {
                turbo$uploadErrorLogged = true;
                TurboLog.warn("Skipped a broken schematic chunk upload (kept the previous one): " + e);
            }
        }
    }
}
