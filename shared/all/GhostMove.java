package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.litematicaturbo.core.TurboConfig;

/**
 * Moving a placement makes Litematica re-copy and rebuild every chunk it touches, for every single
 * step. While a placement is being moved we instead keep the chunks that were already built
 * ("ghosts") and draw them shifted by the distance moved; the new state is prepared out of sight
 * (GhostRenderers: how, on this version). Once the placement has stayed still and everything is
 * built, the view switches over in one frame.
 *
 * Render thread only.
 */
public final class GhostMove
{
    private static final List<ChunkRendererSchematicVbo> ghostRenderers = new ArrayList<>();
    /** Where each ghost was built ({x, y, z}), in the order of ghostRenderers. */
    private static final List<int[]> ghostOrigins = new ArrayList<>();

    private static SchematicPlacement placement;
    private static int[] ghostOrigin;
    private static long lastMoveNanos;
    private static boolean inSetOrigin;
    private static int[] originBeforeSet;

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
        originBeforeSet = GhostRenderers.origin(target);
    }

    /** Called when SchematicPlacement.setOrigin returns. */
    public static void afterSetOrigin(SchematicPlacement target)
    {
        inSetOrigin = false;
        int[] before = originBeforeSet;
        int[] after = GhostRenderers.origin(target);
        originBeforeSet = null;

        if (before == null || Arrays.equals(before, after))
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
        applyOffset(after[0] - ghostOrigin[0], after[1] - ghostOrigin[1], after[2] - ghostOrigin[2]);
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

    private static boolean start(SchematicPlacement target, int[] originBefore)
    {
        List<ChunkRendererSchematicVbo> detached = GhostRenderers.detach();

        if (detached == null)
        {
            return false;
        }

        for (ChunkRendererSchematicVbo renderer : detached)
        {
            // Keep the last finished build; a build still running would upload into a ghost later
            ((TurboRenderer) renderer).turbo$cancelCompile();
            ghostOrigins.add(GhostRenderers.origin(renderer));
            ghostRenderers.add(renderer);
        }

        placement = target;
        ghostOrigin = originBefore;
        return true;
    }

    private static void applyOffset(int dx, int dy, int dz)
    {
        for (int i = 0; i < ghostRenderers.size(); i++)
        {
            int[] origin = ghostOrigins.get(i);
            ((TurboRenderer) ghostRenderers.get(i)).turbo$moveTo(origin[0] + dx, origin[1] + dy, origin[2] + dz);
        }
    }

    /** Drops the ghosts (rebuild done, or something other than a move happened). */
    public static void end()
    {
        if (isActive() == false)
        {
            return;
        }

        for (ChunkRendererSchematicVbo renderer : ghostRenderers)
        {
            GhostRenderers.discard(renderer);
        }

        ghostRenderers.clear();
        ghostOrigins.clear();
        GhostRenderers.clear();
        placement = null;
        ghostOrigin = null;
    }

    /** World unloaded / renderers reloaded: the ghosts' GL buffers are gone or about to be. */
    public static void reset()
    {
        end();
    }
}
