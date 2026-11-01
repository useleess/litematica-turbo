package com.litematicaturbo.shared.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import fi.dy.masa.malilib.util.IntBoundingBox;

import com.litematicaturbo.shared.CompletePiece;
import com.litematicaturbo.shared.EmptyChunkView;

/** A piece of the complete copy (CompletePieces) compares with an all-air world, draws no overlay and keeps out of the world renderer's block entities. */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboCompleteMixin implements CompletePiece
{
    @Shadow protected ChunkCacheSchematic schematicWorldView;
    @Shadow protected ChunkCacheSchematic clientWorldView;
    @Shadow @Final protected List<IntBoundingBox> boxes;
    @Shadow @Final protected BlockPos.Mutable position;

    @Unique
    private volatile boolean turbo$complete;
    @Unique
    private volatile List<IntBoundingBox> turbo$completeBoxes;
    @Unique
    private volatile int turbo$dataGeneration;

    @Override
    public void turbo$makeComplete(List<IntBoundingBox> boxes)
    {
        this.turbo$completeBoxes = new ArrayList<>(boxes);
        this.turbo$complete = true;
    }

    @Override
    public boolean turbo$isComplete()
    {
        return this.turbo$complete;
    }

    @Override
    public List<IntBoundingBox> turbo$boxes()
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
        BlockPos.Mutable pos = new BlockPos.Mutable();

        synchronized (this.boxes)
        {
            for (IntBoundingBox box : this.boxes)
            {
                for (int x = box.minX; x <= box.maxX; x++)
                {
                    for (int y = box.minY; y <= box.maxY; y++)
                    {
                        for (int z = box.minZ; z <= box.maxZ; z++)
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

    @Inject(method = "setChunkRenderData", at = @At("RETURN"))
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
            MinecraftClient mc = MinecraftClient.getInstance();
            this.clientWorldView = new EmptyChunkView(mc.world, mc.world, this.position);

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

    @Redirect(method = "rebuildChunk",
              at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/render/schematic/WorldRendererSchematic;updateBlockEntities(Ljava/util/Collection;Ljava/util/Collection;)V"))
    private void turbo$completeKeepsBlockEntities(WorldRendererSchematic worldRenderer, Collection<BlockEntity> remove, Collection<BlockEntity> add)
    {
        if (this.turbo$complete == false)
        {
            worldRenderer.updateBlockEntities(remove, add);
        }
    }
}
