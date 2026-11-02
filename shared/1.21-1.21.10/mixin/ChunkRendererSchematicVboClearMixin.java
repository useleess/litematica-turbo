package com.litematicaturbo.shared.mixin;

import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.shared.TurboRenderer;

/**
 * clear() ends the chunk's buffer builders, which a build thread may still be filling (stock builds on the render
 * thread only). Reloading the renderers (a shader pack switched on or off, F3+A) or moving the view then wrote into
 * one buffer from two threads: broken vertices, Iris's "Missing elements in vertex" / index errors. Clear only once
 * the chunk's own build is done.
 */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboClearMixin
{
    @Shadow protected abstract void clear();

    @Redirect(method = { "setPosition", "deleteGlResources" }, expect = 2,
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;clear()V"))
    private void turbo$clearAfterBuild(ChunkRendererSchematicVbo self)
    {
        ReentrantLock lock = ((TurboRenderer) self).turbo$buildLock();
        lock.lock();

        try
        {
            this.clear();
        }
        finally
        {
            lock.unlock();
        }
    }
}
