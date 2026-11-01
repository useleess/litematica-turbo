package com.litematicaturbo.shared;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BatchBook;
import com.litematicaturbo.core.BatchDrawing;

/**
 * Batch drawing for Minecraft 1.17.1 - 1.21.4 (the direct OpenGL render era): the built pieces of a 4 x 4 x 4 piece region
 * of an opaque block layer are copied (read back from the graphics card once) into one region buffer, with their vertex
 * positions moved to the region's corner. Before Litematica draws a layer, the pieces a ready region holds are taken out of
 * its list; while the layer's shader is still set up, one glMultiDrawElementsBaseVertex call draws exactly those pieces'
 * vertices from the region buffer, so the picture is the same as drawing them one by one. Pieces a region does not hold
 * (yet) are drawn by Litematica as before. Any error switches batch drawing off for the session (BatchDrawing) and
 * Litematica's own drawing takes over. Render thread only.
 */
public final class BatchDraw
{
    /** A region holds at most 4 x 4 x 4 pieces. */
    private static final int MAX_PIECES = 64;
    private static final int FIRST_INDEX_QUADS = 4096;

    private static final BatchBook<ChunkRendererSchematicVbo> BOOK = new BatchBook<>(3);
    private static final IntBuffer COUNTS = BufferUtils.createIntBuffer(MAX_PIECES);
    private static final IntBuffer BASES = BufferUtils.createIntBuffer(MAX_PIECES);
    /** Every piece's indices start at the beginning of the index buffer: all pointers stay zero. */
    private static final PointerBuffer STARTS = BufferUtils.createPointerBuffer(MAX_PIECES);
    private static ByteBuffer scratch;
    private static int vertexArray;
    private static int indexBuffer;
    private static int indexQuads;
    private static Plan plan;

    /** The regions to draw in the layer that is being drawn, and the list to give back afterwards. */
    private static final class Plan
    {
        final RenderLayer layer;
        final Vec3d camera;
        final List<Draw> draws = new ArrayList<>();
        List<ChunkRendererSchematicVbo> saved;
        int pieces;
        int quads;

        Plan(RenderLayer layer, Vec3d camera)
        {
            this.layer = layer;
            this.camera = camera;
        }
    }

    private static final class Draw
    {
        final BatchBook.Region<ChunkRendererSchematicVbo> region;
        final List<ChunkRendererSchematicVbo> pieces;

        Draw(BatchBook.Region<ChunkRendererSchematicVbo> region, List<ChunkRendererSchematicVbo> pieces)
        {
            this.region = region;
            this.pieces = pieces;
        }
    }

    private BatchDraw()
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
     * Before Litematica draws a block layer: makes the regions that are due and takes the pieces drawn from a ready
     * region out of the list (end gives them back). On any error the list stays as it is and Litematica draws it all.
     */
    public static void begin(RenderLayer layer, Vec3d camera, List<ChunkRendererSchematicVbo> infos)
    {
        plan = null;
        int index = layerIndex(layer);

        if (index == 0)
        {
            BatchDrawing.countFrame();
        }

        if (BatchDrawing.pausedForTest())
        {
            return;
        }

        if (BatchDrawing.active() == false || index < 0 || GhostMove.isActive())
        {
            if (BOOK.regionCount() > 0 && BatchDrawing.active() == false)
            {
                clear();
            }

            return;
        }

        try
        {
            BatchDrawing.checkFaultForTest();

            if (index == 0)
            {
                free(BOOK.nextFrame());
            }

            Plan next = plan(index, layer, camera, infos);

            if (next.draws.isEmpty() == false)
            {
                Set<ChunkRendererSchematicVbo> covered = Collections.newSetFromMap(new IdentityHashMap<>());

                for (Draw draw : next.draws)
                {
                    covered.addAll(draw.pieces);
                }

                next.saved = new ArrayList<>(infos);
                infos.removeIf(covered::contains);
                plan = next;
            }
        }
        catch (RuntimeException | LinkageError e)
        {
            fail("begin", e);
        }
    }

    private static Plan plan(int index, RenderLayer layer, Vec3d camera, List<ChunkRendererSchematicVbo> infos)
    {
        Map<Long, List<ChunkRendererSchematicVbo>> groups = new LinkedHashMap<>();

        for (ChunkRendererSchematicVbo piece : infos)
        {
            if (vertexBuffer(piece, layer) != null)
            {
                BlockPos origin = piece.getOrigin();
                groups.computeIfAbsent(BatchBook.regionKey(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4), k -> new ArrayList<>(8)).add(piece);
            }
        }

        Plan next = new Plan(layer, camera);

        for (Map.Entry<Long, List<ChunkRendererSchematicVbo>> entry : groups.entrySet())
        {
            List<ChunkRendererSchematicVbo> group = entry.getValue();
            boolean quiet = true;

            for (ChunkRendererSchematicVbo piece : group)
            {
                quiet &= BOOK.see(index, piece, piece.getOrigin().asLong(), version(piece));
            }

            BatchBook.Region<ChunkRendererSchematicVbo> region = BOOK.region(index, entry.getKey());
            List<ChunkRendererSchematicVbo> inRegion = held(region, group);

            if (group.size() > 1 && inRegion.size() < group.size() && quiet && BOOK.mayMake())
            {
                region = make(index, layer, entry.getKey(), group, region);
                inRegion = region != null ? group : new ArrayList<>();
            }

            if (inRegion.isEmpty() == false)
            {
                next.draws.add(new Draw(region, inRegion));
                next.pieces += inRegion.size();

                for (ChunkRendererSchematicVbo piece : inRegion)
                {
                    next.quads = Math.max(next.quads, region.member(piece).count / 4);
                }
            }
        }

        return next;
    }

    /** The pieces of the group that the region holds exactly as they are now. */
    private static List<ChunkRendererSchematicVbo> held(BatchBook.Region<ChunkRendererSchematicVbo> region, List<ChunkRendererSchematicVbo> group)
    {
        List<ChunkRendererSchematicVbo> held = new ArrayList<>(group.size());

        for (ChunkRendererSchematicVbo piece : group)
        {
            if (BOOK.holds(region, piece, piece.getOrigin().asLong(), version(piece)))
            {
                held.add(piece);
            }
        }

        return held;
    }

    /** The piece's state: it changes whenever the piece got new built data. */
    private static int version(ChunkRendererSchematicVbo piece)
    {
        return ((BatchPiece) piece).turbo$batchVersion();
    }

    /** The vertex buffer Litematica would draw for the piece in the layer; null when it would draw nothing or the buffer is closed. */
    private static VertexBuffer vertexBuffer(ChunkRendererSchematicVbo piece, RenderLayer layer)
    {
        VertexBuffer buffer = ((BatchPiece) piece).turbo$buffer(layer);
        return buffer != null && BatchCalls.open(buffer) ? buffer : null;
    }

    /** Copies the pieces into one region buffer; null when there is nothing to draw. */
    private static BatchBook.Region<ChunkRendererSchematicVbo> make(int index, RenderLayer layer, long key, List<ChunkRendererSchematicVbo> listed,
                                                                    BatchBook.Region<ChunkRendererSchematicVbo> old)
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

                if (all.contains(piece) == false && BOOK.holds(old, piece, at.asLong(), version(piece)) && vertexBuffer(piece, layer) != null
                        && BatchBook.regionKey(at.getX() >> 4, at.getY() >> 4, at.getZ() >> 4) == key)
                {
                    all.add(piece);
                }
            }
        }

        int stride = BatchCalls.stride(layer.getVertexFormat());
        int[] ids = new int[all.size()];
        int[] sizes = new int[ids.length];
        int total = 0;
        GL30.glBindVertexArray(vertexArray());

        for (int i = 0; i < sizes.length; i++)
        {
            ids[i] = BatchCalls.id(vertexBuffer(all.get(i), layer));
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, ids[i]);
            sizes[i] = GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER, GL15.GL_BUFFER_SIZE) / stride;
            total += sizes[i];
        }

        GL30.glBindVertexArray(0);
        VertexBuffer.unbind();

        if (total == 0)
        {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
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
            BlockPos at = piece.getOrigin();
            region.add(piece, at.asLong(), version(piece), vertexAt, count);

            if (count > 0)
            {
                int start = vertexAt * stride;
                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, ids[i]);
                scratch.clear();
                scratch.position(start).limit(start + count * stride);
                GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, scratch);
                scratch.clear();
                BatchBook.shiftPositions(scratch, start, count, stride, at.getX() - region.originX, at.getY() - region.originY, at.getZ() - region.originZ);
                vertexAt += count;
            }
        }

        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);

        if (region.buffer == 0)
        {
            region.buffer = GL15.glGenBuffers();
        }

        scratch.position(0).limit(bytes);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, region.buffer);
        GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, scratch, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        scratch.clear();
        BOOK.made(index, key, region, bytes);
        BatchDrawing.countRegion();
        return region;
    }

    /**
     * While the layer's shader is still set up: draws each ready region's pieces with one call. An error here switches
     * batch drawing off; the pieces of this one layer are then missing for a single frame.
     */
    public static void drawRegions(BatchPlacement placement)
    {
        Plan current = plan;

        if (current == null)
        {
            return;
        }

        try
        {
            VertexFormat format = current.layer.getVertexFormat();
            prepareArrays(current.quads);

            for (Draw draw : current.draws)
            {
                drawRegion(format, placement, current.camera, draw);
            }
        }
        catch (RuntimeException | LinkageError e)
        {
            fail("drawRegions", e);
        }
        finally
        {
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            VertexBuffer.unbind();
        }
    }

    /** Our own vertex array: it holds the index buffer (the indices of the quads of the biggest piece) every region is drawn with. */
    private static int vertexArray()
    {
        if (vertexArray == 0)
        {
            vertexArray = GL30.glGenVertexArrays();
            indexBuffer = GL15.glGenBuffers();
        }

        return vertexArray;
    }

    private static void prepareArrays(int quads)
    {
        GL30.glBindVertexArray(vertexArray());
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);

        if (quads > indexQuads)
        {
            indexQuads = Math.max(quads, Math.max(indexQuads * 2, FIRST_INDEX_QUADS));
            IntBuffer indices = BufferUtils.createIntBuffer(indexQuads * 6);

            for (int quad = 0; quad < indexQuads; quad++)
            {
                int vertex = quad * 4;
                indices.put(vertex).put(vertex + 1).put(vertex + 2).put(vertex + 2).put(vertex + 3).put(vertex);
            }

            indices.flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
        }
    }

    /** The listed pieces of a region with one call. */
    private static void drawRegion(VertexFormat format, BatchPlacement placement, Vec3d camera, Draw draw)
    {
        BatchBook.Region<ChunkRendererSchematicVbo> region = draw.region;
        COUNTS.clear();
        BASES.clear();

        for (ChunkRendererSchematicVbo piece : draw.pieces)
        {
            BatchBook.Member member = region.member(piece);

            if (member.count >= 4)
            {
                COUNTS.put(member.count / 4 * 6);
                BASES.put(member.first);
            }
        }

        COUNTS.flip();
        BASES.flip();
        STARTS.clear().limit(COUNTS.limit());
        BOOK.used(region);

        if (COUNTS.limit() == 0)
        {
            return;
        }

        placement.draw((float) (region.originX - camera.x), (float) (region.originY - camera.y), (float) (region.originZ - camera.z), () -> {
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, region.buffer);
            BatchCalls.setup(format);
            GL32.glMultiDrawElementsBaseVertex(GL11.GL_TRIANGLES, COUNTS, GL11.GL_UNSIGNED_INT, STARTS, BASES);
        });
        BatchDrawing.countDrawCall();
    }

    /** After Litematica drew the layer: the pieces taken out of its list come back; returns the count with those drawn from regions. */
    public static int end(List<ChunkRendererSchematicVbo> infos, int drawn)
    {
        Plan current = plan;
        plan = null;

        if (current == null)
        {
            return drawn;
        }

        infos.clear();
        infos.addAll(current.saved);
        return drawn + current.pieces;
    }

    /** Batch drawing failed: it is off for the session, every region buffer goes and Litematica draws everything. */
    private static void fail(String call, Throwable t)
    {
        BatchDrawing.switchOff(call, t);
        clear();
    }

    /** Renderers reloaded or batch drawing off: every region buffer goes. */
    public static void clear()
    {
        free(BOOK.clear());

        if (vertexArray != 0)
        {
            GL30.glDeleteVertexArrays(vertexArray);
            GL15.glDeleteBuffers(indexBuffer);
            vertexArray = 0;
            indexBuffer = 0;
            indexQuads = 0;
        }
    }

    /** How many region buffers exist (tests). */
    public static int regionCount()
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
