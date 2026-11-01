package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.litematicaturbo.core.CompleteCopy;
import com.litematicaturbo.shared.mixin.ChunkRenderGpuUploaderAccessor;
import com.litematicaturbo.shared.mixin.ChunkRendererSchematicVboInvoker;

/**
 * GhostMove on the Mojang-named versions: the ghosts are the detached chunk renderers together with the GPU
 * uploaders that hold their buffers. Litematica 0.26 copies on its own background threads and Turbo builds
 * on its build threads, so the new state is prepared out of sight.
 *
 * Litematica 0.26 changes the schematic world as soon as a placement moves: the chunks it left are unloaded during the
 * move and its background threads clear and copy the others. The complete copy (CompletePieces) reads the old place, so
 * while it is being built both wait (at most as long as GhostMove holds the work): the threads take no task and the
 * unloads are done afterwards.
 */
public final class GhostRenderers
{
    /** Each ghost's uploader (null when it had none). */
    private static final Map<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> uploaders = new IdentityHashMap<>();
    /** Original chunk (packed) -> the uploader holding that ghost's buffers. */
    private static final Map<Long, ChunkRenderGpuUploader> chunkUploaders = new HashMap<>();
    /** True while the block layers / overlay are drawn from the ghosts. */
    private static boolean drawingGhosts;
    /** True while the complete copy reads the old place (read by Litematica's background threads). */
    private static volatile boolean copyHeld;

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
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return camera == null ? null : new double[] {camera.getX(), camera.getY(), camera.getZ()};
    }

    /** Takes all chunk renderers (and their uploaders) out of the world renderer; null when there are none. */
    static List<ChunkRendererSchematicVbo> detach()
    {
        Map<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> detached =
                ((TurboWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer()).turbo$detachChunkRenderers();

        if (detached == null)
        {
            return null;
        }

        for (Map.Entry<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> entry : detached.entrySet())
        {
            uploaders.put(entry.getKey(), entry.getValue());

            if (entry.getValue() != null)
            {
                ChunkPos chunk = ((ChunkRendererSchematicVboInvoker) entry.getKey()).turbo$getChunkPos();
                chunkUploaders.put(ChunkKeys.of(chunk), entry.getValue());
            }
        }

        return new ArrayList<>(detached.keySet());
    }

    static void discard(ChunkRendererSchematicVbo renderer)
    {
        ChunkRenderGpuUploader uploader = uploaders.get(renderer);

        if (uploader != null)
        {
            ((ChunkRenderGpuUploaderAccessor) uploader).turbo$clear();
        }

        ((TurboRenderer) renderer).turbo$deleteGlResources();
    }

    /** After the ghosts were dropped. */
    static void clear()
    {
        uploaders.clear();
        chunkUploaders.clear();
        drawingGhosts = false;
    }

    public static void setDrawingGhosts(boolean drawing)
    {
        drawingGhosts = drawing;
    }

    /** While ghosts are drawn: the uploader holding the ghost buffers of that (original) chunk. */
    public static ChunkRenderGpuUploader ghostUploader(int chunkX, int chunkZ)
    {
        return drawingGhosts ? chunkUploaders.get(ChunkKeys.of(chunkX, chunkZ)) : null;
    }

    /** Called while a placement moves, instead of unloading the chunks it left: true when they are to stay until the copy is built. */
    public static boolean holdForCopy()
    {
        if (GhostMove.isInSetOrigin() && CompleteCopy.active())
        {
            copyHeld = true;
        }

        return copyHeld;
    }

    /** True while Litematica's background threads and unloads wait for the complete copy. */
    public static boolean isCopyHeld()
    {
        return copyHeld;
    }

    /** Every frame: the complete copy's step; switch to the real chunks once the placement is still and they are all built. */
    public static void tick(TurboWorldRenderer worldRenderer)
    {
        CompletePieces.tick();
        boolean holding = GhostMove.isHoldingWork();
        copyHeld = holding && CompletePieces.isBuilding();

        if (GhostMove.isActive() == false || holding)
        {
            return;
        }

        if (PlacementManagerDaemonHandler.INSTANCE.hasAnyTasks() == false && worldRenderer.turbo$isIdle())
        {
            GhostMove.end();
        }
    }
}
