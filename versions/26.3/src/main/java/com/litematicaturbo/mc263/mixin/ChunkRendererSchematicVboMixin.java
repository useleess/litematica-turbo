package com.litematicaturbo.mc263.mixin;

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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.SectionSlices;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.mc263.CellIterable;
import com.litematicaturbo.mc263.TurboBuilt;
import com.litematicaturbo.mc263.TurboRenderer;

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

    /**
     * Each render chunk is a whole 16 x worldHeight x 16 column and stock Litematica looks at every
     * cell of the schematic boxes in it (two block lookups per cell, air included). Only visit the
     * 16-high sections where the schematic has blocks, or where the world has blocks that the
     * "extra block" overlay would mark. Skipped cells would have produced nothing. The cells that
     * are left are visited in vanilla's order, so the built data is the same as stock.
     */
    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE", remap = true,
                       target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"))
    private Iterable<BlockPos> turbo$iterateNeededSections(BlockPos from, BlockPos to)
    {
        if (TurboConfig.get().skipEmptySectionsEnabled() == false ||
            (from.getX() >> 4) != (to.getX() >> 4) || (from.getZ() >> 4) != (to.getZ() >> 4))
        {
            return BlockPos.MutableBlockPos.betweenClosed(from, to);
        }

        int chunkX = from.getX() >> 4;
        int chunkZ = from.getZ() >> 4;
        LevelChunk schematicChunk = turbo$chunkAt(this.schematicWorldView, chunkX, chunkZ);
        LevelChunk clientChunk = turbo$chunkAt(this.clientWorldView, chunkX, chunkZ);
        boolean extraOverlay = Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.getBooleanValue() &&
                               Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_EXTRA.getBooleanValue();

        int[] ranges = SectionSlices.neededRanges(from.getY(), to.getY(), sectionY ->
                turbo$hasBlocks(schematicChunk, sectionY) || (extraOverlay && turbo$hasBlocks(clientChunk, sectionY)));

        return new CellIterable(from.getX(), from.getZ(), to.getX(), to.getZ(), ranges);
    }

    @Unique
    private static LevelChunk turbo$chunkAt(ChunkCacheSchematic view, int chunkX, int chunkZ)
    {
        if (view == null)
        {
            return null;
        }

        ChunkCacheSchematicAccessor cache = (ChunkCacheSchematicAccessor) view;
        LevelChunk[][] chunks = cache.turbo$getChunkArray();
        int cx = chunkX - cache.turbo$getChunkStartX();
        int cz = chunkZ - cache.turbo$getChunkStartZ();

        if (chunks == null || cx < 0 || cx >= chunks.length || cz < 0 || cz >= chunks[cx].length)
        {
            return null;
        }

        return chunks[cx][cz];
    }

    @Unique
    private static boolean turbo$hasBlocks(LevelChunk chunk, int sectionY)
    {
        if (chunk == null)
        {
            return false;
        }

        LevelChunkSection[] sections = chunk.getSections();
        int index = chunk.getSectionIndexFromSectionY(sectionY);

        return index >= 0 && index < sections.length && sections[index] != null && sections[index].hasOnlyAir() == false;
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
