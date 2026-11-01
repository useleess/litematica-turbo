package com.litematicaturbo.shared;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
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
 * Batch drawing for Minecraft 1.16.5: the regions (BatchRegions) are read back and uploaded through the array buffer,
 * and a frame draws all listed pieces of a region with ONE glMultiDrawArrays call. That call draws exactly the pieces'
 * ranges, so the picture is the same as drawing them one by one. When the rules (BatchBook) do not allow a region yet,
 * the piece is drawn alone the stock way. Any error switches batch drawing off for the session (BatchDrawing) and the
 * stock code takes over. Render thread only.
 */
public final class BatchDraw
{
    private static final int GL_QUADS = 7;

    private static final BatchRegions.Gpu GPU = new BatchRegions.Gpu()
    {
        @Override
        public boolean has(ChunkRendererSchematicVbo piece, RenderLayer layer)
        {
            return piece.getChunkRenderData().isBlockLayerEmpty(layer) == false;
        }

        @Override
        public int stride(RenderLayer layer)
        {
            return layer.getVertexFormat().getVertexSize();
        }

        @Override
        public int[] sizes(List<ChunkRendererSchematicVbo> pieces, RenderLayer layer, int stride)
        {
            int[] sizes = new int[pieces.size()];

            for (int i = 0; i < sizes.length; i++)
            {
                pieces.get(i).getBlocksVertexBufferByLayer(layer).bind();
                sizes[i] = GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) / stride;
            }

            return sizes;
        }

        @Override
        public void read(ChunkRendererSchematicVbo piece, RenderLayer layer, ByteBuffer data)
        {
            piece.getBlocksVertexBufferByLayer(layer).bind();
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
        }

        @Override
        public void upload(int buffer, ByteBuffer data)
        {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    };

    private static IntBuffer firsts = BufferUtils.createIntBuffer(64);
    private static IntBuffer counts = BufferUtils.createIntBuffer(64);

    private BatchDraw()
    {
    }

    /**
     * Draws the listed pieces of an opaque layer region by region; returns how many pieces were drawn, or null when the
     * caller must draw the layer the stock way (batch drawing off, another layer, a placement being moved, an error).
     */
    public static Integer drawLayer(RenderLayer layer, MatrixStack matrices, Camera camera, List<ChunkRendererSchematicVbo> pieces)
    {
        int index = BatchRegions.start(layer, BatchRegions::clear);

        if (index < 0)
        {
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
            BatchRegions.clear();
            return null;
        }
    }

    private static int draw(int index, RenderLayer layer, MatrixStack matrices, Vec3d camera, List<ChunkRendererSchematicVbo> pieces)
    {
        Map<Long, List<ChunkRendererSchematicVbo>> groups = BatchRegions.groups(index, layer, pieces, GPU);
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
        if (group.size() == 1)
        {
            BatchRegions.see(index, group);
            drawSingle(layer, format, matrices, camera, group.get(0));
            return 1;
        }

        BatchRegions.Pick pick = BatchRegions.pick(index, layer, key, group, GPU);

        if (pick.pieces.isEmpty() == false)
        {
            drawRegion(format, matrices, camera, pick.region, pick.pieces);
        }

        for (ChunkRendererSchematicVbo piece : group)
        {
            if (pick.pieces.contains(piece) == false)
            {
                drawSingle(layer, format, matrices, camera, piece);
            }
        }

        return group.size();
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
        BatchRegions.used(region);

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
        BatchRegions.clear();
    }

    /** How many region buffers exist (tests). */
    public static int regionCount()
    {
        return BatchRegions.regionCount();
    }
}
