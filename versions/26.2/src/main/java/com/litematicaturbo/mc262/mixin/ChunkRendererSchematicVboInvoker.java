package com.litematicaturbo.mc262.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

/** Protected methods of Litematica's chunk renderer. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public interface ChunkRendererSchematicVboInvoker
{
    @Invoker("rebuildChunk")
    void turbo$rebuildChunk(ChunkRenderTaskSchematic task, ChunkRenderDispatcherBuffers pack);

    @Invoker("resortTransparency")
    void turbo$resortTransparency(ChunkRenderTaskSchematic task, ChunkRenderDispatcherBuffers pack);

    @Invoker("getChunkRenderData")
    ChunkRenderDataSchematic turbo$getChunkRenderData();

    @Invoker("getChunkPos")
    ChunkPos turbo$getChunkPos();
}
