package com.litematicaturbo.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

public class CoreTest
{
    @Test
    public void allSectionsNeededGivesWholeRange()
    {
        assertArrayEquals(new int[] { -64, 319 }, SectionSlices.neededRanges(-64, 319, s -> true));
    }

    @Test
    public void noSectionsNeededGivesNothing()
    {
        assertEquals(0, SectionSlices.neededRanges(-64, 319, s -> false).length);
    }

    @Test
    public void rangesAreClampedAndMerged()
    {
        // Sections 0,1 needed, 2 not, 3 needed; box y 5..60
        int[] r = SectionSlices.neededRanges(5, 60, s -> s != 2);
        assertArrayEquals(new int[] { 5, 31, 48, 60 }, r);
    }

    @Test
    public void negativeSectionsWork()
    {
        int[] r = SectionSlices.neededRanges(-40, -1, s -> s == -2);
        assertArrayEquals(new int[] { -32, -17 }, r);
    }

    @Test
    public void iteratorVisitsExactlyTheNeededCells()
    {
        int[] ranges = SectionSlices.neededRanges(0, 63, s -> s == 1 || s == 3);
        BoxCellIterator it = new BoxCellIterator(2, 3, 4, 5, ranges);
        Set<String> seen = new HashSet<>();
        int[] p = new int[3];

        while (it.hasNext())
        {
            it.advance(p);
            assertTrue(seen.add(p[0] + "," + p[1] + "," + p[2]));
            assertTrue(p[1] >= 16 && p[1] <= 31 || p[1] >= 48 && p[1] <= 63);
        }

        assertEquals(3 * 3 * 32, seen.size());
    }

    @Test
    public void iteratorMatchesPlainLoopWhenNothingSkipped()
    {
        BoxCellIterator it = new BoxCellIterator(-3, 7, 1, 9, SectionSlices.neededRanges(-20, 20, s -> true));
        List<String> got = new ArrayList<>();
        int[] p = new int[3];

        while (it.hasNext())
        {
            it.advance(p);
            got.add(p[0] + "," + p[1] + "," + p[2]);
        }

        List<String> expected = new ArrayList<>();

        // Same order as vanilla BlockPos.iterate: x fastest, then y, then z
        for (int z = 7; z <= 9; ++z)
            for (int y = -20; y <= 20; ++y)
                for (int x = -3; x <= 1; ++x)
                    expected.add(x + "," + y + "," + z);

        assertEquals(expected, got);
    }

    @Test
    public void skippedRangesKeepVanillaOrder()
    {
        BoxCellIterator it = new BoxCellIterator(0, 0, 1, 1, new int[] { 2, 3, 6, 6 });
        List<String> got = new ArrayList<>();
        int[] p = new int[3];

        while (it.hasNext())
        {
            it.advance(p);
            got.add(p[0] + "," + p[1] + "," + p[2]);
        }

        List<String> expected = new ArrayList<>();

        for (int z = 0; z <= 1; ++z)
            for (int y : new int[] { 2, 3, 6 })
                for (int x = 0; x <= 1; ++x)
                    expected.add(x + "," + y + "," + z);

        assertEquals(expected, got);
    }

    @Test
    public void emptyRangesIterateNothing()
    {
        assertFalse(new BoxCellIterator(0, 0, 15, 15, new int[0]).hasNext());
    }

    @Test
    public void threadCountResolution()
    {
        TurboConfig c = new TurboConfig();
        assertEquals(6, c.resolveBuildThreads(32));
        assertEquals(2, c.resolveBuildThreads(8));
        assertEquals(1, c.resolveBuildThreads(2));
        c.buildThreads = 3;
        assertEquals(3, c.resolveBuildThreads(32));
        c.buildThreads = 99;
        assertEquals(16, c.resolveBuildThreads(32));
        c.buildThreads = -1;
        assertEquals(0, c.resolveBuildThreads(32));
        c.buildThreads = 0;
        c.enabled = false;
        assertEquals(0, c.resolveBuildThreads(32));
        assertFalse(c.skipEmptySectionsEnabled());
    }

    @Test
    public void configRoundTrip() throws Exception
    {
        Path dir = Files.createTempDirectory("turbo");
        Properties p = new Properties();
        p.setProperty("buildThreads", "4");
        p.setProperty("skipEmptySections", "false");
        p.setProperty("enabled", "garbage-means-false");
        TurboConfig c = new TurboConfig();
        c.read(p);
        assertEquals(4, c.buildThreads);
        assertFalse(c.skipEmptySections);
        assertFalse(c.enabled);
        c.save(dir.resolve(TurboConfig.FILE_NAME));

        TurboConfig loaded = TurboConfig.load(dir);
        assertEquals(4, loaded.buildThreads);
        assertFalse(loaded.skipEmptySections);
        assertTrue(loaded.asyncNearRebuilds);
    }

    @Test
    public void everyOptionSurvivesSaveAndLoad() throws Exception
    {
        Path dir = Files.createTempDirectory("turbo");
        TurboConfig c = new TurboConfig();
        c.enabled = false;
        c.buildThreads = 3;
        c.skipEmptySections = false;
        c.asyncNearRebuilds = false;
        c.showStats = false;
        c.ghostMove = false;
        c.ghostSettleMs = 777;
        c.catchUpAuto = false;
        c.catchUpBudgetMs = 13;
        c.farDetail = false;
        c.save(dir.resolve(TurboConfig.FILE_NAME));

        TurboConfig l = TurboConfig.load(dir);
        assertFalse(l.enabled);
        assertEquals(3, l.buildThreads);
        assertFalse(l.skipEmptySections);
        assertFalse(l.asyncNearRebuilds);
        assertFalse(l.showStats);
        assertFalse(l.ghostMove);
        assertEquals(777, l.ghostSettleMs);
        assertFalse(l.catchUpAuto);
        assertEquals(13, l.catchUpBudgetMs);
        assertFalse(l.farDetail);
    }

    @Test
    public void missingConfigIsCreatedWithDefaults() throws Exception
    {
        Path dir = Files.createTempDirectory("turbo");
        TurboConfig c = TurboConfig.load(dir.resolve("sub"));
        assertTrue(c.enabled);
        assertTrue(Files.isRegularFile(dir.resolve("sub").resolve(TurboConfig.FILE_NAME)));
    }
}
