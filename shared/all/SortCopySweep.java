package com.litematicaturbo.shared;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.SortCopies;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;

/**
 * Less memory for see-through sorting, game side (rules in {@link SortCopies}): at the start of Litematica's chunk
 * updates on the render thread, far pieces lose their re-sort copies and a piece whose copies were dropped is built
 * again once it comes near. A piece a build thread is working on is left for the next sweep. How the copies are held
 * differs by Litematica version ({@code SortCopyData}). An error switches it off and every dropped piece is built again.
 */
public final class SortCopySweep
{
    /** Pieces whose copies were dropped, with the build result they were dropped from. */
    private static final Map<ChunkRendererSchematicVbo, WeakReference<Object>> DROPPED = new WeakHashMap<>();

    private SortCopySweep()
    {
    }

    /** Whether Litematica sorts see-through blocks again here: not before 1.19 with Turbo's build threads. */
    public static boolean resorting(TurboWorldRenderer worldRenderer)
    {
        return (worldRenderer instanceof ResortOffWithThreads && TurboConfig.get().threadingEnabled()) == false;
    }

    /** The re-sort copies a piece holds now (for the tests). */
    public static List<Object> copies(ChunkRendererSchematicVbo piece)
    {
        return SortCopyData.copies(SortCopyData.data(piece));
    }

    public static void tick(TurboWorldRenderer worldRenderer)
    {
        if (SortCopies.active() == false)
        {
            giveBack();
            return;
        }

        double[] camera = GhostRenderers.cameraPos();
        long builds = BuildStats.totalBuilds();
        long now = System.nanoTime();

        if (camera == null || SortCopies.sweepDue(camera[0], camera[1], camera[2], builds, now) == false)
        {
            return;
        }

        try
        {
            SortCopies.checkFaultForTest();
            sweep(worldRenderer.turbo$chunkRenderers(), camera, resorting(worldRenderer));
            SortCopies.swept(camera[0], camera[1], camera[2], builds, now);
        }
        catch (RuntimeException | LinkageError e)
        {
            SortCopies.switchOff("the sweep", e);
            giveBack();
        }
    }

    private static void sweep(Iterable<ChunkRendererSchematicVbo> pieces, double[] camera, boolean resorting)
    {
        for (ChunkRendererSchematicVbo piece : pieces)
        {
            if (piece == null)
            {
                continue;
            }

            double dx = piece.getOrigin().getX() + 8 - camera[0];
            double dy = piece.getOrigin().getY() + 8 - camera[1];
            double dz = piece.getOrigin().getZ() + 8 - camera[2];
            double distanceSq = dx * dx + dy * dy + dz * dz;

            if (SortCopies.drops(distanceSq, resorting))
            {
                drop(piece);
            }
            else if (SortCopies.rebuilds(distanceSq, resorting))
            {
                rebuildIfDropped(piece);
            }
        }
    }

    private static void drop(ChunkRendererSchematicVbo piece)
    {
        ReentrantLock lock = ((TurboRenderer) piece).turbo$buildLock();

        if (lock.tryLock() == false)
        {
            SortCopies.sweepSoon();
            return;
        }

        try
        {
            Object data = SortCopyData.data(piece);

            if (SortCopyData.drop(data))
            {
                DROPPED.put(piece, new WeakReference<>(data));
                SortCopies.countDropped();
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    private static void rebuildIfDropped(ChunkRendererSchematicVbo piece)
    {
        WeakReference<Object> droppedFrom = DROPPED.remove(piece);

        // built again since (or sorted again: 1.21+ makes a missing copy then), nothing to do
        if (droppedFrom != null && droppedFrom.get() == SortCopyData.data(piece) && SortCopyData.copies(droppedFrom.get()).isEmpty())
        {
            ((TurboRenderer) piece).turbo$setNeedsUpdate(false);
            SortCopies.countRebuilt();
        }
    }

    /** Switched off (setting, error): every piece still without the copies it was built with is built again. */
    private static void giveBack()
    {
        if (DROPPED.isEmpty())
        {
            return;
        }

        try
        {
            for (ChunkRendererSchematicVbo piece : new ArrayList<>(DROPPED.keySet()))
            {
                WeakReference<Object> droppedFrom = DROPPED.remove(piece);

                if (droppedFrom != null && droppedFrom.get() == SortCopyData.data(piece))
                {
                    ((TurboRenderer) piece).turbo$setNeedsUpdate(false);
                }
            }
        }
        catch (RuntimeException | LinkageError e)
        {
            DROPPED.clear();
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            TurboLog.error("Less memory for see-through sorting could not ask for dropped pieces to be built again; they get their copies at their next build:\n" + trace);
        }
    }
}
