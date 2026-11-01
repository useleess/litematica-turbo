package com.litematicaturbo.shared;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.core.TurboConfig;

/**
 * GhostMove on the Yarn-named versions: the ghosts are the detached chunk renderers. While catching up after a
 * move, copying into the schematic world runs in slices sized after the frame time.
 */
public final class GhostRenderers
{
    private static long lastFrameNanos;
    private static double avgFrameNanos = 5_000_000.0;
    /** Start of the current per-frame catch-up slice, or 0 when copying runs in the client tick. */
    private static long frameSliceStart;

    private GhostRenderers()
    {
    }

    static int[] origin(SchematicPlacement placement)
    {
        BlockPos origin = placement.getOrigin();
        return new int[] {origin.getX(), origin.getY(), origin.getZ()};
    }

    static int[] origin(ChunkRendererSchematicVbo renderer)
    {
        BlockPos origin = renderer.getOrigin();
        return new int[] {origin.getX(), origin.getY(), origin.getZ()};
    }

    /** The camera's position {x, y, z}; null without a camera. */
    static double[] cameraPos()
    {
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        return camera == null ? null : new double[] {camera.getX(), camera.getY(), camera.getZ()};
    }

    /** Takes all chunk renderers out of the world renderer; null when there are none. */
    static List<ChunkRendererSchematicVbo> detach()
    {
        return ((TurboWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer()).turbo$detachChunkRenderers();
    }

    static void discard(ChunkRendererSchematicVbo renderer)
    {
        ((TurboRenderer) renderer).turbo$forgetBlockEntities(LitematicaRenderer.getInstance().getWorldRenderer());
        ((TurboRenderer) renderer).turbo$deleteGlResources();
    }

    /** After the ghosts were dropped. */
    static void clear()
    {
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
        CompletePieces.tick();
        long now = System.nanoTime();

        if (lastFrameNanos != 0)
        {
            long frame = Math.min(now - lastFrameNanos, 100_000_000L);
            avgFrameNanos = avgFrameNanos * 0.9 + frame * 0.1;
        }

        lastFrameNanos = now;

        if (GhostMove.isActive() == false || GhostMove.isHoldingWork())
        {
            return;
        }

        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();

        if (TurboConfig.get().catchUpAuto && PlacementQueue.hasQueuedChunks(manager))
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

        if (PlacementQueue.hasQueuedChunks(manager) == false && worldRenderer.turbo$isIdle())
        {
            GhostMove.end();
        }
    }
}
