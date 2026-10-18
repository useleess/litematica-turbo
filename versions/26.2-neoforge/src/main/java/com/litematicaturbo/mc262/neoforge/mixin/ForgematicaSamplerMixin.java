package com.litematicaturbo.mc262.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.textures.GpuSampler;

import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

/**
 * Forgematica 0.5.0 (26.2) keeps the sampler vanilla passes to drawBlockLayerGroup and closes it when its
 * renderer unloads (world change, or Turbo's live thread-count change), which breaks vanilla's terrain
 * rendering ("Sampler ... has been closed!"). Litematica on Fabric uses a sampler of its own; so does this.
 */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class ForgematicaSamplerMixin
{
    @Redirect(method = "drawBlockLayerGroup",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/WorldRendererSchematic;setGpuSampler(Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
    private void turbo$keepOwnSampler(WorldRendererSchematic renderer, GpuSampler vanillaSampler)
    {
        // getGpuSampler() creates Litematica's own sampler when none is set
    }
}
