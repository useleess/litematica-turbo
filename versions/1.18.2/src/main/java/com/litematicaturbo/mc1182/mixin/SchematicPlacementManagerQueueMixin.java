package com.litematicaturbo.mc1182.mixin;

import java.util.Set;
import com.google.common.collect.ArrayListMultimap;
import org.objectweb.asm.Opcodes;
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
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;

import com.litematicaturbo.mc1182.GhostMove;
import com.litematicaturbo.mc1182.SchematicCopy;
import com.litematicaturbo.mc1182.GhostRenderers;
import com.litematicaturbo.mc1182.TurboPlacementManager;

/**
 * Placement copying: refreshes instead of re-copies (SchematicCopy), holds while a placement is moved (ghost move)
 * and limits the catch-up afterwards.
 */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class SchematicPlacementManagerQueueMixin implements TurboPlacementManager
{
    @Shadow @Final private Set<ChunkPos> chunksToRebuild;
    @Shadow @Final private Set<ChunkPos> chunkRebuildQueue;
    @Shadow @Final private ArrayListMultimap<ChunkPos, SchematicPlacement> schematicsTouchingChunk;

    @Override
    public boolean turbo$hasQueuedChunks()
    {
        return this.chunkRebuildQueue.isEmpty() == false;
    }

    /** Runs after the queued unloads (first read of chunkRebuildQueue), like stock's own queue loop. */
    @Inject(method = "processQueuedChunks",
            at = @At(value = "FIELD", target = "Lfi/dy/masa/litematica/schematic/placement/SchematicPlacementManager;chunkRebuildQueue:Ljava/util/Set;", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void turbo$refreshInsteadOfReplacing(CallbackInfo ci)
    {
        SchematicCopy.refreshInsteadOfReplacing(this.chunkRebuildQueue, this.chunksToRebuild, this.schematicsTouchingChunk, SchematicWorldHandler.getSchematicWorld());
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
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/data/DataManager;getClientTickStartTime()J"))
    private long turbo$catchUpBudget()
    {
        // Stock stops copying once 50 ms have passed since this tick started
        if (GhostMove.isActive())
        {
            return GhostRenderers.hasCatchUpTimeLeft() ? System.nanoTime() : System.nanoTime() - 1_000_000_000L;
        }

        return DataManager.getClientTickStartTime();
    }
}
