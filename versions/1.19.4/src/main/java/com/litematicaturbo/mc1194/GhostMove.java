package com.litematicaturbo.mc1194;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.core.TurboConfig;

/**
 * Moving a placement makes Litematica re-copy and rebuild every chunk it touches, for every single
 * step. While a placement is being moved we instead keep the chunks that were already built
 * ("ghosts") and draw them shifted by the distance moved; all copying is held back. Once the
 * placement has stayed still for a moment the real rebuild runs in the background (ghosts still
 * shown), and when it is complete the view switches over in one frame.
 *
 * Render thread only.
 */
public final class GhostMove
{
    private static final List<Ghost> ghosts = new ArrayList<>();
    private static final List<ChunkRendererSchematicVbo> ghostRenderers = new ArrayList<>();

    private static SchematicPlacement placement;
    private static BlockPos ghostOrigin;
    private static long lastMoveNanos;
    private static boolean inSetOrigin;
    private static BlockPos originBeforeSet;

    private static long lastFrameNanos;
    private static double avgFrameNanos = 5_000_000.0;
    /** Start of the current per-frame catch-up slice, or 0 when copying runs in the client tick. */
    private static long frameSliceStart;

    private GhostMove()
    {
    }

    public static boolean isActive()
    {
        return placement != null;
    }

    /** True while the placement is still moving, so copying and rebuilding should wait. */
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
        WorldRendererSchematic worldRenderer = fi.dy.masa.litematica.render.LitematicaRenderer.getInstance().getWorldRenderer();
        List<ChunkRendererSchematicVbo> detached = ((TurboWorldRenderer) worldRenderer).turbo$detachChunkRenderers();

        if (detached == null)
        {
            return false;
        }

        for (ChunkRendererSchematicVbo renderer : detached)
        {
            // Keep the last finished build; a build still running would upload into a ghost later
            ((TurboRenderer) renderer).turbo$cancelCompile();
            BlockPos origin = renderer.getOrigin();
            ghosts.add(new Ghost(renderer, origin.getX(), origin.getY(), origin.getZ()));
            ghostRenderers.add(renderer);
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

    /**
     * Render-thread time that copying may take right now while catching up after a move.
     * Auto: a quarter of the recent frame time (0.5 - 4 ms), spent every frame instead of only in
     * the 20 client ticks per second - finishes sooner and never makes one frame much longer.
     */
    public static boolean hasCatchUpTimeLeft()
    {
        long now = System.nanoTime();

        if (TurboConfig.get().catchUpAuto)
        {
            long start = frameSliceStart != 0 ? frameSliceStart : DataManager.getClientTickStartTime();
            long budget = (long) Math.max(500_000.0, Math.min(4_000_000.0, avgFrameNanos * 0.25));
            return now - start <= budget;
        }

        return now - DataManager.getClientTickStartTime() <= TurboConfig.get().catchUpBudgetMs * 1_000_000L;
    }

    /** Every frame: catch-up slice, and switch to the real chunks once they are all rebuilt. */
    public static void tick(TurboWorldRenderer worldRenderer)
    {
        long now = System.nanoTime();

        if (lastFrameNanos != 0)
        {
            long frame = Math.min(now - lastFrameNanos, 100_000_000L);
            avgFrameNanos = avgFrameNanos * 0.9 + frame * 0.1;
        }

        lastFrameNanos = now;

        if (isActive() == false || isHoldingWork())
        {
            return;
        }

        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();

        if (TurboConfig.get().catchUpAuto && manager.hasQueuedChunks())
        {
            frameSliceStart = now;

            try
            {
                manager.processQueuedChunks();
            }
            finally
            {
                frameSliceStart = 0;
            }
        }

        if (manager.hasQueuedChunks() == false && worldRenderer.turbo$isIdle())
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

        WorldRendererSchematic worldRenderer = fi.dy.masa.litematica.render.LitematicaRenderer.getInstance().getWorldRenderer();

        for (Ghost ghost : ghosts)
        {
            ((TurboRenderer) ghost.renderer).turbo$forgetBlockEntities(worldRenderer);
            ghost.renderer.deleteGlResources();
        }

        ghosts.clear();
        ghostRenderers.clear();
        placement = null;
        ghostOrigin = null;
    }

    /** World unloaded / renderers reloaded: the ghosts' GL buffers are gone or about to be. */
    public static void reset()
    {
        end();
    }

    private static final class Ghost
    {
        final ChunkRendererSchematicVbo renderer;
        final int x, y, z;

        Ghost(ChunkRendererSchematicVbo renderer, int x, int y, int z)
        {
            this.renderer = renderer;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
