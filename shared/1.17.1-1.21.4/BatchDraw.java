package com.litematicaturbo.shared;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
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
 * Batch drawing for Minecraft 1.17.1 - 1.21.4 (the direct OpenGL render era): the regions (BatchRegions) are read back
 * through the copy buffers. Before Litematica draws a layer, the pieces a ready region holds are taken out of its list;
 * while the layer's shader is still set up, one glMultiDrawElementsBaseVertex call draws exactly those pieces' vertices
 * from the region buffer, so the picture is the same as drawing them one by one. Pieces a region does not hold (yet) are
 * drawn by Litematica as before. Any error switches batch drawing off for the session (BatchDrawing) and Litematica's own
 * drawing takes over. Render thread only.
 */
public final class BatchDraw
{
    /** A region holds at most 4 x 4 x 4 pieces. */
    private static final int MAX_PIECES = 64;
    private static final int FIRST_INDEX_QUADS = 4096;

    private static final BatchRegions.Gpu GPU = new BatchRegions.Gpu()
    {
        /** The buffers sizes found, by piece: read uses these, as Litematica may drop a piece's buffer in between. */
        private final Map<ChunkRendererSchematicVbo, VertexBuffer> found = new IdentityHashMap<>();

        @Override
        public boolean has(ChunkRendererSchematicVbo piece, RenderLayer layer)
        {
            return vertexBuffer(piece, layer) != null;
        }

        @Override
        public int stride(RenderLayer layer)
        {
            return BatchCalls.stride(layer.getVertexFormat());
        }

        @Override
        public int[] sizes(List<ChunkRendererSchematicVbo> pieces, RenderLayer layer, int stride)
        {
            int[] sizes = new int[pieces.size()];
            this.found.clear();
            GL30.glBindVertexArray(vertexArray());

            for (int i = 0; i < sizes.length; i++)
            {
                ChunkRendererSchematicVbo piece = pieces.get(i);
                VertexBuffer buffer = BatchDrawing.takeBufferGoneForTest() ? null : vertexBuffer(piece, layer);

                if (buffer == null)
                {
                    sizes[i] = -1;
                    continue;
                }

                this.found.put(piece, buffer);
                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, BatchCalls.id(buffer));
                sizes[i] = GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER, GL15.GL_BUFFER_SIZE) / stride;
            }

            GL30.glBindVertexArray(0);
            VertexBuffer.unbind();
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
            return sizes;
        }

        @Override
        public void read(ChunkRendererSchematicVbo piece, RenderLayer layer, ByteBuffer data)
        {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, BatchCalls.id(this.found.get(piece)));
            GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, data);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        }

        @Override
        public void upload(int buffer, ByteBuffer data)
        {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, buffer);
            GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, data, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        }
    };

    private static final IntBuffer COUNTS = BufferUtils.createIntBuffer(MAX_PIECES);
    private static final IntBuffer BASES = BufferUtils.createIntBuffer(MAX_PIECES);
    /** Every piece's indices start at the beginning of the index buffer: all pointers stay zero. */
    private static final PointerBuffer STARTS = BufferUtils.createPointerBuffer(MAX_PIECES);
    private static int vertexArray;
    private static int indexBuffer;
    private static int indexQuads;
    private static Plan plan;

    /** The regions to draw in the layer that is being drawn, and the list to give back afterwards. */
    private static final class Plan
    {
        final RenderLayer layer;
        final Vec3d camera;
        final List<BatchRegions.Pick> draws = new ArrayList<>();
        List<ChunkRendererSchematicVbo> saved;
        int pieces;
        int quads;

        Plan(RenderLayer layer, Vec3d camera)
        {
            this.layer = layer;
            this.camera = camera;
        }
    }

    private BatchDraw()
    {
    }

    /**
     * Before Litematica draws a block layer: makes the regions that are due and takes the pieces drawn from a ready
     * region out of the list (end gives them back). On any error the list stays as it is and Litematica draws it all.
     */
    public static void begin(RenderLayer layer, Vec3d camera, List<ChunkRendererSchematicVbo> infos)
    {
        plan = null;
        int index = BatchRegions.start(layer, BatchDraw::clear);

        if (index < 0)
        {
            return;
        }

        try
        {
            BatchDrawing.checkFaultForTest();
            Plan next = plan(index, layer, camera, infos);

            if (next.draws.isEmpty() == false)
            {
                Set<ChunkRendererSchematicVbo> covered = Collections.newSetFromMap(new IdentityHashMap<>());

                for (BatchRegions.Pick draw : next.draws)
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
        Plan next = new Plan(layer, camera);

        for (Map.Entry<Long, List<ChunkRendererSchematicVbo>> entry : BatchRegions.groups(index, layer, infos, GPU).entrySet())
        {
            BatchRegions.Pick pick = BatchRegions.pick(index, layer, entry.getKey(), entry.getValue(), GPU);

            if (pick.pieces.isEmpty() == false)
            {
                next.draws.add(pick);
                next.pieces += pick.pieces.size();

                for (ChunkRendererSchematicVbo piece : pick.pieces)
                {
                    next.quads = Math.max(next.quads, pick.region.member(piece).count / 4);
                }
            }
        }

        return next;
    }

    /** The vertex buffer Litematica would draw for the piece in the layer; null when it would draw nothing or the buffer is closed. */
    private static VertexBuffer vertexBuffer(ChunkRendererSchematicVbo piece, RenderLayer layer)
    {
        VertexBuffer buffer = ((BatchPiece) piece).turbo$buffer(layer);
        return buffer != null && BatchCalls.open(buffer) ? buffer : null;
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

            for (BatchRegions.Pick draw : current.draws)
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
    private static void drawRegion(VertexFormat format, BatchPlacement placement, Vec3d camera, BatchRegions.Pick draw)
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
        BatchRegions.used(region);

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
        BatchRegions.clear();

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
        return BatchRegions.regionCount();
    }
}
