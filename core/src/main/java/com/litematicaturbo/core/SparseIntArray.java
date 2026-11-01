package com.litematicaturbo.core;

/**
 * Block ids of a box in 16 x 16 x 16 sections that only exist where something other than id 0 is stored, so a huge, mostly
 * empty box takes memory only for the sections that hold blocks (idea from Schematica Upgraded, MIT: Lunatrius /
 * HackerRouter / Useleess). Entries are numbered like Litematica's packed array: x fastest, then z, then y.
 */
public final class SparseIntArray
{
    public static final int MAX_BITS = 16;
    public static final int SECTION_CELLS = 4096;
    public static final int SECTION_BYTES = SECTION_CELLS * 2;

    private final int maxValue;
    private final int sizeX, sizeY, sizeZ;
    private final long size;
    private final long layer;
    private final int sectionsX, sectionsY, sectionsZ;
    private final char[][] sections;
    private int allocated;

    public SparseIntArray(int bits, int sizeX, int sizeY, int sizeZ)
    {
        if (bits < 1 || bits > MAX_BITS)
        {
            throw new IllegalArgumentException("bits " + bits);
        }

        this.maxValue = (1 << bits) - 1;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.size = (long) sizeX * sizeY * sizeZ;
        this.layer = (long) sizeX * sizeZ;
        this.sectionsX = (sizeX + 15) >> 4;
        this.sectionsY = (sizeY + 15) >> 4;
        this.sectionsZ = (sizeZ + 15) >> 4;
        long count = (long) sectionsX * sectionsY * sectionsZ;

        if (count > Integer.MAX_VALUE - 8)
        {
            throw new IllegalArgumentException("too many sections: " + count);
        }

        this.sections = new char[(int) count][];
    }

    /** How many longs the tightly packed form of a box takes. */
    public static long packedLength(int bits, long volume)
    {
        return (volume * bits + 63) / 64;
    }

    /** One entry of a tightly packed long array (the .litematic layout, entries may span two longs). */
    public static int packedGet(long[] words, int bits, long index)
    {
        long start = index * bits;
        int first = (int) (start >> 6);
        int last = (int) (((index + 1) * bits - 1) >> 6);
        int shift = (int) (start & 63);
        long mask = (1L << bits) - 1;

        if (first == last)
        {
            return (int) ((words[first] >>> shift) & mask);
        }

        return (int) (((words[first] >>> shift) | (words[last] << (64 - shift))) & mask);
    }

    private static void packedSet(long[] words, int bits, long index, int value)
    {
        long start = index * bits;
        int first = (int) (start >> 6);
        int last = (int) (((index + 1) * bits - 1) >> 6);
        int shift = (int) (start & 63);
        long mask = (1L << bits) - 1;
        words[first] = (words[first] & ~(mask << shift)) | (((long) value & mask) << shift);

        if (first != last)
        {
            int spill = 64 - shift;
            words[last] = (words[last] & ~(mask >>> spill)) | (((long) value & mask) >>> spill);
        }
    }

    /**
     * Reads a tightly packed long array into sparse sections. Words that are zero are not looked at (all their entries are
     * id 0), so the work follows the number of blocks, not the size of the box. Returns null when the array does not fit
     * the box or the entries are wider than {@link #MAX_BITS}.
     */
    public static SparseIntArray fromPacked(int bits, int sizeX, int sizeY, int sizeZ, long[] words)
    {
        long volume = (long) sizeX * sizeY * sizeZ;

        if (words == null || bits < 1 || bits > MAX_BITS || packedLength(bits, volume) > words.length)
        {
            return null;
        }

        SparseIntArray out;

        try
        {
            out = new SparseIntArray(bits, sizeX, sizeY, sizeZ);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }

        for (int w = 0; w < words.length; w++)
        {
            if (words[w] == 0L)
            {
                continue;
            }

            long first = (w * 64L) / bits;
            long last = Math.min(volume - 1, (w * 64L + 63) / bits);

            for (long e = first; e <= last; e++)
            {
                int value = packedGet(words, bits, e);

                if (value != 0)
                {
                    out.setAt(e, value);
                }
            }
        }

        return out;
    }

    /** Whether at least half of the longs are zero, so sparse sections may be smaller than the packed array. */
    public static boolean mostlyZero(long[] words)
    {
        long nonZero = 0;

        for (long w : words)
        {
            if (w != 0L)
            {
                nonZero++;
            }
        }

        return nonZero * 2 < words.length;
    }

    /** Bytes the packed array takes. */
    public long denseBytes(int bits)
    {
        return packedLength(bits, this.size) * 8;
    }

    /** The tightly packed form again (what Litematica saves). */
    public long[] toPacked(int bits)
    {
        long[] words = new long[(int) packedLength(bits, this.size)];

        for (int sy = 0; sy < this.sectionsY; sy++)
        {
            for (int sz = 0; sz < this.sectionsZ; sz++)
            {
                for (int sx = 0; sx < this.sectionsX; sx++)
                {
                    char[] section = this.sections[this.sectionIndex(sx, sy, sz)];

                    if (section != null)
                    {
                        this.packSection(section, sx << 4, sy << 4, sz << 4, bits, words);
                    }
                }
            }
        }

        return words;
    }

    private void packSection(char[] section, int x0, int y0, int z0, int bits, long[] words)
    {
        for (int i = 0; i < SECTION_CELLS; i++)
        {
            int value = section[i];
            int x = x0 + (i & 15);
            int z = z0 + ((i >> 4) & 15);
            int y = y0 + (i >> 8);

            if (value != 0 && x < this.sizeX && z < this.sizeZ && y < this.sizeY)
            {
                packedSet(words, bits, (y * this.layer) + (long) z * this.sizeX + x, value);
            }
        }
    }

    public int allocatedSections()
    {
        return this.allocated;
    }

    public int totalSections()
    {
        return this.sections.length;
    }

    /** Bytes held by the sections that exist (for the memory report and the tests). */
    public long allocatedBytes()
    {
        return (long) this.allocated * SECTION_BYTES;
    }

    public long size()
    {
        return this.size;
    }

    private int sectionIndex(int sx, int sy, int sz)
    {
        return (sy * this.sectionsZ + sz) * this.sectionsX + sx;
    }

    public int getAt(long index)
    {
        if (index < 0 || index >= this.size)
        {
            return 0;
        }

        int y = (int) (index / this.layer);
        int rest = (int) (index - (long) y * this.layer);
        int z = rest / this.sizeX;
        int x = rest - z * this.sizeX;
        char[] section = this.sections[this.sectionIndex(x >> 4, y >> 4, z >> 4)];
        return section == null ? 0 : section[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)];
    }

    public void setAt(long index, int value)
    {
        if (index < 0 || index >= this.size)
        {
            return;
        }

        if (value < 0 || value > this.maxValue)
        {
            throw new IllegalArgumentException("value " + value + " does not fit " + this.maxValue);
        }

        int y = (int) (index / this.layer);
        int rest = (int) (index - (long) y * this.layer);
        int z = rest / this.sizeX;
        int x = rest - z * this.sizeX;
        int si = this.sectionIndex(x >> 4, y >> 4, z >> 4);
        char[] section = this.sections[si];

        if (section == null)
        {
            if (value == 0)
            {
                return;
            }

            this.sections[si] = section = new char[SECTION_CELLS];
            this.allocated++;
        }

        section[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] = (char) value;
    }
}
