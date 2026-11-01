package com.litematicaturbo.selftest;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * copy/: the complete copy while a placement moves. Litematica's pieces leave out blocks that already match the world, so
 * the old pieces shown shifted have holes; with the copy a second set of pieces built from every schematic block is shown
 * instead. Proved by COUNTING (never by time): the blocks the drawn pieces hold against the schematic's non-air blocks, the
 * pieces built and freed, and what pretended low memory and an error in the copy do. The lone test placement moves with a
 * long settle time, so the shifted view stays up while the test looks at it.
 */
final class CopyTests
{
    private static final String COPY = "com.litematicaturbo.core.CompleteCopy";
    private static final String BUDGET = "com.litematicaturbo.core.MemoryBudget";
    private static final String OFF_KEY = "litematica_turbo.message.complete_off";
    private static final int[] MOVE = {5, 2, -3};
    private static final long WAIT_MS = 90_000L;

    private CopyTests()
    {
    }

    private static Object pieces(Ctx ctx) throws Exception
    {
        return ctx.turbo("CompletePieces");
    }

    private static Object copy(Ctx ctx) throws Exception
    {
        return ctx.cls(COPY);
    }

    private static void setting(Ctx ctx, boolean on) throws Exception
    {
        Ctx.setField(Ctx.call(ctx.cls("com.litematicaturbo.core.TurboConfig"), "get"), "completeCopy", on);
    }

    private static int count(Ctx ctx, Object owner, String method) throws Exception
    {
        return ctx.onMain(() -> (Integer) Ctx.call(owner, method));
    }

    private static long drawnBlocks(Ctx ctx, List<?> renderers) throws Exception
    {
        return ctx.onMain(() -> {
            long n = 0;

            for (Object r : renderers)
            {
                n += (Long) Ctx.call(r, "turbo$drawnBlocks");
            }

            return n;
        });
    }

    /** The renderers the block layers draw right now, and the old pieces (ghosts). */
    private static List<?> blockRenderers(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> new java.util.ArrayList<Object>((List<?>) Ctx.call(ctx.turbo("GhostMove"), "blockRenderers")));
    }

    private static List<?> ghosts(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> new java.util.ArrayList<Object>((List<?>) Ctx.call(ctx.turbo("GhostMove"), "renderers")));
    }

    /**
     * The old pieces that overlap the lone placement's area (the other pieces in view belong to the standard test zones). A
     * piece is 16 blocks high, or the whole world height in the newer versions: its bounding box tells.
     */
    private static List<?> ghostsOfThePlacement(Ctx ctx, int[] start) throws Exception
    {
        List<Object> out = new java.util.ArrayList<>();

        for (Object r : ghosts(ctx))
        {
            double[] box = ctx.onMain(() -> {
                Object b = Ctx.call(r, "getBoundingBox");
                double[] d = new double[6];
                String[] names = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};

                for (int i = 0; i < 6; i++)
                {
                    d[i] = (Double) Ctx.getField(b, names[i]);
                }

                return d;
            });

            if (box[0] < start[0] + 32 && box[3] > start[0] && box[1] < start[1] + 6 && box[4] > start[1] && box[2] < start[2] + 32 && box[5] > start[2])
            {
                out.add(r);
            }
        }

        return out;
    }

    /** The schematic's non-air blocks in the lone placement's area at this origin. */
    private static long schematicBlocks(Ctx ctx, int[] o) throws Exception
    {
        return ctx.onMain(() -> {
            long n = 0;

            for (int y = 0; y < 6; y++)
            {
                for (int z = 0; z < 32; z++)
                {
                    for (int x = 0; x < 32; x++)
                    {
                        if (String.valueOf(Ctx.call(MoveTests.f.schematicWorld(), "getBlockState", MoveTests.f.game.blockPos(o[0] + x, o[1] + y, o[2] + z))).contains("minecraft:air") == false)
                        {
                            n++;
                        }
                    }
                }
            }

            return n;
        });
    }

    /** Puts the same block in the world as the schematic has at every second spot, so the pieces leave those out; null state = air back. */
    private static void matchWorld(Ctx ctx, int[] o, boolean match) throws Exception
    {
        Object air = MoveTests.f.stateNamed("minecraft:air");
        ctx.onMain(() -> {
            Object world = MoveTests.f.game.clientWorld();

            for (int y = 0; y < 6; y++)
            {
                for (int z = 0; z < 32; z++)
                {
                    for (int x = (z + y) % 2; x < 32; x += 2)
                    {
                        Object pos = MoveTests.f.game.blockPos(o[0] + x, o[1] + y, o[2] + z);
                        Object state = Ctx.call(MoveTests.f.schematicWorld(), "getBlockState", pos);

                        if (String.valueOf(state).contains("minecraft:air") == false)
                        {
                            Game.callAny(world, new String[] {"setBlockState", "setBlock"}, pos, match ? state : air, 18);
                        }
                    }
                }
            }

            return null;
        });
    }

    private static void rebuildAll(Ctx ctx) throws Exception
    {
        Set<Long> mine = new LinkedHashSet<>(MoveTests.f.chunksOf("N"));
        mine.retainAll(MoveTests.f.renderedKeys());
        Map<Long, Object> before = MoveTests.f.dataNow(mine);
        MoveTests.f.touchAll();
        MoveTests.f.awaitIdle("the lone placement rebuilt against the changed world", mine, before);
    }

    private static void waitFor(String what, java.util.concurrent.Callable<Boolean> condition) throws Exception
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;

        while (condition.call() == false)
        {
            SelfTest.check(System.currentTimeMillis() < deadline, "timed out waiting for " + what);
            Thread.sleep(50);
        }
    }

    private interface Scenario
    {
        void run(int[] start) throws Exception;
    }

    /**
     * A fresh lone placement (selected) over a world that matches every second block of it, then the scenario, with the copy
     * setting as given; everything is put back afterwards.
     */
    private static void scenario(Ctx ctx, boolean copyOn, boolean matching, Scenario body) throws Exception
    {
        MoveTests.f = WorldTests.fixture(ctx);
        MoveTests.guarded(ctx, () -> {
            Ctx.call(copy(ctx), "resetForTest");
            int[] start = MoveTests.prepare(ctx, true);
            Object config = Ctx.call(ctx.cls("com.litematicaturbo.core.TurboConfig"), "get");
            Ctx.setField(config, "ghostSettleMs", 600_000);
            setting(ctx, copyOn);

            try
            {
                if (matching)
                {
                    matchWorld(ctx, start, true);
                    rebuildAll(ctx);
                }

                ctx.onMain(() -> {
                    Object manager = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
                    Ctx.call(manager, "setSelectedSchematicPlacement", MoveTests.placement);
                    return null;
                });
                body.run(start);
            }
            finally
            {
                Ctx.call(copy(ctx), "resetForTest");
                setting(ctx, true);
                ctx.onMain(() -> {
                    Object manager = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
                    Ctx.call(manager, "setSelectedSchematicPlacement", (Object) null);
                    return null;
                });

                if (matching)
                {
                    matchWorld(ctx, start, false);
                }
            }
        });
    }

    private static void move(Ctx ctx, int[] start) throws Exception
    {
        MoveTests.setOrigin(ctx, start[0] + MOVE[0], start[1] + MOVE[1], start[2] + MOVE[2]);
        SelfTest.check(MoveTests.ghostActive(ctx), "the shifted view was not shown right after the move");
    }

    /** Waits until the complete copy has built and kept every piece it planned. */
    private static void built(Ctx ctx) throws Exception
    {
        Object pieces = pieces(ctx);
        waitFor("the complete copy to be built", () -> {
            int planned = count(ctx, pieces, "pieceCount");
            return planned > 0 && count(ctx, pieces, "keptCount") == planned;
        });
    }

    private static void ghostsOnly(Ctx ctx, String when) throws Exception
    {
        Object pieces = pieces(ctx);
        SelfTest.check(MoveTests.ghostActive(ctx), when + ": the shifted old pieces are not shown");
        SelfTest.equal(0, count(ctx, pieces, "pieceCount"), when + ": complete pieces planned");
        SelfTest.equal(0, count(ctx, pieces, "drawnCompleteCount"), when + ": complete pieces drawn");
        SelfTest.equal(ghosts(ctx), blockRenderers(ctx), when + ": the block layers draw something other than the old pieces");
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("copy/drawn-pieces-hold-every-block", () -> scenario(ctx, true, true, start -> {
            long expected = schematicBlocks(ctx, start);
            SelfTest.check(expected > 0, "the lone placement holds no blocks");
            move(ctx, start);
            built(ctx);
            List<?> drawn = blockRenderers(ctx);
            SelfTest.equal(count(ctx, pieces(ctx), "pieceCount"), count(ctx, pieces(ctx), "drawnCompleteCount"), "complete pieces drawn");
            SelfTest.equal(count(ctx, pieces(ctx), "keptCount"), drawn.size(), "pieces in the drawn list (a leftover old piece is drawn next to the copy)");
            SelfTest.equal(expected, drawnBlocks(ctx, drawn), "blocks the drawn pieces hold, against the schematic's non-air blocks");
        }));

        tests.add("copy/off-keeps-the-holes", () -> scenario(ctx, false, true, start -> {
            long expected = schematicBlocks(ctx, start);
            move(ctx, start);
            ctx.ticks(10);
            ghostsOnly(ctx, "setting off");
            List<?> mine = ghostsOfThePlacement(ctx, start);
            long shown = drawnBlocks(ctx, mine);
            SelfTest.check(shown > 0 && shown < expected, "the " + mine.size() + " old pieces of the placement hold " + shown + " blocks, the schematic " + expected
                           + ": the world's matching blocks should be missing from them");
        }));

        tests.add("copy/pieces-built-nearest-first-and-kept", () -> scenario(ctx, true, false, start -> {
            move(ctx, start);
            built(ctx);
            Object pieces = pieces(ctx);
            SelfTest.check(count(ctx, pieces, "pieceCount") >= 4, "only " + count(ctx, pieces, "pieceCount") + " complete pieces planned");
            SelfTest.equal(false, ctx.onMain(() -> Ctx.call(pieces, "budgetHit")), "the memory limit was hit by a small placement");
            SelfTest.check((Long) ctx.onMain(() -> Ctx.call(pieces, "usedBytes")) > 0, "the kept pieces use no memory");
            SelfTest.check((Long) ctx.onMain(() -> Ctx.call(pieces, "usedBytes")) <= (Long) ctx.onMain(() -> Ctx.call(pieces, "budgetBytes")), "the kept pieces use more than the limit");
            SelfTest.equal(1, count(ctx, copy(ctx), "made"), "complete copies made");
        }));

        tests.add("copy/deselected-frees-the-pieces", () -> scenario(ctx, true, false, start -> {
            move(ctx, start);
            built(ctx);
            Object pieces = pieces(ctx);
            int kept = count(ctx, pieces, "keptCount");
            int freedBefore = count(ctx, pieces, "freedCount");
            ctx.onMain(() -> {
                Object manager = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
                Ctx.call(manager, "setSelectedSchematicPlacement", (Object) null);
                return null;
            });
            waitFor("the deselected placement's copy to be dropped", () -> count(ctx, pieces, "pieceCount") == 0);
            SelfTest.equal(false, ctx.onMain(() -> Ctx.call(pieces, "exists")), "a copy still exists");
            SelfTest.equal(kept, count(ctx, pieces, "freedCount") - freedBefore, "pieces whose graphics buffers were deleted");
            SelfTest.check(MoveTests.ghostActive(ctx), "the move itself ended with the copy");
        }));

        tests.add("copy/low-java-memory-builds-none", () -> scenario(ctx, true, false, start -> {
            Object budget = ctx.cls(BUDGET);
            Ctx.call(budget, "setMaxMemoryForTest", 1L);

            try
            {
                move(ctx, start);
                ctx.ticks(10);
                ghostsOnly(ctx, "Java memory low");
                SelfTest.equal(1, count(ctx, copy(ctx), "skippedLowMemory"), "moves that skipped the copy for low memory");
            }
            finally
            {
                Ctx.call(budget, "setMaxMemoryForTest", 0L);
            }
        }));

        tests.add("copy/low-graphics-memory-builds-none", () -> scenario(ctx, true, false, start -> {
            Ctx.call(copy(ctx), "setGpuFreeKbForTest", 1000L);
            move(ctx, start);
            ctx.ticks(10);
            ghostsOnly(ctx, "graphics memory low");
            SelfTest.equal(1, count(ctx, copy(ctx), "skippedLowMemory"), "moves that skipped the copy for low memory");
        }));

        tests.add("copy/memory-getting-tight-drops-the-copy", () -> scenario(ctx, true, false, start -> {
            move(ctx, start);
            built(ctx);
            Object pieces = pieces(ctx);
            int kept = count(ctx, pieces, "keptCount");
            int freedBefore = count(ctx, pieces, "freedCount");
            Ctx.call(copy(ctx), "setGpuFreeKbForTest", 1000L);
            waitFor("the copy to be dropped", () -> count(ctx, pieces, "pieceCount") == 0);
            SelfTest.equal(1, count(ctx, copy(ctx), "droppedTight"), "copies dropped for tight memory");
            SelfTest.equal(kept, count(ctx, pieces, "freedCount") - freedBefore, "pieces whose graphics buffers were deleted");
            SelfTest.equal(ghosts(ctx), blockRenderers(ctx), "the block layers draw something other than the old pieces");
        }));

        tests.add("copy/error-switches-off-with-a-chat-line", () -> scenario(ctx, true, false, start -> {
            Ctx.call(copy(ctx), "setFaultForTest", true);
            move(ctx, start);
            ctx.ticks(10);
            SelfTest.check(Ctx.call(copy(ctx), "failure") != null, "the error did not switch the complete copy off");
            String chat = SafetyTests.chatText(ctx);
            SelfTest.check(chat.contains(OFF_KEY), "no chat line says the complete copy is off:\n" + chat);
            SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");
            Ctx.call(copy(ctx), "setFaultForTest", false);
            ghostsOnly(ctx, "after the error");
            MoveTests.setOrigin(ctx, start[0] + MOVE[0] + 1, start[1] + MOVE[1], start[2] + MOVE[2]);
            ctx.ticks(5);
            ghostsOnly(ctx, "moving on after the error");
        }));

        tests.add("copy/screenshot-while-moving", () -> scenario(ctx, true, true, start -> {
            move(ctx, start);
            built(ctx);
            ctx.ticks(10);
            // The benchmark's screenshot call (every version has it; the benchmark itself is not started)
            Constructor<?> make = ctx.cls(ctx.pkg + ".bench.TurboBench").getDeclaredConstructor(File.class);
            make.setAccessible(true);
            Object bench = make.newInstance(new File("unused"));
            ctx.onMain(() -> Ctx.call(bench, "screenshot", "turbo-copy-moving.png"));
            ctx.ticks(5);
        }));

        tests.add("copy/messages-translated", () -> {
            String lang = AuditTests.resource(ctx.cls(COPY).getClassLoader(), "assets/litematica_turbo/lang/en_us.json");
            SelfTest.check(lang != null, "no en_us.json");

            for (String key : new String[] {OFF_KEY, "config.name.turbocompletecopy"})
            {
                SelfTest.check(lang.contains("\"" + key + "\": \""), "en_us.json has no English text for " + key);
            }

            Method translate = ctx.cls("fi.dy.masa.malilib.util.StringUtils").getMethod("translate", String.class, Object[].class);
            String text = (String) translate.invoke(null, OFF_KEY, new Object[] {"reason-4242"});
            SelfTest.check(text.equals(OFF_KEY) == false && text.contains("reason-4242"), "the message is not translated by the game: " + text);
        });
    }
}
