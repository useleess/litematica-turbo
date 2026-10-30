package com.litematicaturbo.core;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Splits a vertical block range into the parts that lie in 16-high chunk sections worth visiting.
 * A schematic column can be hundreds of blocks tall while only a few sections hold blocks;
 * visiting only the needed sections avoids two block lookups per empty cell.
 */
public final class SectionSlices
{
    private SectionSlices()
    {
    }

    /** Empty sections are skipped when the setting is on and the box lies in one chunk column (one render chunk). */
    public static boolean skipApplies(int fromX, int fromZ, int toX, int toZ)
    {
        return TurboConfig.get().skipEmptySectionsEnabled() && (fromX >> 4) == (toX >> 4) && (fromZ >> 4) == (toZ >> 4);
    }

    /** grid[x][z], or null when the grid is missing or x / z is outside it. */
    public static <T> T cell(T[][] grid, int x, int z)
    {
        if (grid == null || x < 0 || x >= grid.length || z < 0 || z >= grid[x].length)
        {
            return null;
        }

        return grid[x][z];
    }

    /** True when sections[index] exists and is not empty. */
    public static <S> boolean hasBlocks(S[] sections, int index, Predicate<S> isEmpty)
    {
        return index >= 0 && index < sections.length && sections[index] != null && isEmpty.test(sections[index]) == false;
    }

    /**
     * Returns the y ranges to visit as pairs {from0, to0, from1, to1, ...} (inclusive), merging
     * neighbouring needed sections. {@code sectionNeeded} gets the section coordinate (y >> 4).
     * Also counts visited and skipped sections into {@link BuildStats}.
     */
    public static int[] neededRanges(int minY, int maxY, IntPredicate sectionNeeded)
    {
        if (maxY < minY)
        {
            return new int[0];
        }

        int firstSection = minY >> 4;
        int lastSection = maxY >> 4;
        int[] out = new int[(lastSection - firstSection + 1) * 2];
        int count = 0;
        int skipped = 0;
        int runStart = Integer.MIN_VALUE;

        for (int s = firstSection; s <= lastSection; ++s)
        {
            boolean needed = sectionNeeded.test(s);

            if (needed)
            {
                if (runStart == Integer.MIN_VALUE)
                {
                    runStart = Math.max(minY, s << 4);
                }
            }
            else
            {
                ++skipped;

                if (runStart != Integer.MIN_VALUE)
                {
                    out[count++] = runStart;
                    out[count++] = (s << 4) - 1;
                    runStart = Integer.MIN_VALUE;
                }
            }
        }

        if (runStart != Integer.MIN_VALUE)
        {
            out[count++] = runStart;
            out[count++] = maxY;
        }

        BuildStats.recordSections(lastSection - firstSection + 1, skipped);

        if (count == out.length)
        {
            return out;
        }

        int[] trimmed = new int[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }
}
