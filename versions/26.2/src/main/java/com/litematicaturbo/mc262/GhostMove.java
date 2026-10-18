package com.litematicaturbo.mc262;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.mc262.mixin.ChunkRenderGpuUploaderAccessor;
import com.litematicaturbo.mc262.mixin.ChunkRendererSchematicVboInvoker;

/**
 * Moving a placement makes Litematica re-copy and rebuild every chunk it touches, for every single
 * step. While a placement is being moved we instead keep the chunks that were already built
 * ("ghosts", with the GPU uploaders that hold their buffers) and draw them shifted by the
 * distance moved. Litematica 0.26 copies on its own background threads and Turbo builds on its
 * build threads, so the new state is prepared out of sight; once the placement has stayed still
 * and everything is built, the view switches over in one frame.
 *
 * Render thread only.
 */
public final class GhostMove
{
    private static final List<Ghost> ghosts = new ArrayList<>();
    private static final List<ChunkRendererSchematicVbo> ghostRenderers = new ArrayList<>();
    /** Original chunk (packed) -> the uploader holding that ghost's buffers. */
    private static final Map<Long, ChunkRenderGpuUploader> ghostUploaders = new HashMap<>();

    private static SchematicPlacement placement;
    private static BlockPos ghostOrigin;
    private static long lastMoveNanos;
    private static boolean inSetOrigin;
    private static BlockPos originBeforeSet;
    /** True while the block layers / overlay are drawn from the ghosts. */
    private static boolean drawingGhosts;

    private GhostMove()
    {
    }

    public static boolean isActive()
    {
        return placement != null;
    }

    /** True while the placement is still moving. */
    public static boolean isHoldingWork()
    {
        return isActive() && (System.nanoTime() - lastMoveNanos) < TurboConfig.get().ghostSettleMs * 1_000_000L;
    }

    public static List<ChunkRendererSchematicVbo> renderers()
    {
        return ghostRenderers;
    }

    public static boolean isInSetOrigin()
    {
        return inSetOrigin;
    }

    public static void setDrawingGhosts(boolean drawing)
    {
        drawingGhosts = drawing;
    }

    /** While ghosts are drawn: the uploader holding the ghost buffers of that (original) chunk. */
    public static ChunkRenderGpuUploader ghostUploader(int chunkX, int chunkZ)
    {
        return drawingGhosts ? ghostUploaders.get(ChunkPos.pack(chunkX, chunkZ)) : null;
    }

    /** Called at the start of SchematicPlacement.setOrigin. */
    public static void beforeSetOrigin(SchematicPlacement target)
    {
        inSetOrigin = true;
        originBeforeSet = target.getOrigin();
    }

    /** Called when SchematicPlacement.setOrigin returns. */
    public static void afterSetOrigin(SchematicPlacement target)
    {
        inSetOrigin = false;
        BlockPos before = originBeforeSet;
        BlockPos after = target.getOrigin();
        originBeforeSet = null;

        if (before == null || before.equals(after))
        {
            return;
        }

        if (isActive() && placement != target)
        {
            end();
        }

        if (isActive() == false)
        {
            if (eligible(target) == false || start(target, before) == false)
            {
                return;
            }
        }

        lastMoveNanos = System.nanoTime();
        applyOffset(after.subtract(ghostOrigin));
    }

    /** Only a lone placement: the built chunks then contain nothing else that would move along with it. */
    private static boolean eligible(SchematicPlacement target)
    {
        if (TurboConfig.get().ghostMoveEnabled() == false || target.isEnabled() == false || target.isRenderingEnabled() == false)
        {
            return false;
        }

        for (SchematicPlacement other : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements())
        {
            if (other != target && other.isEnabled())
            {
                return false;
            }
        }

        return true;
    }

    private static boolean start(SchematicPlacement target, BlockPos originBefore)
    {
        Map<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> detached =
                ((TurboWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer()).turbo$detachChunkRenderers();

        if (detached == null)
        {
            return false;
        }

        for (Map.Entry<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> entry : detached.entrySet())
        {
            ChunkRendererSchematicVbo renderer = entry.getKey();
            // Keep the last finished build; a build still running would upload into a ghost later
            ((TurboRenderer) renderer).turbo$cancelCompile();
            ChunkPos chunk = ((ChunkRendererSchematicVboInvoker) renderer).turbo$getChunkPos();
            BlockPos origin = renderer.getOrigin();
            ghosts.add(new Ghost(renderer, entry.getValue(), origin.getX(), origin.getY(), origin.getZ()));
            ghostRenderers.add(renderer);

            if (entry.getValue() != null)
            {
                ghostUploaders.put(ChunkPos.pack(chunk.x(), chunk.z()), entry.getValue());
            }
        }

        placement = target;
        ghostOrigin = originBefore;
        return true;
    }

    private static void applyOffset(BlockPos offset)
    {
        for (Ghost ghost : ghosts)
        {
            ((TurboRenderer) ghost.renderer).turbo$moveTo(ghost.x + offset.getX(), ghost.y + offset.getY(), ghost.z + offset.getZ());
        }
    }

    /** Every frame: switch to the real chunks once the placement is still and they are all built. */
    public static void tick(TurboWorldRenderer worldRenderer)
    {
        if (isActive() == false || isHoldingWork())
        {
            return;
        }

        if (PlacementManagerDaemonHandler.INSTANCE.hasAnyTasks() == false && worldRenderer.turbo$isIdle())
        {
            end();
        }
    }

    /** Drops the ghosts (rebuild done, or something other than a move happened). */
    public static void end()
    {
        if (isActive() == false)
        {
            return;
        }

        for (Ghost ghost : ghosts)
        {
            if (ghost.uploader != null)
            {
                ((ChunkRenderGpuUploaderAccessor) ghost.uploader).turbo$clear();
            }

            ((TurboRenderer) ghost.renderer).turbo$deleteGlResources();
        }

        ghosts.clear();
        ghostRenderers.clear();
        ghostUploaders.clear();
        placement = null;
        ghostOrigin = null;
        drawingGhosts = false;
    }

    /** World unloaded / renderers reloaded: the ghosts' GL buffers are gone or about to be. */
    public static void reset()
    {
        end();
    }

    private static final class Ghost
    {
        final ChunkRendererSchematicVbo renderer;
        final ChunkRenderGpuUploader uploader;
        final int x, y, z;

        Ghost(ChunkRendererSchematicVbo renderer, ChunkRenderGpuUploader uploader, int x, int y, int z)
        {
            this.renderer = renderer;
            this.uploader = uploader;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
