package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.shared.GhostRenderers;

/** The chunks a moved placement left stay loaded while the complete copy reads the old place (GhostRenderers.holdForCopy). */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class SchematicPlacementManagerHoldMixin
{
    @Shadow @Final protected ConcurrentHashMap<ChunkPos, CopyOnWriteArrayList<SchematicPlacement>> schematicsTouchingChunk;

    @Shadow private void markChunksForUnload(Collection<ChunkPos> chunks) {}
    @Shadow public abstract void markChunkForUnload(ChunkPos pos);

    @Unique
    private final List<ChunkPos> turbo$keptForCopy = new ArrayList<>();

    @Redirect(method = "onPostPlacementChange",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/placement/SchematicPlacementManager;markChunksForUnload(Ljava/util/Collection;)V"))
    private void turbo$keepForCopy(SchematicPlacementManager manager, Collection<ChunkPos> chunks)
    {
        if (GhostRenderers.holdForCopy())
        {
            this.turbo$keptForCopy.addAll(chunks);
        }
        else
        {
            this.markChunksForUnload(chunks);
        }
    }

    /** Once the copy is built: the kept chunks that no placement touches by now are unloaded, as Litematica would have. */
    @Inject(method = "onClientTick", at = @At("HEAD"))
    private void turbo$unloadKept(CallbackInfo ci)
    {
        if (this.turbo$keptForCopy.isEmpty() || GhostRenderers.isCopyHeld())
        {
            return;
        }

        for (ChunkPos pos : this.turbo$keptForCopy)
        {
            if (this.schematicsTouchingChunk.containsKey(pos) == false)
            {
                this.markChunkForUnload(pos);
            }
        }

        this.turbo$keptForCopy.clear();
    }
}
