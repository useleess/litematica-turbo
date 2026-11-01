package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.core.CompleteCopy;
import com.litematicaturbo.core.MemoryBudget;
import com.litematicaturbo.core.TurboLog;

/**
 * The game side of the complete copy (rules and switch-off: core CompleteCopy). When a move starts, one complete piece is
 * planned for every ghost that holds part of the placement (its boxes as they were before the move), nearest first; they are
 * built on the build threads a few at a time and kept while they fit the memory limit. While moving, a finished piece is
 * drawn shifted in place of its ghost; a ghost is drawn only where the copy has no finished piece yet. The copy is kept
 * until the placement is deselected, changed or removed, so the next move shows it at once. Any error switches it off:
 * the copy is dropped and the ghosts are drawn as before. Render thread only.
 */
public final class CompletePieces
{
    private enum State { NEW, QUEUED, KEPT, REJECTED }

    private static final class Piece
    {
        final int x;
        final int y;
        final int z;
        final double distance;
        /** The ghost's boxes, as Litematica holds them. */
        final List<?> boxes;
        ChunkRendererSchematicVbo renderer;
        State state = State.NEW;
        int submitGeneration;
        long blocks;
        long bytes;
        int[] shift = {0, 0, 0};
        boolean deleted;

        Piece(int x, int y, int z, double distance, List<?> boxes)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
            this.boxes = boxes;
        }
    }

    private static final List<Piece> PIECES = new ArrayList<>();
    private static final Map<Long, Piece> BY_POSITION = new HashMap<>();
    /** The positions of all the ghosts when the copy was planned; a ghost elsewhere is not covered by it. */
    private static final Set<Long> SCOPE = new HashSet<>();
    private static final List<ChunkRendererSchematicVbo> DRAW = new ArrayList<>();

    private static SchematicPlacement placement;
    private static SchematicPlacement selectedAtStart;
    /** The placement's origin when the copy was planned. */
    private static int[] origin;
    private static int next;
    private static int decided;
    private static long budget;
    private static long used;
    private static boolean budgetHit;
    private static boolean stale;
    private static long lastMemoryCheck;
    private static long lastMaxMemory;
    private static int freed;
    private static int drawnComplete;

    private CompletePieces()
    {
    }

    /** A piece's position (a multiple of 16 on each axis) as one number. */
    private static long key(int x, int y, int z)
    {
        return ((long) (x >> 4) & 0x3FFFFFL) << 42 | ((long) (y >> 4) & 0xFFFFFL) << 22 | (long) (z >> 4) & 0x3FFFFFL;
    }

    /** A move of this placement starts; the ghosts were just taken from the world renderer (built at {@code ghostOrigins}). */
    public static void onMoveStart(SchematicPlacement target, List<ChunkRendererSchematicVbo> ghosts, List<int[]> ghostOrigins, int[] originBefore)
    {
        try
        {
            CompleteCopy.checkFaultForTest();

            if (CompleteCopy.active() == false)
            {
                drop();
                return;
            }

            if (placement == target && stale == false && CompleteCopy.budget(MemoryBudget.maxMemory(), gpuFreeKb()) >= used)
            {
                return; // the kept copy is shown at once; the limit is checked again at each move start
            }

            drop();

            if (CompleteCopy.memoryTight(gpuFreeKb()))
            {
                CompleteCopy.noteSkippedLowMemory();
                return;
            }

            double[] camera = GhostRenderers.cameraPos();

            if (camera == null)
            {
                return;
            }

            for (int i = 0; i < ghosts.size(); i++)
            {
                int[] at = ghostOrigins.get(i);
                SCOPE.add(key(at[0], at[1], at[2]));
                List<?> boxes = ((CompletePiece) ghosts.get(i)).turbo$boxes();

                if (boxes.isEmpty() == false)
                {
                    double dx = at[0] + 8 - camera[0];
                    double dy = at[1] + 8 - camera[1];
                    double dz = at[2] + 8 - camera[2];
                    Piece piece = new Piece(at[0], at[1], at[2], dx * dx + dy * dy + dz * dz, boxes);
                    PIECES.add(piece);
                    BY_POSITION.put(key(at[0], at[1], at[2]), piece);
                }
            }

            PIECES.sort(Comparator.comparingDouble(p -> p.distance));
            placement = target;
            selectedAtStart = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
            origin = originBefore;
            budget = CompleteCopy.budget(MemoryBudget.maxMemory(), gpuFreeKb());
            CompleteCopy.noteMade();
        }
        catch (RuntimeException e)
        {
            fail("starting a move", e);
        }
    }

    private static long gpuFreeKb()
    {
        return CompleteCopy.gpuFreeKb(GpuMemory.freeKb());
    }

    /** Every frame: keeps the copy honest (selection, memory), takes finished pieces in order, queues the next ones. */
    public static void tick()
    {
        if (placement == null)
        {
            return;
        }

        try
        {
            CompleteCopy.checkFaultForTest();
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();

            if (CompleteCopy.active() == false || manager.getSelectedSchematicPlacement() != selectedAtStart || manager.getAllSchematicsPlacements().contains(placement) == false)
            {
                drop();
                return;
            }

            if (memoryTightNow())
            {
                CompleteCopy.noteDroppedTight();
                drop();
                return;
            }

            takeFinished();

            if (budgetHit)
            {
                giveUpTheRest();
            }
            else if (stale == false && GhostMove.isActive())
            {
                queueNext();
            }
        }
        catch (RuntimeException e)
        {
            fail("the frame step", e);
        }
    }

    private static boolean memoryTightNow()
    {
        long now = System.nanoTime();
        long max = MemoryBudget.maxMemory();

        if (now - lastMemoryCheck <= CompleteCopy.MEMORY_CHECK_NANOS && max == lastMaxMemory)
        {
            return false;
        }

        lastMemoryCheck = now;
        lastMaxMemory = max;
        return CompleteCopy.memoryTight(gpuFreeKb());
    }

    /** Finished pieces are taken in the order they were queued (nearest first): one is kept while it fits the limit; after the first that does not, no further piece is kept. */
    private static void takeFinished()
    {
        while (decided < next)
        {
            Piece p = PIECES.get(decided);

            if (((CompletePiece) p.renderer).turbo$dataGeneration() <= p.submitGeneration || ((TurboRenderer) p.renderer).turbo$isBuilding())
            {
                break;
            }

            p.blocks = ((CompletePiece) p.renderer).turbo$drawnBlocks();
            p.bytes = CompleteCopy.estimateBytes(p.blocks);

            if (budgetHit == false && CompleteCopy.fits(used, p.bytes, budget))
            {
                p.state = State.KEPT;
                used += p.bytes;
            }
            else
            {
                budgetHit = true;
                p.state = State.REJECTED;
                delete(p);
            }

            decided++;
        }
    }

    private static void giveUpTheRest()
    {
        for (int i = decided; i < PIECES.size(); i++)
        {
            Piece p = PIECES.get(i);
            p.state = State.REJECTED;
            delete(p);
        }

        decided = PIECES.size();
        next = decided;
    }

    private static void queueNext()
    {
        CompleteWorldRenderer worldRenderer = (CompleteWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer();
        ChunkRenderDispatcherLitematica dispatcher = worldRenderer.turbo$dispatcher();

        if (dispatcher == null)
        {
            return;
        }

        while (next < PIECES.size() && next - decided < CompleteCopy.IN_FLIGHT)
        {
            Piece p = PIECES.get(next);

            if (p.renderer == null)
            {
                p.renderer = worldRenderer.turbo$newRenderer(p.x, p.y, p.z);
                ((CompletePiece) p.renderer).turbo$makeComplete(p.boxes);
            }

            p.submitGeneration = ((CompletePiece) p.renderer).turbo$dataGeneration();

            if (((TurboDispatcher) dispatcher).turbo$queueLater(p.renderer) == false)
            {
                break;
            }

            ((TurboRenderer) p.renderer).turbo$clearNeedsUpdate();
            p.state = State.QUEUED;
            next++;
        }
    }

    /** True while pieces are still to be built (the move's real rebuild waits a little for them). */
    public static boolean isBuilding()
    {
        return placement != null && stale == false && budgetHit == false && decided < PIECES.size();
    }

    /** The real copy is about to change the schematic world: unfinished pieces would read the new blocks, so they stop; the copy is made again on the next move. */
    public static void beforeWorldChange()
    {
        if (isBuilding() == false)
        {
            return;
        }

        for (int i = decided; i < next; i++)
        {
            ((TurboRenderer) PIECES.get(i).renderer).turbo$cancelCompile();
        }

        stale = true;
    }

    /**
     * What the block layers draw while the placement is moved: the finished complete pieces, moved to where the placement is
     * now, and the ghosts where the copy has no finished piece yet. The ghosts themselves when there is no copy.
     */
    public static List<ChunkRendererSchematicVbo> drawList(SchematicPlacement target, int[] ghostOrigin, List<ChunkRendererSchematicVbo> ghosts, List<int[]> ghostOrigins)
    {
        if (placement == null || placement != target)
        {
            return ghosts;
        }

        try
        {
            DRAW.clear();
            int[] now = GhostRenderers.origin(target);
            int[] shift = {now[0] - origin[0], now[1] - origin[1], now[2] - origin[2]};

            for (Piece p : PIECES)
            {
                if (p.state == State.KEPT)
                {
                    if (java.util.Arrays.equals(p.shift, shift) == false)
                    {
                        ((TurboRenderer) p.renderer).turbo$moveTo(p.x + shift[0], p.y + shift[1], p.z + shift[2]);
                        p.shift = shift.clone();
                    }

                    DRAW.add(p.renderer);
                }
            }

            drawnComplete = DRAW.size();

            for (int i = 0; i < ghosts.size(); i++)
            {
                if (covers(ghostOrigin, ghostOrigins.get(i)) == false)
                {
                    DRAW.add(ghosts.get(i));
                }
            }

            return DRAW;
        }
        catch (RuntimeException e)
        {
            fail("drawing", e);
            return ghosts;
        }
    }

    /**
     * Whether the copy already shows the 16-block piece that stood at {@code at} when the placement's origin was
     * {@code ghostOrigin}: every complete piece it overlaps is finished (or holds nothing of the placement).
     */
    private static boolean covers(int[] ghostOrigin, int[] at)
    {
        int bx = at[0] - ghostOrigin[0] + origin[0];
        int by = at[1] - ghostOrigin[1] + origin[1];
        int bz = at[2] - ghostOrigin[2] + origin[2];

        for (int px = bx >> 4; px <= (bx + 15) >> 4; px++)
        {
            for (int py = by >> 4; py <= (by + 15) >> 4; py++)
            {
                for (int pz = bz >> 4; pz <= (bz + 15) >> 4; pz++)
                {
                    long k = key(px << 4, py << 4, pz << 4);
                    Piece p = BY_POSITION.get(k);

                    if (p == null ? SCOPE.contains(k) == false : p.state != State.KEPT)
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static void delete(Piece p)
    {
        if (p.deleted || p.renderer == null)
        {
            return;
        }

        p.deleted = true;
        ((TurboRenderer) p.renderer).turbo$cancelCompile();
        ((TurboRenderer) p.renderer).turbo$deleteGlResources();
        freed++;
    }

    /** Throws the copy away (deselected, changed, memory tight, switched off): its graphics buffers are deleted. */
    public static void drop()
    {
        for (Piece p : PIECES)
        {
            delete(p);
        }

        forget();
    }

    /** Forgets the copy without touching its pieces. */
    private static void forget()
    {
        PIECES.clear();
        BY_POSITION.clear();
        SCOPE.clear();
        DRAW.clear();
        placement = null;
        selectedAtStart = null;
        origin = null;
        next = 0;
        decided = 0;
        used = 0;
        budgetHit = false;
        stale = false;
        drawnComplete = 0;
    }

    private static void fail(String call, RuntimeException e)
    {
        CompleteCopy.switchOff(call, e);

        try
        {
            drop();
        }
        catch (RuntimeException dropError)
        {
            TurboLog.error("Could not delete the complete copy's pieces: " + dropError);
            forget();
        }
    }

    // ---- what the tests read ----

    public static boolean exists()
    {
        return placement != null;
    }

    public static int pieceCount()
    {
        return PIECES.size();
    }

    public static int keptCount()
    {
        int n = 0;

        for (Piece p : PIECES)
        {
            if (p.state == State.KEPT)
            {
                n++;
            }
        }

        return n;
    }

    /** The blocks the kept pieces draw. */
    public static long keptBlocks()
    {
        long n = 0;

        for (Piece p : PIECES)
        {
            if (p.state == State.KEPT)
            {
                n += p.blocks;
            }
        }

        return n;
    }

    /** Complete pieces in the list the block layers drew last. */
    public static int drawnCompleteCount()
    {
        return drawnComplete;
    }

    /** Pieces whose graphics buffers were deleted since the game started. */
    public static int freedCount()
    {
        return freed;
    }

    public static long usedBytes()
    {
        return used;
    }

    public static long budgetBytes()
    {
        return budget;
    }

    public static boolean budgetHit()
    {
        return budgetHit;
    }
}
