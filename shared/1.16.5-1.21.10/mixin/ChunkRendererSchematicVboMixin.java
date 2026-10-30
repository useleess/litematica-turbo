package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.shared.EmptySections;
import com.litematicaturbo.shared.TurboRenderer;

@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboMixin implements TurboRenderer
{
    @Shadow protected ChunkCacheSchematic schematicWorldView;
    @Shadow protected ChunkCacheSchematic clientWorldView;
    @Shadow public abstract ChunkRenderDataSchematic getChunkRenderData();
    @Shadow @Final protected BlockPos.Mutable position;
    @Shadow @Final protected Set<BlockEntity> setBlockEntities;
    @Shadow private Box boundingBox;
    @Shadow protected abstract void finishCompileTask();

    @Unique
    private final ReentrantLock turbo$buildLock = new ReentrantLock();

    @Override
    public ReentrantLock turbo$buildLock()
    {
        return this.turbo$buildLock;
    }

    @Override
    public void turbo$cancelCompile()
    {
        this.finishCompileTask();
    }

    @Override
    public void turbo$moveTo(int x, int y, int z)
    {
        // Not setPosition(): that would throw the built buffers away
        this.position.set(x, y, z);
        this.boundingBox = null;
    }

    @Override
    public void turbo$forgetBlockEntities(WorldRendererSchematic worldRenderer)
    {
        worldRenderer.updateBlockEntities(new ArrayList<>(this.setBlockEntities), Collections.emptyList());
        this.setBlockEntities.clear();
    }

    /** Only visit the 16-high sections that can hold something to draw (EmptySections). */
    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE", remap = true,
                       target = "Lnet/minecraft/util/math/BlockPos$Mutable;iterate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)Ljava/lang/Iterable;"))
    private Iterable<BlockPos> turbo$iterateNeededSections(BlockPos from, BlockPos to)
    {
        return EmptySections.iterate(from, to, this.schematicWorldView, this.clientWorldView);
    }

    /**
     * Stock hasOverlay() is a field the build clears at its start, so with threaded builds the
     * overlay of a chunk would blink off while it rebuilds. Answer from the finished data instead.
     */
    @Inject(method = "hasOverlay", at = @At("HEAD"), cancellable = true)
    private void turbo$hasOverlayFromBuiltData(CallbackInfoReturnable<Boolean> cir)
    {
        if (TurboConfig.get().threadingEnabled())
        {
            cir.setReturnValue(this.getChunkRenderData().isOverlayEmpty() == false);
        }
    }
}
