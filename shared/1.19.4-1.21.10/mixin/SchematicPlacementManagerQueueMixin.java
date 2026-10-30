package com.litematicaturbo.shared.mixin;

import java.util.Set;
import java.util.function.Supplier;
import com.google.common.collect.ArrayListMultimap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.math.ChunkPos;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.shared.GhostMove;
import com.litematicaturbo.shared.SchematicCopy;
import com.litematicaturbo.shared.GhostRenderers;

/**
 * Placement copying: refreshes instead of re-copies (SchematicCopy), holds while a placement is moved (ghost move)
 * and limits the catch-up afterwards.
 */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class SchematicPlacementManagerQueueMixin
{
    @Shadow @Final protected Set<ChunkPos> chunksToRebuild;
    @Shadow @Final protected Set<ChunkPos> chunkRebuildQueue;
    @Shadow @Final protected ArrayListMultimap<ChunkPos, SchematicPlacement> schematicsTouchingChunk;
    @Shadow @Final protected Supplier<WorldSchematic> worldSupplier;

    @Shadow protected abstract boolean hasTimeToExecuteMoreTasks();

    @Inject(method = "processQueuedChunks", at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/placement/SchematicPlacementManager;hasQueuedChunks()Z"))
    private void turbo$refreshInsteadOfReplacing(CallbackInfo ci)
    {
        SchematicCopy.refreshInsteadOfReplacing(this.chunkRebuildQueue, this.chunksToRebuild, this.schematicsTouchingChunk, this.worldSupplier.get());
    }

    /** While a placement is being moved nothing is copied; the ghosts are shown instead. */
    @Inject(method = "processQueuedChunks", at = @At("HEAD"), cancellable = true)
    private void turbo$holdWhileMoving(CallbackInfo ci)
    {
        if (GhostMove.isHoldingWork())
        {
            ci.cancel();
        }
    }

    /**
     * Stock allows 50 ms of copying per tick, which is a dropped frame every tick. While the ghosts
     * cover the view there is no hurry, so keep each tick's share small.
     */
    @Redirect(method = "processQueuedChunks",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/placement/SchematicPlacementManager;hasTimeToExecuteMoreTasks()Z"))
    private boolean turbo$catchUpBudget(SchematicPlacementManager self)
    {
        if (GhostMove.isActive())
        {
            return GhostRenderers.hasCatchUpTimeLeft();
        }

        return this.hasTimeToExecuteMoreTasks();
    }
}
