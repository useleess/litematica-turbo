package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.util.profiler.Profiler;

import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Protected build methods of Litematica's chunk renderer. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public interface ChunkRendererSchematicVboInvoker
{
    @Invoker("rebuildChunk")
    void turbo$rebuildChunk(ChunkRenderTaskSchematic task, Profiler profiler);

    @Invoker("resortTransparency")
    void turbo$resortTransparency(ChunkRenderTaskSchematic task, Profiler profiler);

    @Invoker("needsUpdate")
    boolean turbo$callNeedsUpdate();

    @Invoker("setNeedsUpdate")
    void turbo$callSetNeedsUpdate(boolean immediate);

    @Invoker("clearNeedsUpdate")
    void turbo$callClearNeedsUpdate();

    @Invoker("deleteGlResources")
    void turbo$callDeleteGlResources();
}
