package com.litematicaturbo.shared.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import fi.dy.masa.litematica.schematic.container.LitematicaBitArray;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;

import com.litematicaturbo.shared.CompactBlocks;

/** The block container of a schematic region gets sparse storage when its packed array is huge and mostly empty. */
@Mixin(value = LitematicaBlockStateContainer.class, remap = false)
public abstract class LitematicaBlockStateContainerMixin
{
    @Shadow
    protected LitematicaBitArray storage;
    @Shadow
    protected int sizeX;
    @Shadow
    protected int sizeY;
    @Shadow
    protected int sizeZ;

    @Redirect(method = "setBits", at = @At(value = "NEW", target = "(IJ[J)Lfi/dy/masa/litematica/schematic/container/LitematicaBitArray;"))
    private LitematicaBitArray turbo$packedStorage(int bits, long size, long[] words)
    {
        return CompactBlocks.fromPacked(bits, size, words, this.sizeX, this.sizeY, this.sizeZ);
    }

    @Redirect(method = "setBits", at = @At(value = "NEW", target = "(IJ)Lfi/dy/masa/litematica/schematic/container/LitematicaBitArray;"))
    private LitematicaBitArray turbo$emptyStorage(int bits, long size)
    {
        return CompactBlocks.resized(this.storage, bits, size, this.sizeX, this.sizeY, this.sizeZ);
    }
}
