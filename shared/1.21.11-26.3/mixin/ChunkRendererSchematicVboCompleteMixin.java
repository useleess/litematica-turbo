package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.render.IWorldSchematicRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.shared.CompleteBoxes;
import com.litematicaturbo.shared.CompletePiece;
import com.litematicaturbo.shared.CompleteUploads;
import com.litematicaturbo.shared.EmptyChunkView;

/**
 * A piece of the complete copy (CompletePieces) compares with an all-air world, draws no overlay and keeps its graphics
 * buffers in a GPU uploader of its own (CompleteUploads). Its block entities stay in its own build data, which only the
 * block layers read.
 */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboCompleteMixin implements CompletePiece, CompleteUploads
{
    @Shadow protected WorldSchematic world;
    @Shadow @Final protected IWorldSchematicRenderer worldRenderer;
    @Shadow protected ChunkCacheSchematic schematicWorldView;
    @Shadow protected ChunkCacheSchematic clientWorldView;
    @Shadow @Final protected List<Object> boxes;
    @Shadow @Final protected BlockPos.MutableBlockPos position;
    @Shadow protected ChunkPos chunkPosition;

    @Shadow protected abstract ChunkPos getChunkPos();

    @Unique
    private volatile boolean turbo$complete;
    @Unique
    private volatile List<Object> turbo$completeBoxes;
    @Unique
    private volatile int turbo$dataGeneration;
    @Unique
    private volatile ChunkRenderGpuUploader turbo$uploader;

    /** The piece was moved to its place after it was made (TurboRenderer.turbo$moveTo keeps the chunk): it belongs to the chunk it is at. */
    @Override
    public void turbo$makeComplete(List<?> boxes)
    {
        this.turbo$completeBoxes = new ArrayList<>(boxes);
        this.chunkPosition = null;
        this.getChunkPos();
        ChunkRenderGpuUploader uploader = ChunkRenderGpuUploaderAccessor.turbo$create(this.world, this.worldRenderer);
        ((ChunkRenderGpuUploaderAccessor) uploader).turbo$setPosition(this.position.getX(), this.position.getY(), this.position.getZ());
        this.turbo$uploader = uploader;
        this.turbo$complete = true;
    }

    @Override
    public boolean turbo$isComplete()
    {
        return this.turbo$complete;
    }

    @Override
    public ChunkRenderGpuUploader turbo$completeUploader()
    {
        return this.turbo$uploader;
    }

    @Override
    public List<?> turbo$boxes()
    {
        synchronized (this.boxes)
        {
            return new ArrayList<>(this.boxes);
        }
    }

    @Override
    public int turbo$dataGeneration()
    {
        return this.turbo$dataGeneration;
    }

    @Override
    public long turbo$drawnBlocks()
    {
        long blocks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        synchronized (this.boxes)
        {
            for (Object box : this.boxes)
            {
                int[] b = CompleteBoxes.bounds(box);

                for (int x = b[0]; x <= b[3]; x++)
                {
                    for (int y = b[1]; y <= b[4]; y++)
                    {
                        for (int z = b[2]; z <= b[5]; z++)
                        {
                            pos.set(x, y, z);

                            if (this.schematicWorldView.getBlockState(pos).isAir() == false && this.clientWorldView.getBlockState(pos).isAir())
                            {
                                blocks++;
                            }
                        }
                    }
                }
            }
        }

        return blocks;
    }

    /** The upload of a build is in place (render thread). */
    @Inject(method = "updateChunkRenderData", at = @At("RETURN"))
    private void turbo$countData(CallbackInfo ci)
    {
        this.turbo$dataGeneration++;
    }

    /** The boxes are the placement's from before the move: Litematica's own are already the moved ones, while the schematic world still holds the blocks at the old place. */
    @Inject(method = "rebuildWorldView", at = @At("RETURN"))
    private void turbo$completeView(CallbackInfo ci)
    {
        if (this.turbo$complete)
        {
            Minecraft mc = Minecraft.getInstance();
            this.clientWorldView = new EmptyChunkView(mc.level, mc.level, this.position);

            synchronized (this.boxes)
            {
                this.boxes.clear();
                this.boxes.addAll(this.turbo$completeBoxes);
            }
        }
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void turbo$completeHasNoOverlay(CallbackInfo ci)
    {
        if (this.turbo$complete)
        {
            ci.cancel();
        }
    }

    @Inject(method = "deleteGlResources", at = @At("RETURN"))
    private void turbo$deleteOwnBuffers(CallbackInfo ci)
    {
        ChunkRenderGpuUploader uploader = this.turbo$uploader;

        if (uploader != null)
        {
            ((ChunkRenderGpuUploaderAccessor) uploader).turbo$clear();
        }
    }
}
