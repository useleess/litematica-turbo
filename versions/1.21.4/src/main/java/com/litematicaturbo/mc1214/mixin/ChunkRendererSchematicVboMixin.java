package com.litematicaturbo.mc1214.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;

import com.litematicaturbo.core.SectionSlices;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.mc1214.CellIterable;
import com.litematicaturbo.mc1214.TurboRenderer;

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
    @Shadow protected abstract boolean needsUpdate();
    @Shadow protected abstract void setNeedsUpdate(boolean immediate);
    @Shadow protected abstract void deleteGlResources();

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

    /**
     * Each render chunk is a whole 16 x worldHeight x 16 column and stock Litematica looks at every
     * cell of the schematic boxes in it (two block lookups per cell, air included). Only visit the
     * 16-high sections where the schematic has blocks, or where the world has blocks that the
     * "extra block" overlay would mark. Skipped cells would have produced nothing.
     */
    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE", remap = true,
                       target = "Lnet/minecraft/util/math/BlockPos$Mutable;iterate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)Ljava/lang/Iterable;"))
    private Iterable<BlockPos> turbo$iterateNeededSections(BlockPos from, BlockPos to)
    {
        if (TurboConfig.get().skipEmptySectionsEnabled() == false ||
            (from.getX() >> 4) != (to.getX() >> 4) || (from.getZ() >> 4) != (to.getZ() >> 4))
        {
            return BlockPos.Mutable.iterate(from, to);
        }

        int chunkX = from.getX() >> 4;
        int chunkZ = from.getZ() >> 4;
        WorldChunk schematicChunk = turbo$chunkAt(this.schematicWorldView, chunkX, chunkZ);
        WorldChunk clientChunk = turbo$chunkAt(this.clientWorldView, chunkX, chunkZ);
        boolean extraOverlay = Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.getBooleanValue() &&
                               Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_EXTRA.getBooleanValue();

        int[] ranges = SectionSlices.neededRanges(from.getY(), to.getY(), sectionY ->
                turbo$hasBlocks(schematicChunk, sectionY) || (extraOverlay && turbo$hasBlocks(clientChunk, sectionY)));

        return new CellIterable(from.getX(), from.getZ(), to.getX(), to.getZ(), ranges);
    }

    @Unique
    private static WorldChunk turbo$chunkAt(ChunkCacheSchematic view, int chunkX, int chunkZ)
    {
        if (view == null)
        {
            return null;
        }

        ChunkCacheSchematicAccessor cache = (ChunkCacheSchematicAccessor) view;
        WorldChunk[][] chunks = cache.turbo$getChunkArray();
        int cx = chunkX - cache.turbo$getChunkStartX();
        int cz = chunkZ - cache.turbo$getChunkStartZ();

        if (chunks == null || cx < 0 || cx >= chunks.length || cz < 0 || cz >= chunks[cx].length)
        {
            return null;
        }

        return chunks[cx][cz];
    }

    @Unique
    private static boolean turbo$hasBlocks(WorldChunk chunk, int sectionY)
    {
        if (chunk == null)
        {
            return false;
        }

        ChunkSection[] sections = chunk.getSectionArray();
        int index = chunk.sectionCoordToIndex(sectionY);

        return index >= 0 && index < sections.length && sections[index] != null && sections[index].isEmpty() == false;
    }

    /**
     * Build code sets the shader (no effect on the built buffers); that is render-thread only
     * state, so only make the call on the render thread.
     */
    @Redirect(method = { "resortTransparency", "preRenderOverlay" }, expect = 2,
              at = @At(value = "INVOKE", remap = true,
                       target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Lnet/minecraft/client/gl/ShaderProgramKey;)Lnet/minecraft/client/gl/ShaderProgram;"))
    private ShaderProgram turbo$setShaderOnRenderThread(ShaderProgramKey shader)
    {
        return RenderSystem.isOnRenderThread() ? RenderSystem.setShader(shader) : null;
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
