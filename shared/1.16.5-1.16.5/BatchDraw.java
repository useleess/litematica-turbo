package com.litematicaturbo.shared;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;

import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;

import com.litematicaturbo.core.BatchBook;
import com.litematicaturbo.core.BatchDrawing;

/**
 * Batch drawing for Minecraft 1.16.5: the built pieces of a 4 x 4 x 4 piece region of an opaque block layer are copied
 * (read back from the graphics card once) into one region buffer, with their vertex positions moved to the region's
 * corner, and a frame draws all listed pieces of a region with ONE glMultiDrawArrays call. That call draws exactly the
 * pieces' ranges, so the picture is the same as drawing them one by one. When the rules (BatchBook) do not allow a
 * region yet, the piece is drawn alone the stock way. Any error switches batch drawing off for the session
 * (BatchDrawing) and the stock code takes over. Render thread only.
 */
public final class BatchDraw
{
    private static final int GL_QUADS = 7;

    private static final BatchBook<ChunkRendererSchematicVbo> BOOK = new BatchBook<>(3);
    private static ByteBuffer scratch;
    private static IntBuffer firsts = BufferUtils.createIntBuffer(64);
    private static IntBuffer counts = BufferUtils.createIntBuffer(64);

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
     * Draws the listed pieces of an opaque layer region by region; returns how many pieces were drawn, or null when the
     * caller must draw the layer the stock way (batch drawing off, another layer, a placement being moved, an error).
     */
    public static Integer drawLayer(RenderLayer layer, MatrixStack matrices, Camera camera, List<ChunkRendererSchematicVbo> pieces)
    {
        int index = layerIndex(layer);

        if (index == 0)
        {
            BatchDrawing.countFrame();
        }

        if (BatchDrawing.pausedForTest())
        {
            return null;
        }

        if (BatchDrawing.active() == false || index < 0 || GhostMove.isActive())
        {
            if (BOOK.regionCount() > 0 && BatchDrawing.active() == false)
            {
                free(BOOK.clear());
            }

            return null;
        }

        try
        {
            BatchDrawing.checkFaultForTest();
            return draw(index, layer, matrices, camera.getPos(), pieces);
        }
        catch (RuntimeException | LinkageError e)
        {
            BatchDrawing.switchOff("drawLayer", e);
            VertexBuffer.unbind();
            layer.getVertexFormat().endDrawing();
            layer.endDrawing();
            free(BOOK.clear());
            return null;
        }
    }

    private static int draw(int index, RenderLayer layer, MatrixStack matrices, Vec3d camera, List<ChunkRendererSchematicVbo> pieces)
    {
        if (index == 0)
        {
            free(BOOK.nextFrame());
        }

        Map<Long, List<ChunkRendererSchematicVbo>> groups = new LinkedHashMap<>();

        for (ChunkRendererSchematicVbo piece : pieces)
        {
            if (piece.getChunkRenderData().isBlockLayerEmpty(layer) == false)
            {
                BlockPos origin = piece.getOrigin();
                groups.computeIfAbsent(BatchBook.regionKey(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4), k -> new ArrayList<>(8)).add(piece);
            }
        }

        VertexFormat format = layer.getVertexFormat();
        layer.startDrawing();
        int drawn = 0;

        for (Map.Entry<Long, List<ChunkRendererSchematicVbo>> entry : groups.entrySet())
        {
            drawn += drawGroup(index, layer, format, matrices, camera, entry.getKey(), entry.getValue());
        }

        VertexBuffer.unbind();
        RenderSystem.clearCurrentColor();
        format.endDrawing();
        layer.endDrawing();
        return drawn;
    }

    private static int drawGroup(int index, RenderLayer layer, VertexFormat format, MatrixStack matrices, Vec3d camera, long key, List<ChunkRendererSchematicVbo> group)
    {
        boolean quiet = true;

        for (ChunkRendererSchematicVbo piece : group)
        {
            quiet &= BOOK.see(index, piece, piece.getOrigin().asLong(), version(piece, layer));
        }

        if (group.size() == 1)
        {
            drawSingle(layer, format, matrices, camera, group.get(0));
            return 1;
        }

        BatchBook.Region<ChunkRendererSchematicVbo> region = BOOK.region(index, key);
        List<ChunkRendererSchematicVbo> inRegion = new ArrayList<>(group.size());

        for (ChunkRendererSchematicVbo piece : group)
        {
            if (holds(region, piece, layer))
            {
                inRegion.add(piece);
            }
        }

        if (inRegion.size() < group.size() && quiet && BOOK.mayMake())
        {
            region = make(index, layer, format, key, group, region);
            inRegion.clear();

            if (region != null)
            {
                inRegion.addAll(group);
            }
        }

        if (inRegion.isEmpty() == false)
        {
            drawRegion(format, matrices, camera, region, inRegion);
        }

        for (ChunkRendererSchematicVbo piece : group)
        {
            if (inRegion.contains(piece) == false)
            {
                drawSingle(layer, format, matrices, camera, piece);
            }
        }

        return group.size();
    }

    /** The piece's state: it changes whenever the piece got new built data. */
    private static int version(ChunkRendererSchematicVbo piece, RenderLayer layer)
    {
        return ((BatchPiece) piece).turbo$batchVersion();
    }

    /** How many vertices the piece's layer buffer holds (the buffer is bound by the call). */
    private static int vertices(ChunkRendererSchematicVbo piece, RenderLayer layer, int stride)
    {
        piece.getBlocksVertexBufferByLayer(layer).bind();
        return GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) / stride;
    }

    private static boolean holds(BatchBook.Region<ChunkRendererSchematicVbo> region, ChunkRendererSchematicVbo piece, RenderLayer layer)
    {
        return BOOK.holds(region, piece, piece.getOrigin().asLong(), version(piece, layer));
    }

    /** One piece the stock way: bind its buffer, set the pointers, one draw call. */
    private static void drawSingle(RenderLayer layer, VertexFormat format, MatrixStack matrices, Vec3d camera, ChunkRendererSchematicVbo piece)
    {
        BlockPos origin = piece.getOrigin();
        VertexBuffer buffer = piece.getBlocksVertexBufferByLayer(layer);
        matrices.push();
        matrices.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        buffer.bind();
        format.startDrawing(0L);
        buffer.draw(matrices.peek().getModel(), GL_QUADS);
        matrices.pop();
        BatchDrawing.countDrawCall();
    }

    /** Copies the pieces into one region buffer; null when there is nothing to draw. */
    private static BatchBook.Region<ChunkRendererSchematicVbo> make(int index, RenderLayer layer, VertexFormat format, long key,
                                                                    List<ChunkRendererSchematicVbo> listed, BatchBook.Region<ChunkRendererSchematicVbo> old)
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

                if (all.contains(piece) == false && holds(old, piece, layer)
                        && BatchBook.regionKey(at.getX() >> 4, at.getY() >> 4, at.getZ() >> 4) == key)
                {
                    all.add(piece);
                }
            }
        }

        int stride = format.getVertexSize();
        int[] sizes = new int[all.size()];
        int total = 0;

        for (int i = 0; i < sizes.length; i++)
        {
            sizes[i] = vertices(all.get(i), layer, stride);
            total += sizes[i];
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
            BlockPos at = piece.getOrigin();
            region.add(piece, at.asLong(), version(piece, layer), vertexAt, count);

            if (count > 0)
            {
                int start = vertexAt * stride;
                piece.getBlocksVertexBufferByLayer(layer).bind();
                scratch.clear();
                scratch.position(start).limit(start + count * stride);
                GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, scratch);
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
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, region.buffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, scratch, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        scratch.clear();
        BOOK.made(index, key, region, bytes);
        BatchDrawing.countRegion();
        return region;
    }

    /** All listed pieces of a region with one call. */
    private static void drawRegion(VertexFormat format, MatrixStack matrices, Vec3d camera, BatchBook.Region<ChunkRendererSchematicVbo> region,
                                   List<ChunkRendererSchematicVbo> pieces)
    {
        if (firsts.capacity() < pieces.size())
        {
            firsts = BufferUtils.createIntBuffer(pieces.size() * 2);
            counts = BufferUtils.createIntBuffer(pieces.size() * 2);
        }

        firsts.clear();
        counts.clear();

        for (ChunkRendererSchematicVbo piece : pieces)
        {
            BatchBook.Member member = region.member(piece);

            if (member.count > 0)
            {
                firsts.put(member.first);
                counts.put(member.count);
            }
        }

        firsts.flip();
        counts.flip();
        BOOK.used(region);

        if (counts.limit() == 0)
        {
            return;
        }

        matrices.push();
        matrices.translate(region.originX - camera.x, region.originY - camera.y, region.originZ - camera.z);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, region.buffer);
        format.startDrawing(0L);
        RenderSystem.pushMatrix();
        RenderSystem.loadIdentity();
        RenderSystem.multMatrix(matrices.peek().getModel());
        GL14.glMultiDrawArrays(GL_QUADS, firsts, counts);
        RenderSystem.popMatrix();
        matrices.pop();
        BatchDrawing.countDrawCall();
    }

    /** Renderers reloaded or batch drawing off: every region buffer goes. */
    public static void clear()
    {
        free(BOOK.clear());
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
