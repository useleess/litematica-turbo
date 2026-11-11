package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.litematicaturbo.core.CompleteCopy;
import com.litematicaturbo.core.TurboConfig;

/**
 * Moving a placement makes Litematica re-copy and rebuild every chunk it touches, for every single
 * step. While a placement is being moved we instead keep the chunks that were already built
 * ("ghosts") and draw them shifted by the distance moved; the new state is prepared out of sight
 * (GhostRenderers: how, on this version). Once the placement has stayed still and everything is
 * built, the view switches over in one frame.
 *
 * Only the moved placement's chunk columns become ghosts; the other placements stay drawn from their real chunks. A
 * chunk column's renderer holds the blocks of every placement in it, so a placement sharing a column with another
 * one moves the normal way.
 *
 * Render thread only.
 */
public final class GhostMove
{
    private static final List<ChunkRendererSchematicVbo> ghostRenderers = new ArrayList<>();
    /** Where each ghost was built ({x, y, z}), in the order of ghostRenderers. */
    private static final List<int[]> ghostOrigins = new ArrayList<>();
    /** The chunk columns of the other placements, whose real chunks are drawn during the move. */
    private static final Set<Long> otherColumns = new HashSet<>();
    private static final List<ChunkRendererSchematicVbo> drawn = new ArrayList<>();

    private static SchematicPlacement placement;
    private static int[] ghostOrigin;
    private static long lastMoveNanos;
    private static boolean inSetOrigin;
    private static int[] originBeforeSet;
    private static Set<Long> columnsBeforeSet;

    private GhostMove()
    {
    }

    public static boolean isActive()
    {
        return placement != null;
    }

    /**
     * True while the placement is still moving, so copying and rebuilding should wait: until it has been still for the settle
     * time, and a little longer (at most CompleteCopy.MAX_HOLD_NANOS) for the complete copy's last builds. When the wait is
     * over the real copy is about to change the schematic world, which the copy's unfinished builds must not read.
     */
    public static boolean isHoldingWork()
    {
        if (isActive() == false)
        {
            return false;
        }

        long still = System.nanoTime() - lastMoveNanos;
        boolean holding = still < TurboConfig.get().ghostSettleMs * 1_000_000L || (still < CompleteCopy.MAX_HOLD_NANOS && CompletePieces.isBuilding());

        if (holding == false)
        {
            CompletePieces.beforeWorldChange();
        }

        return holding;
    }

    /** The old pieces, drawn shifted: the overlay is drawn from these. */
    public static List<ChunkRendererSchematicVbo> renderers()
    {
        return ghostRenderers;
    }

    /**
     * What the block layers draw: the old pieces, with the complete copy's finished pieces in place of theirs, and the
     * other placements' chunks among {@code live} (the real visible chunks).
     */
    public static List<ChunkRendererSchematicVbo> blockRenderers(List<ChunkRendererSchematicVbo> live)
    {
        return withOthers(CompletePieces.drawList(placement, ghostOrigin, ghostRenderers, ghostOrigins), live);
    }

    /** What the overlay draws: the old pieces and the other placements' chunks among {@code live}. */
    public static List<ChunkRendererSchematicVbo> overlayRenderers(List<ChunkRendererSchematicVbo> live)
    {
        return withOthers(ghostRenderers, live);
    }

    private static List<ChunkRendererSchematicVbo> withOthers(List<ChunkRendererSchematicVbo> moved, List<ChunkRendererSchematicVbo> live)
    {
        drawn.clear();
        drawn.addAll(moved);

        if (otherColumns.isEmpty() == false)
        {
            for (ChunkRendererSchematicVbo renderer : live)
            {
                if (otherColumns.contains(GhostRenderers.column(renderer)))
                {
                    drawn.add(renderer);
                }
            }
        }

        return drawn;
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
        columnsBeforeSet = isActive() && placement == target ? null : GhostRenderers.touchedColumns(target);
    }

    /** Called when SchematicPlacement.setOrigin returns. */
    public static void afterSetOrigin(SchematicPlacement target)
    {
        inSetOrigin = false;
        int[] before = originBeforeSet;
        int[] after = GhostRenderers.origin(target);
        Set<Long> columnsBefore = columnsBeforeSet;
        originBeforeSet = null;
        columnsBeforeSet = null;

        if (before == null || Arrays.equals(before, after))
        {
            return;
        }

        if (isActive() && placement != target)
        {
            endAndDropCopy();
        }

        if (isActive() == false)
        {
            if (usable(target) == false || columnsBefore == null || start(target, before, columnsBefore) == false)
            {
                return;
            }
        }

        lastMoveNanos = System.nanoTime();
        applyOffset(after[0] - ghostOrigin[0], after[1] - ghostOrigin[1], after[2] - ghostOrigin[2]);
    }

    private static boolean usable(SchematicPlacement target)
    {
        return TurboConfig.get().ghostMoveEnabled() && target.isEnabled() && target.isRenderingEnabled();
    }

    /** The chunk columns of the other enabled placements. */
    private static Set<Long> columnsOfOthers(SchematicPlacement target)
    {
        Set<Long> out = new HashSet<>();

        for (SchematicPlacement other : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements())
        {
            if (other != target && other.isEnabled())
            {
                out.addAll(GhostRenderers.touchedColumns(other));
            }
        }

        return out;
    }

    /** Starts the ghosts of the placement's own columns (where it was); not when another placement has blocks in one of them. */
    private static boolean start(SchematicPlacement target, int[] originBefore, Set<Long> columnsBefore)
    {
        Set<Long> others = columnsOfOthers(target);

        for (Long column : columnsBefore)
        {
            if (others.contains(column))
            {
                return false;
            }
        }

        List<ChunkRendererSchematicVbo> detached = GhostRenderers.detach(columnsBefore);

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
        otherColumns.addAll(others);
        CompletePieces.onMoveStart(target, ghostRenderers, ghostOrigins, originBefore);
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

    /** The placement changed in another way than moving, was removed, or the renderers reloaded: the ghosts and the complete copy go. */
    public static void endAndDropCopy()
    {
        end();
        CompletePieces.drop();
    }

    /** Drops the ghosts (rebuild done, or something other than a move happened); the complete copy stays for the next move. */
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
        otherColumns.clear();
        drawn.clear();
        GhostRenderers.clear();
        placement = null;
        ghostOrigin = null;
    }

    /** World unloaded / renderers reloaded: the ghosts' GL buffers are gone or about to be. */
    public static void reset()
    {
        endAndDropCopy();
    }
}
