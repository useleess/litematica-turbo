package com.litematicaturbo.shared;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BatchBook;
import com.litematicaturbo.core.BatchDrawing;

/**
 * The game-side half of batch drawing that every render era up to 1.21.4 shares: which layers take part, when batch
 * drawing runs, the listed pieces grouped by 4 x 4 x 4 piece region, and making a region (each piece's vertex data read
 * back once, its positions moved to the region's corner, all of it uploaded as one region buffer). The rules live in
 * BatchBook; each era's BatchDraw passes in how its pieces' data is read back (Gpu) and draws the regions itself.
 * Render thread only.
 */
final class BatchRegions
{
    /** How an era finds a piece's built data on the graphics card and uploads a region. */
    interface Gpu
    {
        /** Whether Litematica would draw something of the piece in the layer. */
        boolean has(ChunkRendererSchematicVbo piece, RenderLayer layer);

        /** The bytes per vertex of the layer. */
        int stride(RenderLayer layer);

        /**
         * How many vertices each piece's layer buffer holds; -1 for a piece whose buffer is gone by now (Litematica
         * dropped or closed it after the piece was listed). Remembers the buffers found for read.
         */
        int[] sizes(List<ChunkRendererSchematicVbo> pieces, RenderLayer layer, int stride);

        /** Reads the whole layer buffer sizes found for the piece into the data's position .. limit. */
        void read(ChunkRendererSchematicVbo piece, RenderLayer layer, ByteBuffer data);

        /** Puts the data into the region's GL buffer. */
        void upload(int buffer, ByteBuffer data);
    }

    /** The pieces of a listed group drawn from a region; empty when the region holds none of them (yet). */
    static final class Pick
    {
        final BatchBook.Region<ChunkRendererSchematicVbo> region;
        final List<ChunkRendererSchematicVbo> pieces;

        Pick(BatchBook.Region<ChunkRendererSchematicVbo> region, List<ChunkRendererSchematicVbo> pieces)
        {
            this.region = region;
            this.pieces = pieces;
        }
    }

    private static final BatchBook<ChunkRendererSchematicVbo> BOOK = new BatchBook<>(3);
    private static ByteBuffer scratch;

    private BatchRegions()
    {
    }

    /** The index of an opaque block layer; -1 for any layer that is drawn the stock way (see-through ones). */
    private static int layerIndex(RenderLayer layer)
    {
        if (layer == RenderLayer.getSolid())
        {
            return 0;
        }

        if (layer == RenderLayer.getCutoutMipped())
        {
            return 1;
        }

        return layer == RenderLayer.getCutout() ? 2 : -1;
    }

    /**
     * Before a block layer is drawn: the layer's index when batch drawing takes it, otherwise -1 (another layer, batch
     * drawing off or paused by a test, a placement being moved). Once batch drawing is off, clear frees what is left.
     */
    static int start(RenderLayer layer, Runnable clear)
    {
        int index = layerIndex(layer);

        if (index == 0)
        {
            BatchDrawing.countFrame();
            BatchDrawing.pauseFor(ShaderPacks.drawing());
        }

        if (BatchDrawing.pausedForTest())
        {
            return -1;
        }

        if (BatchDrawing.active() == false || index < 0 || GhostMove.isActive())
        {
            if (BOOK.regionCount() > 0 && BatchDrawing.active() == false)
            {
                clear.run();
            }

            return -1;
        }

        return index;
    }

    /** The listed pieces that have something to draw in the layer, by region; the first layer of a frame also frees the regions not used for a while. */
    static Map<Long, List<ChunkRendererSchematicVbo>> groups(int index, RenderLayer layer, List<ChunkRendererSchematicVbo> pieces, Gpu gpu)
    {
        if (index == 0)
        {
            free(BOOK.nextFrame());
        }

        Map<Long, List<ChunkRendererSchematicVbo>> groups = new LinkedHashMap<>();

        for (ChunkRendererSchematicVbo piece : pieces)
        {
            if (gpu.has(piece, layer))
            {
                BlockPos origin = piece.getOrigin();
                groups.computeIfAbsent(BatchBook.regionKey(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4), k -> new ArrayList<>(8)).add(piece);
            }
        }

        return groups;
    }

    /** Tells the book the group's pieces are listed; false while one of them changed lately. */
    static boolean see(int index, List<ChunkRendererSchematicVbo> group)
    {
        boolean quiet = true;

        for (ChunkRendererSchematicVbo piece : group)
        {
            quiet &= BOOK.see(index, piece, piece.getOrigin().asLong(), version(piece));
        }

        return quiet;
    }

    /** The region a listed group is drawn from: the one held, or made / made again when the rules allow it. */
    static Pick pick(int index, RenderLayer layer, long key, List<ChunkRendererSchematicVbo> group, Gpu gpu)
    {
        boolean quiet = see(index, group);
        BatchBook.Region<ChunkRendererSchematicVbo> region = BOOK.region(index, key);
        List<ChunkRendererSchematicVbo> held = held(region, group);

        if (group.size() > 1 && held.size() < group.size() && quiet && BOOK.mayMake())
        {
            region = make(index, layer, key, group, region, gpu);
            held = held(region, group);
        }

        return new Pick(region, held);
    }

    /** The pieces of the group the region holds as they are now; Litematica draws the others. */
    private static List<ChunkRendererSchematicVbo> held(BatchBook.Region<ChunkRendererSchematicVbo> region, List<ChunkRendererSchematicVbo> group)
    {
        List<ChunkRendererSchematicVbo> held = new ArrayList<>(group.size());

        for (ChunkRendererSchematicVbo piece : group)
        {
            if (holds(region, piece))
            {
                held.add(piece);
            }
        }

        return held;
    }

    /** The region is drawn this frame. */
    static void used(BatchBook.Region<ChunkRendererSchematicVbo> region)
    {
        BOOK.used(region);
    }

    /** The piece's state: it changes whenever the piece got new built data. */
    private static int version(ChunkRendererSchematicVbo piece)
    {
        return ((BatchPiece) piece).turbo$batchVersion();
    }

    private static boolean holds(BatchBook.Region<ChunkRendererSchematicVbo> region, ChunkRendererSchematicVbo piece)
    {
        return BOOK.holds(region, piece, piece.getOrigin().asLong(), version(piece));
    }

    /** Copies the pieces into one region buffer; null when there is nothing to draw. */
    private static BatchBook.Region<ChunkRendererSchematicVbo> make(int index, RenderLayer layer, long key, List<ChunkRendererSchematicVbo> listed,
                                                                    BatchBook.Region<ChunkRendererSchematicVbo> old, Gpu gpu)
    {
        // a region keeps the pieces it already holds, so turning the camera (which lists other pieces of the
        // same region) does not make it again
        List<ChunkRendererSchematicVbo> all = new ArrayList<>(listed);

        if (old != null)
        {
            for (Map.Entry<ChunkRendererSchematicVbo, BatchBook.Member> e : old.members())
            {
                ChunkRendererSchematicVbo piece = e.getKey();
                BlockPos at = piece.getOrigin();

                if (all.contains(piece) == false && holds(old, piece) && gpu.has(piece, layer)
                        && BatchBook.regionKey(at.getX() >> 4, at.getY() >> 4, at.getZ() >> 4) == key)
                {
                    all.add(piece);
                }
            }
        }

        int stride = gpu.stride(layer);
        int[] sizes = gpu.sizes(all, layer, stride);
        int total = 0;

        for (int size : sizes)
        {
            total += Math.max(size, 0);
        }

        if (total == 0)
        {
            return null;
        }

        int bytes = total * stride;

        if (scratch == null || scratch.capacity() < bytes)
        {
            scratch = BufferUtils.createByteBuffer(Math.max(bytes, 1 << 20)).order(ByteOrder.nativeOrder());
        }

        BatchBook.Region<ChunkRendererSchematicVbo> region = old != null ? old : new BatchBook.Region<>();
        BlockPos first = all.get(0).getOrigin();
        region.clearMembers();
        region.originX = BatchBook.regionOrigin(first.getX() >> 4);
        region.originY = BatchBook.regionOrigin(first.getY() >> 4);
        region.originZ = BatchBook.regionOrigin(first.getZ() >> 4);
        int vertexAt = 0;

        for (int i = 0; i < sizes.length; i++)
        {
            ChunkRendererSchematicVbo piece = all.get(i);
            int count = sizes[i];

            if (count < 0)
            {
                continue; // its buffer is gone: not held, so Litematica draws it and a later region takes it in
            }

            BlockPos at = piece.getOrigin();
            region.add(piece, at.asLong(), version(piece), vertexAt, count);

            if (count > 0)
            {
                int start = vertexAt * stride;
                scratch.clear();
                scratch.position(start).limit(start + count * stride);
                gpu.read(piece, layer, scratch);
                scratch.clear();
                BatchBook.shiftPositions(scratch, start, count, stride, at.getX() - region.originX, at.getY() - region.originY, at.getZ() - region.originZ);
                vertexAt += count;
            }
        }

        if (region.buffer == 0)
        {
            region.buffer = GL15.glGenBuffers();
        }

        scratch.position(0).limit(bytes);
        gpu.upload(region.buffer, scratch);
        scratch.clear();
        BOOK.made(index, key, region, bytes);
        BatchDrawing.countRegion();
        return region;
    }

    /** Renderers reloaded or batch drawing off: every region buffer goes. */
    static void clear()
    {
        free(BOOK.clear());
    }

    /** How many region buffers exist (tests). */
    static int regionCount()
    {
        return BOOK.regionCount();
    }

    private static void free(List<BatchBook.Region<ChunkRendererSchematicVbo>> regions)
    {
        for (BatchBook.Region<ChunkRendererSchematicVbo> region : regions)
        {
            if (region.buffer != 0)
            {
                GL15.glDeleteBuffers(region.buffer);
                region.buffer = 0;
            }
        }
    }
}
