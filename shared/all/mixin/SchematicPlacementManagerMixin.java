package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.shared.GhostMove;

/** Litematica 0.26 copies placements on its own background threads; Turbo only follows the changes. */
/** A placement change other than a move ends the ghost move (GhostMove). */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class SchematicPlacementManagerMixin
{
    /** Any change other than moving (rotate, mirror, sub-regions, ...) goes the normal way. */
    @Inject(method = "onPrePlacementChange", at = @At("HEAD"))
    private void turbo$otherChange(CallbackInfo ci)
    {
        if (GhostMove.isInSetOrigin() == false)
        {
            GhostMove.endAndDropCopy();
        }
    }

    @Inject(method = "removeSchematicPlacement(Lfi/dy/masa/litematica/schematic/placement/SchematicPlacement;Z)Z", at = @At("HEAD"))
    private void turbo$placementRemoved(CallbackInfoReturnable<Boolean> cir)
    {
        GhostMove.endAndDropCopy();
    }

    @Inject(method = "addSchematicPlacement", at = @At("HEAD"))
    private void turbo$placementAdded(CallbackInfo ci)
    {
        GhostMove.endAndDropCopy();
    }
}
