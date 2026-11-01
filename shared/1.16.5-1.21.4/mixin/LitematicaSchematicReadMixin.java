package com.litematicaturbo.shared.mixin;

import java.io.File;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;

import com.litematicaturbo.shared.CompactBlocks;

/** A schematic file that cannot fit in the game's memory is not read: the player gets a red message instead of an out-of-memory crash. */
@Mixin(value = LitematicaSchematic.class, remap = false)
public abstract class LitematicaSchematicReadMixin
{
    @Shadow
    @Final
    private File schematicFile;

    @Inject(method = "readFromFile(Lfi/dy/masa/litematica/util/FileType;)Z", at = @At("HEAD"), cancellable = true)
    private void turbo$checkMemory(FileType type, CallbackInfoReturnable<Boolean> cir)
    {
        if (CompactBlocks.refuseRead(this.schematicFile.toPath()))
        {
            cir.setReturnValue(false);
        }
    }
}
