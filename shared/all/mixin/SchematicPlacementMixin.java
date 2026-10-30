package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.litematicaturbo.shared.GhostMove;

@Mixin(value = SchematicPlacement.class, remap = false)
public abstract class SchematicPlacementMixin
{
    @Inject(method = "setOrigin", at = @At("HEAD"))
    private void turbo$beforeMove(CallbackInfoReturnable<SchematicPlacement> cir)
    {
        GhostMove.beforeSetOrigin((SchematicPlacement) (Object) this);
    }

    @Inject(method = "setOrigin", at = @At("RETURN"))
    private void turbo$afterMove(CallbackInfoReturnable<SchematicPlacement> cir)
    {
        GhostMove.afterSetOrigin((SchematicPlacement) (Object) this);
    }
}
