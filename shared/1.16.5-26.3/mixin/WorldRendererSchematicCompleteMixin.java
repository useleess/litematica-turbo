package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.IChunkRendererFactory;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.shared.CompleteWorldRenderer;
import com.litematicaturbo.shared.TurboRenderer;

@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicCompleteMixin implements CompleteWorldRenderer
{
    @Shadow private WorldSchematic world;
    @Shadow @Final private IChunkRendererFactory renderChunkFactory;
    @Shadow private ChunkRenderDispatcherLitematica renderDispatcher;

    @Override
    public ChunkRendererSchematicVbo turbo$newRenderer(int x, int y, int z)
    {
        ChunkRendererSchematicVbo renderer = this.renderChunkFactory.create(this.world, (WorldRendererSchematic) (Object) this);
        ((TurboRenderer) renderer).turbo$moveTo(x, y, z);
        return renderer;
    }

    @Override
    public ChunkRenderDispatcherLitematica turbo$dispatcher()
    {
        return this.renderDispatcher;
    }
}
