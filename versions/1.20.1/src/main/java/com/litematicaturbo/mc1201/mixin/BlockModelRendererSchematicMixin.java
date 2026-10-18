package com.litematicaturbo.mc1201.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.util.math.random.LocalRandom;

import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;

/**
 * The model renderer is shared by every build thread, but its random generator (used to pick
 * block model variants) is not thread-safe. Give each thread its own.
 */
@Mixin(value = BlockModelRendererSchematic.class, remap = false)
public abstract class BlockModelRendererSchematicMixin
{
    @Unique
    private static final ThreadLocal<LocalRandom> TURBO_RANDOM = ThreadLocal.withInitial(() -> new LocalRandom(0));

    @Redirect(method = "renderModel",
              at = @At(value = "FIELD", target = "Lfi/dy/masa/litematica/render/schematic/BlockModelRendererSchematic;random", opcode = Opcodes.GETFIELD))
    private LocalRandom turbo$threadRandom(BlockModelRendererSchematic self)
    {
        return TURBO_RANDOM.get();
    }
}
