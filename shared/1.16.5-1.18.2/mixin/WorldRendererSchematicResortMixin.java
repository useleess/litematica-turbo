package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.TurboConfig;

@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicResortMixin
{
    /**
     * Before 1.19 a translucency re-sort reuses whatever vertex data is left in the buffer cache it
     * gets (BufferBuilder.restoreState keeps no vertices). With one cache that only draws wrong
     * faces; with a cache per build thread it can overflow the buffer and crash. Translucent
     * blocks stay sorted as they were built instead.
     */
    @Redirect(method = "renderBlockLayer*",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateTransparencyLater(Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;)Z"))
    private boolean turbo$noResortWithThreads(ChunkRenderDispatcherLitematica dispatcher, ChunkRendererSchematicVbo renderer)
    {
        return TurboConfig.get().threadingEnabled() || dispatcher.updateTransparencyLater(renderer);
    }
}
