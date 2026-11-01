package com.litematicaturbo.core;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The rules of batch drawing, free of any game or graphics code: which built 16 x 16 x 16 pieces belong to a region of
 * 4 x 4 x 4 pieces, when a region may be made (its pieces stayed unchanged for a while; at most one region and a
 * read-back budget per frame), which pieces a region still holds, and when an unused region is given back. The game
 * layer of each render era keeps the buffers; it asks this book what to do and tells it what it did. One set of
 * regions per block layer. Render thread only.
 */
public final class BatchBook<P>
{
    /** A region is 1 << REGION_SHIFT pieces wide, high and deep. */
    public static final int REGION_SHIFT = 2;
    /** A piece must stay unchanged this many frames before its region is made. */
    public static final int QUIET_FRAMES = 20;
    public static final int MAX_REGIONS_PER_FRAME = 1;
    public static final long MAX_READBACK_BYTES_PER_FRAME = 4L * 1024 * 1024;
    /** A region not drawn for this many frames gives its buffer back. */
    public static final int EVICT_FRAMES = 600;
    private static final int EVICT_CHECK_EVERY = 100;

    /** Where a piece sits in a region's buffer, and the state of the piece it was copied from. */
    public static final class Member
    {
        public final long pos;
        public final int generation;
        public final int first;
        public final int count;

        Member(long pos, int generation, int first, int count)
        {
            this.pos = pos;
            this.generation = generation;
            this.first = first;
            this.count = count;
        }
    }

    /** One region's buffer (handle chosen by the game layer) and the pieces copied into it. */
    public static final class Region<P>
    {
        /** The game layer's buffer handle; 0 while none exists. */
        public int buffer;
        public int originX;
        public int originY;
        public int originZ;
        private long lastUsed;
        private final Map<P, Member> members = new IdentityHashMap<>();

        public Member member(P piece)
        {
            return this.members.get(piece);
        }

        public Iterable<Map.Entry<P, Member>> members()
        {
            return this.members.entrySet();
        }

        public void clearMembers()
        {
            this.members.clear();
        }

        public void add(P piece, long pos, int generation, int first, int count)
        {
            this.members.put(piece, new Member(pos, generation, first, count));
        }
    }

    private static final class Seen
    {
        long pos;
        int generation;
        long since = -1;
    }

    private final List<Map<Long, Region<P>>> regions = new ArrayList<>();
    private final List<Map<P, Seen>> seen = new ArrayList<>();
    private long frame;
    private int madeThisFrame;
    private long readBackThisFrame;

    public BatchBook(int layers)
    {
        for (int i = 0; i < layers; i++)
        {
            this.regions.add(new HashMap<>());
            this.seen.add(new IdentityHashMap<>());
        }
    }

    /** The region a piece belongs to (piece coordinates = block coordinate >> 4), packed into one long. */
    public static long regionKey(int pieceX, int pieceY, int pieceZ)
    {
        long rx = pieceX >> REGION_SHIFT;
        long ry = pieceY >> REGION_SHIFT;
        long rz = pieceZ >> REGION_SHIFT;
        return ((rx & 0x3FFFFFL) << 42) | ((rz & 0x3FFFFFL) << 20) | (ry & 0xFFFFFL);
    }

    /** The block coordinate of a region's corner along one axis, from a piece coordinate. */
    public static int regionOrigin(int pieceCoord)
    {
        return (pieceCoord >> REGION_SHIFT) << (REGION_SHIFT + 4);
    }

    /**
     * Moves the positions of vertices (the first three floats of each vertex) by an offset, so vertices relative to
     * their piece become relative to the region.
     */
    public static void shiftPositions(ByteBuffer data, int start, int vertices, int stride, float dx, float dy, float dz)
    {
        for (int i = 0; i < vertices; i++)
        {
            int at = start + i * stride;
            data.putFloat(at, data.getFloat(at) + dx);
            data.putFloat(at + 4, data.getFloat(at + 4) + dy);
            data.putFloat(at + 8, data.getFloat(at + 8) + dz);
        }
    }

    /** At the start of a frame: resets the budgets; returns the regions to give their buffers back (now and then). */
    public List<Region<P>> nextFrame()
    {
        this.frame++;
        this.madeThisFrame = 0;
        this.readBackThisFrame = 0;
        List<Region<P>> evicted = new ArrayList<>();

        if (this.frame % EVICT_CHECK_EVERY == 0)
        {
            for (Map<Long, Region<P>> layer : this.regions)
            {
                Iterator<Region<P>> it = layer.values().iterator();

                while (it.hasNext())
                {
                    Region<P> region = it.next();

                    if (this.frame - region.lastUsed > EVICT_FRAMES)
                    {
                        evicted.add(region);
                        it.remove();
                    }
                }
            }
        }

        return evicted;
    }

    /** Notes a piece's state; true when it has not changed for QUIET_FRAMES frames. */
    public boolean see(int layer, P piece, long pos, int generation)
    {
        Map<P, Seen> map = this.seen.get(layer);
        Seen s = map.get(piece);

        if (s == null)
        {
            s = new Seen();
            map.put(piece, s);
        }

        if (s.since < 0 || s.pos != pos || s.generation != generation)
        {
            s.pos = pos;
            s.generation = generation;
            s.since = this.frame;
        }

        return this.frame - s.since >= QUIET_FRAMES;
    }

    public Region<P> region(int layer, long key)
    {
        return this.regions.get(layer).get(key);
    }

    /** Whether the region holds the piece exactly as it is now. */
    public boolean holds(Region<P> region, P piece, long pos, int generation)
    {
        Member m = region == null ? null : region.members.get(piece);
        return m != null && m.pos == pos && m.generation == generation;
    }

    /** Whether a region may be made now: the budgets of this frame are not used up. */
    public boolean mayMake()
    {
        return this.madeThisFrame < MAX_REGIONS_PER_FRAME && this.readBackThisFrame < MAX_READBACK_BYTES_PER_FRAME;
    }

    /** A region was made from this many read-back bytes. */
    public void made(int layer, long key, Region<P> region, long readBackBytes)
    {
        region.lastUsed = this.frame;
        this.regions.get(layer).put(key, region);
        this.madeThisFrame++;
        this.readBackThisFrame += readBackBytes;
    }

    /** The region is drawn in this frame, so it is not given back. */
    public void used(Region<P> region)
    {
        region.lastUsed = this.frame;
    }

    /** Drops everything (renderers reloaded, world changed, batch drawing off); returns the regions to free. */
    public List<Region<P>> clear()
    {
        List<Region<P>> all = new ArrayList<>();

        for (int i = 0; i < this.regions.size(); i++)
        {
            all.addAll(this.regions.get(i).values());
            this.regions.get(i).clear();
            this.seen.get(i).clear();
        }

        return all;
    }

    public int regionCount()
    {
        int n = 0;

        for (Map<Long, Region<P>> layer : this.regions)
        {
            n += layer.size();
        }

        return n;
    }
}
