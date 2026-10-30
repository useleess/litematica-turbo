package com.litematicaturbo.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.litematicaturbo.shared.mixin.ChunkRenderGpuUploaderAccessor;
import com.litematicaturbo.shared.mixin.ChunkRendererSchematicVboInvoker;

/**
 * GhostMove on the Mojang-named versions: the ghosts are the detached chunk renderers together with the GPU
 * uploaders that hold their buffers. Litematica 0.26 copies on its own background threads and Turbo builds
 * on its build threads, so the new state is prepared out of sight.
 */
public final class GhostRenderers
{
    /** Each ghost's uploader (null when it had none). */
    private static final Map<ChunkRendererSchematicVbo, ChunkRenderGpuUploader> uploaders = new IdentityHashMap<>();
    /** Original chunk (packed) -> the uploader holding that ghost's buffers. */
    private static final Map<Long, ChunkRenderGpuUploader> chunkUploaders = new HashMap<>();
    /** True while the block layers / overlay are drawn from the ghosts. */
    private static boolean drawingGhosts;

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

    /** Every frame: switch to the real chunks once the placement is still and they are all built. */
    public static void tick(TurboWorldRenderer worldRenderer)
    {
        if (GhostMove.isActive() == false || GhostMove.isHoldingWork())
        {
            return;
        }

        if (PlacementManagerDaemonHandler.INSTANCE.hasAnyTasks() == false && worldRenderer.turbo$isIdle())
        {
            GhostMove.end();
        }
    }
}
