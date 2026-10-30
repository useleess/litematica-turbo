package com.litematicaturbo.shared.mixin;

import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.shared.EmptySections;
import com.litematicaturbo.shared.TurboBuilt;
import com.litematicaturbo.shared.TurboRenderer;

@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboMixin implements TurboRenderer
{
    @Shadow protected ChunkCacheSchematic schematicWorldView;
    @Shadow protected ChunkCacheSchematic clientWorldView;
    @Shadow @Final protected BlockPos.MutableBlockPos position;
    @Shadow protected ChunkPos chunkPosition;
    @Shadow private AABB boundingBox;

    @Shadow protected abstract ChunkRenderDataSchematic getChunkRenderData();
    @Shadow protected abstract ChunkPos getChunkPos();
    @Shadow protected abstract void finishCompileTask();
    @Shadow protected abstract boolean needsUpdate();
    @Shadow protected abstract void setNeedsUpdate(boolean immediate);
    @Shadow protected abstract void deleteGlResources();

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
        // The chunk position stays: it names the GPU uploader that holds this chunk's buffers.
        // Not setPosition(): that would throw the built data away.
        this.getChunkPos();
        this.position.set(x, y, z);
        this.boundingBox = null;
    }

    @Override
    public boolean turbo$needsUpdate()
    {
        return this.needsUpdate();
    }

    @Override
    public void turbo$setNeedsUpdate(boolean immediate)
    {
        this.setNeedsUpdate(immediate);
    }

    @Override
    public void turbo$deleteGlResources()
    {
        this.deleteGlResources();
    }

    /** Only visit the 16-high sections that can hold something to draw (EmptySections). */
    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE", remap = true,
                       target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"))
    private Iterable<BlockPos> turbo$iterateNeededSections(BlockPos from, BlockPos to)
    {
        return EmptySections.iterate(from, to, this.schematicWorldView, this.clientWorldView);
    }

    /**
     * A rebuild starts by clearing "the task's data", which stock hands out as the chunk's published
     * data while the task has none of its own: a build thread would empty the block entity list that
     * the render thread is drawing from. The build works on its own fresh data instead.
     */
    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE",
                       target = "Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;getChunkRenderData()Lfi/dy/masa/litematica/render/schematic/ChunkRenderDataSchematic;"))
    private ChunkRenderDataSchematic turbo$buildOnFreshData(ChunkRenderTaskSchematic task)
    {
        return new ChunkRenderDataSchematic();
    }

    /**
     * Stock publishes the finished data here, on the build thread, and clears the old published
     * data. Hold it back: processTask uploads it and publishes it on the render thread, where
     * nothing else walks the old lists at the same time.
     */
    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE",
                       target = "Lfi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo;updateChunkRenderData(Lfi/dy/masa/litematica/render/schematic/ChunkRenderDataSchematic;)V"))
    private void turbo$holdBuiltData(ChunkRendererSchematicVbo self, ChunkRenderDataSchematic data)
    {
        TurboBuilt.set(data);
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
