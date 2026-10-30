package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.util.math.BlockPos;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.IChunkRendererFactory;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.shared.TurboWorldRenderer;

/** Ghost move: takes the built chunk renderers out of the world renderer. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicDetachMixin
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;
    @Shadow private Set<ChunkRendererSchematicVbo> chunksToUpdate;
    @Shadow private ChunkRenderDispatcherSchematic chunkRendererDispatcher;
    @Shadow private boolean displayListEntitiesDirty;
    @Shadow @Final private IChunkRendererFactory renderChunkFactory;
    @Shadow private WorldSchematic world;

    /** TurboWorldRenderer.turbo$detachChunkRenderers (that interface is added by WorldRendererSchematicMixin). */
    /**
     * Litematica 0.11 keeps its chunk renderers in a fixed array (one 16-high slice each). Every
     * slice that has built buffers is taken out and replaced by a fresh renderer at the same spot.
     */
    public List<ChunkRendererSchematicVbo> turbo$detachChunkRenderers()
    {
        if (this.chunkRendererDispatcher == null || this.world == null)
        {
            return null;
        }

        ChunkRendererSchematicVbo[] renderers = this.chunkRendererDispatcher.renderers;
        List<ChunkRendererSchematicVbo> detached = new ArrayList<>();

        for (int i = 0; i < renderers.length; ++i)
        {
            ChunkRendererSchematicVbo old = renderers[i];

            if (old.getChunkRenderData() != ChunkRenderDataSchematic.EMPTY)
            {
                BlockPos origin = old.getOrigin();
                ChunkRendererSchematicVbo fresh = this.renderChunkFactory.create(this.world, (WorldRendererSchematic) (Object) this);
                fresh.setPosition(origin.getX(), origin.getY(), origin.getZ());
                renderers[i] = fresh;
                detached.add(old);
            }
        }

        this.renderInfos.clear();
        this.chunksToUpdate.clear();
        ((TurboWorldRenderer) this).turbo$rebuilds().clear();
        this.displayListEntitiesDirty = true;
        return detached;
    }
}
