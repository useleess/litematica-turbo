package com.litematicaturbo.mc1182.mixin;

import java.util.Iterator;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.math.ChunkPos;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.mc1182.GhostMove;
import com.litematicaturbo.mc1182.TurboPlacementManager;

/**
 * Every chunk packet the client receives queues that chunk, and the queue wipes the schematic chunk
 * and copies all placements into it again - even when it is already loaded and nothing about the
 * placements changed (flying around, chunk resends). Only the render needs refreshing then, because
 * the real-world blocks it is compared against changed. Explicit rebuild requests (placement moved,
 * schematic edited, ...) are in chunksToRebuild and still take the full path.
 */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class SchematicPlacementManagerMixin implements TurboPlacementManager
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
        if (TurboConfig.get().enabled == false || this.chunkRebuildQueue.isEmpty())
        {
            return;
        }

        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();

        if (world == null)
        {
            return;
        }

        Iterator<ChunkPos> it = this.chunkRebuildQueue.iterator();

        while (it.hasNext())
        {
            ChunkPos pos = it.next();

            if (this.chunksToRebuild.contains(pos) == false &&
                this.schematicsTouchingChunk.containsKey(pos) &&
                world.getChunkProvider().getChunkIfExists(pos.x, pos.z) != null)
            {
                world.scheduleChunkRenders(pos.x, pos.z);
                BuildStats.placeCallsSkipped.incrementAndGet();
                it.remove();
            }
        }
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
            return GhostMove.hasCatchUpTimeLeft() ? System.nanoTime() : System.nanoTime() - 1_000_000_000L;
        }

        return DataManager.getClientTickStartTime();
    }

    /** Any change other than moving (rotate, mirror, sub-regions, ...) goes the normal way. */
    @Inject(method = "onPrePlacementChange", at = @At("HEAD"))
    private void turbo$otherChange(CallbackInfo ci)
    {
        if (GhostMove.isActive() && GhostMove.isInSetOrigin() == false)
        {
            GhostMove.end();
        }
    }

    @Inject(method = "removeSchematicPlacement(Lfi/dy/masa/litematica/schematic/placement/SchematicPlacement;Z)Z", at = @At("HEAD"))
    private void turbo$placementRemoved(CallbackInfoReturnable<Boolean> cir)
    {
        GhostMove.end();
    }

    @Inject(method = "addSchematicPlacement", at = @At("HEAD"))
    private void turbo$placementAdded(CallbackInfo ci)
    {
        GhostMove.end();
    }
}
