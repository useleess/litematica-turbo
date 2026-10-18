package com.litematicaturbo.mc1165.mixin;

import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.util.WorldUtils;

import com.litematicaturbo.mc1165.GhostMove;
import com.litematicaturbo.mc1165.TurboPlacementManager;

/** Holds copying while a placement is moved (ghost move) and limits the catch-up afterwards. */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class SchematicPlacementManagerMixin implements TurboPlacementManager
{
    @Shadow @Final private Set<ChunkPos> chunksToRebuild;

    /**
     * Litematica 0.9 keeps chunks whose client chunk is not loaded in the queue until it is, so
     * only count the ones that can be copied now.
     */
    @Override
    public boolean turbo$hasQueuedChunks()
    {
        if (this.chunksToRebuild.isEmpty())
        {
            return false;
        }

        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null || Configs.Generic.LOAD_ENTIRE_SCHEMATICS.getBooleanValue())
        {
            return true;
        }

        for (ChunkPos pos : this.chunksToRebuild)
        {
            if (WorldUtils.isClientChunkLoaded(world, pos.x, pos.z))
            {
                return true;
            }
        }

        return false;
    }

    // Litematica 0.9 never re-copies a chunk just because the client received it again, so
    // there is no refresh shortcut here (newer versions need one).

    /** While a placement is being moved nothing is copied; the ghosts are shown instead. */
    @Inject(method = "processQueuedChunks", at = @At("HEAD"), cancellable = true)
    private void turbo$holdWhileMoving(CallbackInfoReturnable<Boolean> cir)
    {
        if (GhostMove.isHoldingWork())
        {
            cir.setReturnValue(false);
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
