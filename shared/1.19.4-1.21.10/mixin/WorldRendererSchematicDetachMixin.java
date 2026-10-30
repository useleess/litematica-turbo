package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.shared.TurboWorldRenderer;

/** Ghost move: takes the built chunk renderers out of the world renderer. */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicDetachMixin
{
    @Shadow @Final private List<ChunkRendererSchematicVbo> renderInfos;
    @Shadow private Set<ChunkRendererSchematicVbo> chunksToUpdate;
    @Shadow private ChunkRenderDispatcherSchematic chunkRendererDispatcher;
    @Shadow private boolean displayListEntitiesDirty;

    /** TurboWorldRenderer.turbo$detachChunkRenderers (that interface is added by WorldRendererSchematicMixin). */
    public List<ChunkRendererSchematicVbo> turbo$detachChunkRenderers()
    {
        if (this.chunkRendererDispatcher == null)
        {
            return null;
        }

        Long2ObjectOpenHashMap<ChunkRendererSchematicVbo> map = ((ChunkRenderDispatcherSchematicAccessor) this.chunkRendererDispatcher).turbo$getChunkRenderers();
        List<ChunkRendererSchematicVbo> detached = new ArrayList<>(map.values());
        map.clear();
        this.renderInfos.clear();
        this.chunksToUpdate.clear();
        ((TurboWorldRenderer) this).turbo$rebuilds().clear();
        this.displayListEntitiesDirty = true;
        return detached;
    }
}
