package com.litematicaturbo.shared.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.ChunkPos;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.core.ChangeWatch;

/**
 * Litematica 0.25+ lists the schematic chunks to draw again only while its "visible chunks changed" flag is set, and
 * clears that flag after reading the loaded chunks. A chunk its background threads finish in between sets the flag
 * before that clear and is lost: it never gets a renderer until something else changes. A change seen during the
 * listing sets the flag again, so the next frame lists that chunk too.
 */
@Mixin(value = SchematicPlacementManager.class, remap = false)
public abstract class SchematicPlacementManagerVisibleMixin
{
    @Shadow public abstract void setVisibleSubChunksNeedsUpdate();

    @Unique
    private final ChangeWatch turbo$visibleChanges = new ChangeWatch();

    @Inject(method = "setVisibleSubChunksNeedsUpdate", at = @At("HEAD"))
    private void turbo$countVisibleChange(CallbackInfo ci)
    {
        this.turbo$visibleChanges.changed();
    }

    @Inject(method = "getAndUpdateVisibleChunks", at = @At("HEAD"))
    private void turbo$startListing(ChunkPos center, CallbackInfoReturnable<List<ChunkPos>> cir)
    {
        this.turbo$visibleChanges.start();
    }

    @Inject(method = "getAndUpdateVisibleChunks", at = @At("RETURN"))
    private void turbo$keepLateVisibleChange(ChunkPos center, CallbackInfoReturnable<List<ChunkPos>> cir)
    {
        if (this.turbo$visibleChanges.changedSinceStart())
        {
            this.setVisibleSubChunksNeedsUpdate();
        }
    }
}
