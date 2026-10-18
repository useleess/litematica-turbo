package com.litematicaturbo.mc12110.mixin;

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
}
