package com.litematicaturbo.shared.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 1.16.5 - 1.18.2 only (litematica_turbo.forge.mixins.json). Forge asks for model data whenever a block entity
 * of a client-side world is removed, and its ModelDataManager throws for every world but the client's own one. Litematica's
 * schematic world is such a world, so replacing a chest there crashed the game (plain Forgematica too). Those requests
 * are skipped; Forge would only have thrown. Forge 1.19+ and NeoForge keep model data per world and never throw here.
 */
@Mixin(targets = "net.minecraftforge.client.model.ModelDataManager", remap = false)
public abstract class ModelDataManagerMixin
{
    @Inject(method = "requestModelDataRefresh", at = @At("HEAD"), cancellable = true)
    private static void turbo$onlyClientWorld(BlockEntity blockEntity, CallbackInfo ci)
    {
        if (blockEntity.getWorld() != MinecraftClient.getInstance().world)
        {
            ci.cancel();
        }
    }
}
