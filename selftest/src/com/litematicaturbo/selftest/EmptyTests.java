package com.litematicaturbo.selftest;

import java.util.Set;

/**
 * empty/: skipping empty 16-high sections of a chunk column while building changes nothing that is built (same
 * geometry with skipping on and off, and the same as plain Litematica's), and the skipped work really is skipped:
 * the section counters show it, and show nothing when skipping or Turbo is off. The sparse zone S is a column of
 * the full world height with a few blocks (at the very bottom, at the very top, on section borders), most of its
 * chunks have no block at all.
 */
final class EmptyTests
{
    private EmptyTests()
    {
    }

    /** Chunk indexes of zone S (3 x 3, column-major): (1,0) and (0,1) have no block at all. */
    private static final int[] S_EMPTY = {3, 1};
    /** (0,0) has a block on the bottom layer of the world; (2,0) one on the very top layer. */
    private static final int[] S_EXTREME = {0, 6};

    /** Versions whose render chunks are single 16-high sections have no empty section inside a build to skip. */
    private static void noSectionsToSkip(Fixture f) throws Exception
    {
        if (f.sectionRenderers())
        {
            throw SelfTest.skip("this version builds 16-high sections one by one, so there is no empty section inside a build to skip");
        }
    }

    private static Long sChunk(Fixture f, int index)
    {
        return f.chunksOf("S").toArray(new Long[0])[index];
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        same(ctx, tests, "same-geometry-sparse", "S");
        same(ctx, tests, "same-geometry-all-states", "A", "B");
        same(ctx, tests, "same-geometry-edges", "E");
        same(ctx, tests, "same-geometry-translucent-fluids", "T", "F");

        tests.add("empty/same-geometry-everything", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture on = WorldTests.turbo(f);
            WorldTests.same(on, WorldTests.noSkip(f), f.allChunks(), "skipping on vs off");
            WorldTests.same(WorldTests.stock(f), on, f.allChunks(), "plain Litematica vs skipping on");
        });

        tests.add("empty/skips-empty-sections", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture on = WorldTests.turbo(f);
            noSectionsToSkip(f);
            SelfTest.check(on.scannedSections > 0, "no section was looked at while building with skipping on");
            SelfTest.check(on.skippedSections > 0, "no empty section was skipped (" + on.scannedSections + " looked at; world height " + f.height + " from y " + f.bottom + ")");
        });

        tests.add("empty/skipped-share-high", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture on = WorldTests.turbo(f);
            noSectionsToSkip(f);
            SelfTest.check(on.scannedSections > 0, "no section was looked at");
            SelfTest.check(on.skippedSections * 2 >= on.scannedSections,
                           "only " + on.skippedSections + " of " + on.scannedSections + " sections were skipped, the test schematics are mostly empty");
        });

        tests.add("empty/skip-off-skips-nothing", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture off = WorldTests.noSkip(f);
            SelfTest.equal(0L, off.skippedSections, "sections skipped with 'skip empty sections' off");
            SelfTest.equal(0L, off.scannedSections, "sections counted with 'skip empty sections' off");
        });

        tests.add("empty/master-off-skips-nothing", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture stock = WorldTests.stock(f);
            SelfTest.equal(0L, stock.skippedSections, "sections skipped with Turbo off");
            SelfTest.equal(0L, stock.scannedSections, "sections counted with Turbo off");
        });

        tests.add("empty/empty-chunks-no-geometry", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture on = WorldTests.turbo(f);
            Fixture.Capture stock = WorldTests.stock(f);

            for (int index : S_EMPTY)
            {
                Fixture.Sig sig = on.sigs.get(sChunk(f, index));
                SelfTest.equal(0, sig.count("data.empty=false"), "build results with something in them for the chunk without any block");
                SelfTest.check(sig.lines.equals(stock.sigs.get(sChunk(f, index)).lines), "empty chunk differs from plain Litematica's: " + sig.diff(stock.sigs.get(sChunk(f, index))));
            }

            SelfTest.check(on.sigs.get(sChunk(f, S_EXTREME[0])).count("data.empty=false") > 0, "the chunk with a block has nothing built");
        });

        tests.add("empty/extreme-sections-kept", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture on = WorldTests.turbo(f);
            Fixture.Capture off = WorldTests.noSkip(f);
            boolean sections = f.sectionRenderers();

            for (int index : S_EXTREME)
            {
                Long k = sChunk(f, index);
                String where = "chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k);

                if (sections && index == S_EXTREME[1])
                {
                    continue; // this version builds only the 16-high sections the camera sees: the section at the very top is not among them
                }

                SelfTest.check(on.sigs.get(k).count("data.empty=false") > 0, "the block at the bottom / top of the world is missing (" + where + ")");
                SelfTest.check(on.sigs.get(k).lines.equals(off.sigs.get(k).lines), where + " differs with skipping off: " + on.sigs.get(k).diff(off.sigs.get(k)));
            }
        });

        tests.add("empty/world-extra-overlay-same", () -> {
            // the "extra block" overlay marks world blocks where the schematic has none: sections with only world blocks must still be built
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture plain = WorldTests.turbo(f);
            Class<?> visuals = ctx.cls("fi.dy.masa.litematica.config.Configs$Visuals");
            Object overlay = Ctx.getStatic(visuals, "ENABLE_SCHEMATIC_OVERLAY");
            Object extra = Ctx.getStatic(visuals, "SCHEMATIC_OVERLAY_TYPE_EXTRA");
            boolean overlayWas = (Boolean) Ctx.call(overlay, "getBooleanValue");
            boolean extraWas = (Boolean) Ctx.call(extra, "getBooleanValue");

            try
            {
                Ctx.call(overlay, "setBooleanValue", true);
                Ctx.call(extra, "setBooleanValue", true);
                Fixture.Capture on = f.capture("extra-skip", true, 3, true, true);
                Fixture.Capture off = f.capture("extra-noskip", true, 3, false, true);
                WorldTests.same(off, on, f.allChunks(), "extra-block overlay, skipping on vs off");
                Long empty = sChunk(f, S_EMPTY[0]);

                if (on.sigs.get(empty).lines.equals(plain.sigs.get(empty).lines))
                {
                    throw SelfTest.skip("the extra-block overlay shows nothing over the empty chunks here, so the test proves nothing");
                }
            }
            finally
            {
                Ctx.call(overlay, "setBooleanValue", overlayWas);
                Ctx.call(extra, "setBooleanValue", extraWas);
            }
        });
    }

    private static void same(Ctx ctx, SelfTest.Tests tests, String name, String... zones)
    {
        tests.add("empty/" + name, () -> {
            Fixture f = WorldTests.fixture(ctx);
            Set<Long> chunks = WorldTests.zones(f, zones);
            Fixture.Capture on = WorldTests.turbo(f);
            WorldTests.hasGeometry(f, on, chunks, "zones " + String.join("+", zones));
            WorldTests.same(WorldTests.noSkip(f), on, chunks, "skipping on vs off");
        });
    }
}
