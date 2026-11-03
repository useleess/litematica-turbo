package com.litematicaturbo.selftest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * move/: moving a placement (what a player does with the placement move keys or the Placement Configuration screen).
 * With Turbo, a lone placement that is being moved is shown shifted by the distance moved without copying and
 * rebuilding at every step, and once it has stayed still for ghostSettleMs the real rebuild runs and the view
 * switches over. Every scenario is run twice, with Turbo and with plain Litematica (master switch off), and the two
 * final results must be the same; what a player sees on the way (shifted by exactly the distance, no rebuild per step,
 * a complete view at the moment the shifted copy goes away) is checked in the Turbo run.
 *
 * The lone placement "M" (2 x 2 chunks, a few mixed blocks overhead, a gold marker block at a known place) replaces
 * the standard test zones for these tests: Turbo only moves the cheap way while one placement is enabled.
 */
final class MoveTests
{
    private MoveTests()
    {
    }

    /** What one run of a scenario saw. */
    private static final class Run
    {
        Fixture.Capture finalView;
        Fixture.Capture reloadedView;
        long builds0;
        StringBuilder buildsAfterStep = new StringBuilder();
        List<Long> buildsAfterStepList = new ArrayList<>();
        long buildsWhileMoving;
        long buildsInTotal;
        boolean activeRightAfter;
        boolean activeAfterSettle;
        boolean incompleteWhenEnded;
        boolean removed;
        int[] finalOrigin;
        String markerNew = "";
        int worldHash;
        String markerOld = "";
        List<int[]> shownOrigins = new ArrayList<>();
        List<int[]> before = new ArrayList<>();
    }

    static Fixture f;
    static Object placement;

    static boolean ghostActive(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> (Boolean) Ctx.call(ctx.turbo("GhostMove"), "isActive"));
    }

    /** Moves the placement to this origin the way the game's move command does. */
    static void setOrigin(Ctx ctx, int x, int y, int z) throws Exception
    {
        ctx.onMain(() -> {
            Object pos = f.game.blockPos(x, y, z);

            for (Method m : placement.getClass().getMethods())
            {
                if (m.getName().equals("setOrigin") && m.getParameterCount() >= 1 && m.getParameterTypes()[0].isInstance(pos))
                {
                    Object[] args = new Object[m.getParameterCount()];
                    args[0] = pos;
                    m.invoke(placement, args);
                    return null;
                }
            }

            throw new NoSuchMethodException("SchematicPlacement.setOrigin(BlockPos, ...)");
        });
    }

    static int[] origin(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> {
            Object o = Ctx.call(placement, "getOrigin");
            return new int[] {(Integer) Ctx.call(o, "getX"), (Integer) Ctx.call(o, "getY"), (Integer) Ctx.call(o, "getZ")};
        });
    }

    static String stateAt(Ctx ctx, int x, int y, int z) throws Exception
    {
        return ctx.onMain(() -> String.valueOf(Ctx.call(f.schematicWorld(), "getBlockState", f.game.blockPos(x, y, z))));
    }

    /** A fresh lone placement at its starting place, all built, Turbo (or plain Litematica) configured. */
    static int[] prepare(Ctx ctx, boolean turbo) throws Exception
    {
        f.configure(turbo, 3, true, true);

        if (placement != null)
        {
            Object old = placement;
            ctx.onMain(() -> {
                Object manager = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
                Ctx.call(manager, "removeSchematicPlacement", old);
                return null;
            });
        }

        placement = f.placeLone("M");
        int[] start = origin(ctx);
        settle(ctx, f.chunksAt(start[0], start[2]));
        return start;
    }

    /** A number for all blocks of the placement's area in the schematic world. */
    private static int hashRegion(Ctx ctx, int[] o) throws Exception
    {
        return ctx.onMain(() -> {
            int h = 1;

            for (int y = 0; y < 6; y++)
            {
                for (int z = 0; z < 32; z++)
                {
                    for (int x = 0; x < 32; x++)
                    {
                        h = h * 31 + String.valueOf(Ctx.call(f.schematicWorld(), "getBlockState", f.game.blockPos(o[0] + x, o[1] + y, o[2] + z))).hashCode();
                    }
                }
            }

            return h;
        });
    }

    static void settle(Ctx ctx, Set<Long> chunks) throws Exception
    {
        Set<Long> inView = new LinkedHashSet<>();

        // the renderers of a new placement's chunks appear within a few frames, not all in the same one (a busy test box
        // once gave only 3 of them): wait for all of them, or until no new one came for 2 s
        for (int tries = 0, still = 0; tries < 400 && inView.size() < chunks.size() && (inView.isEmpty() || still < 40); tries++)
        {
            int before = inView.size();
            inView.addAll(chunks);
            inView.retainAll(f.renderedKeys());
            still = inView.size() == before ? still + 1 : 0;
            Thread.sleep(50);
        }

        SelfTest.check(inView.isEmpty() == false, "the lone test placement is not in view");
        f.awaitIdle("the lone test placement built", inView, null);
        f.awaitQuiet("everything built after placing the lone test placement");
    }

    private static void restore(Ctx ctx) throws Exception
    {
        Object config = Ctx.call(f.configClass, "get");
        Ctx.setField(config, "ghostMove", true);
        Ctx.setField(config, "ghostSettleMs", 250);
        Ctx.setField(config, "completeCopy", true);
        Ctx.setField(config, "catchUpAuto", true);
        Ctx.setField(config, "catchUpBudgetMs", 8);
    }

    /**
     * One scenario: a fresh placement, the given steps ({dx, dy, dz} from the starting origin, one every stepMs),
     * then wait for everything to settle. extra runs right after the last step (on the test thread).
     */
    /** The description of the chunks once two looks in a row agree (uploads to the graphics card finish a little after the builds). */
    private static java.util.Map<Long, Fixture.Sig> stableDescription(Set<Long> keys) throws Exception
    {
        java.util.Map<Long, Fixture.Sig> look = f.describeAll(keys);

        for (int tries = 0; tries < 80; tries++)
        {
            Thread.sleep(150);
            java.util.Map<Long, Fixture.Sig> next = f.describeAll(keys);
            boolean same = next.keySet().equals(look.keySet());

            for (Long k : next.keySet())
            {
                same &= same && next.get(k).lines.equals(look.get(k).lines);
            }

            look = next;

            if (same)
            {
                break;
            }
        }

        return look;
    }

    private static Run run(Ctx ctx, boolean turbo, int stepMs, int[][] steps, Extra extra) throws Exception
    {
        Run r = new Run();
        int[] start = prepare(ctx, turbo);
        r.before = f.renderOrigins();
        long builds0 = f.stats("totalBuilds");
        r.builds0 = builds0;

        for (int[] s : steps)
        {
            setOrigin(ctx, start[0] + s[0], start[1] + s[1], start[2] + s[2]);
            r.buildsAfterStepList.add(f.stats("totalBuilds") - builds0);
            r.buildsAfterStep.append(' ').append(f.stats("totalBuilds") - builds0);

            if (stepMs > 0)
            {
                Thread.sleep(stepMs);
            }
        }

        r.activeRightAfter = ghostActive(ctx);
        r.buildsWhileMoving = f.stats("totalBuilds") - builds0;

        if (r.activeRightAfter)
        {
            r.shownOrigins = ctx.onMain(() -> {
                List<int[]> out = new ArrayList<>();

                for (Object renderer : (List<?>) Ctx.call(ctx.turbo("GhostMove"), "renderers"))
                {
                    Object o = Ctx.call(renderer, "getOrigin");
                    out.add(new int[] {(Integer) Ctx.call(o, "getX"), (Integer) Ctx.call(o, "getY"), (Integer) Ctx.call(o, "getZ")});
                }

                return out;
            });
        }

        if (extra != null)
        {
            extra.run(r);
        }

        r.finalOrigin = origin(ctx);
        // wait for the end of the ghosts, watching every poll: when they are gone the whole view must be built
        long deadline = System.currentTimeMillis() + 90_000L;
        boolean wasActive = r.activeRightAfter;

        while (System.currentTimeMillis() < deadline)
        {
            boolean active = ghostActive(ctx);

            if (wasActive && active == false && f.everythingBuilt() == false)
            {
                r.incompleteWhenEnded = true;
            }

            wasActive = active;

            if (active == false && f.placementsIdle())
            {
                break;
            }

            Thread.sleep(25);
        }

        if (r.removed == false)
        {
            settle(ctx, f.chunksAt(r.finalOrigin[0], r.finalOrigin[2]));
        }

        r.activeAfterSettle = ghostActive(ctx);
        r.buildsInTotal = f.stats("totalBuilds") - builds0;
        r.finalView = new Fixture.Capture(turbo ? "turbo-moved" : "stock-moved");
        Set<Long> keys = f.renderedKeys();
        // uploads to the graphics card finish a little after the builds: describe until two looks in a row agree
        java.util.Map<Long, Fixture.Sig> look = stableDescription(keys);

        r.finalView.sigs.putAll(look);

        // What the same chunks look like when they are all built again from the final world (the settled picture): plain
        // Litematica's own moved view can keep a border face that its next full rebuild drops, so it is no steady reference
        if (r.removed == false && extra != null)
        {
            Set<Long> mine = new LinkedHashSet<>(f.chunksOf("N"));
            mine.retainAll(keys);
            java.util.Map<Long, Object> dataBefore = f.dataNow(mine);
            f.touchAll();
            f.awaitIdle("everything rebuilt from the final world", mine, dataBefore);
            r.reloadedView = new Fixture.Capture(turbo ? "turbo-rebuilt" : "stock-rebuilt");
            r.reloadedView.sigs.putAll(stableDescription(mine));
        }

        r.worldHash = r.removed ? 0 : hashRegion(ctx, r.finalOrigin);
        r.markerNew = stateAt(ctx, r.finalOrigin[0] + Fixture.MARKER[0], r.finalOrigin[1] + Fixture.MARKER[1], r.finalOrigin[2] + Fixture.MARKER[2]);
        r.markerOld = stateAt(ctx, start[0] + Fixture.MARKER[0], start[1] + Fixture.MARKER[1], start[2] + Fixture.MARKER[2]);

        if (r.finalOrigin[0] == start[0] && r.finalOrigin[1] == start[1] && r.finalOrigin[2] == start[2])
        {
            r.markerOld = "(not moved)";
        }

        return r;
    }

    private interface Extra
    {
        void run(Run r) throws Exception;
    }

    /** The two runs of a scenario and the check that they end the same. */
    private static Run[] both(Ctx ctx, int stepMs, int[][] steps) throws Exception
    {
        try
        {
            return bothOnce(ctx, stepMs, steps);
        }
        catch (SelfTest.Fail first)
        {
            // The order of the quads of translucent blocks follows the build moment; once more before calling it a difference
            if (first.getMessage().contains("differs between 'stock-moved' and 'turbo-moved'") == false)
            {
                throw first;
            }

            return bothOnce(ctx, stepMs, steps);
        }
    }

    private static Run[] bothOnce(Ctx ctx, int stepMs, int[][] steps) throws Exception
    {
        Run turbo = run(ctx, true, stepMs, steps, null);
        Run stock = run(ctx, false, stepMs, steps, null);
        sameFinal(stock, turbo);
        return new Run[] {stock, turbo};
    }

    /** The lines about what was uploaded to the graphics card (older versions keep it next to the build result). */
    private static final java.util.regex.Pattern UPLOAD_STATE = java.util.regex.Pattern.compile("(?s).*vertexBuffer(Blocks|Overlay).*");

    /** The view with the upload-state lines taken out: the upload is compared by its own (known bug) test. */
    private static Fixture.Capture withoutUploads(Fixture.Capture c)
    {
        Fixture.Capture out = new Fixture.Capture(c.label);

        for (java.util.Map.Entry<Long, Fixture.Sig> e : c.sigs.entrySet())
        {
            List<String> lines = new ArrayList<>();

            for (String l : e.getValue().lines)
            {
                if (UPLOAD_STATE.matcher(l).matches() == false)
                {
                    lines.add(l);
                }
            }

            out.sigs.put(e.getKey(), new Fixture.Sig(lines));
        }

        return out;
    }

    private static boolean same(Fixture.Capture a, Fixture.Capture b, Set<Long> chunks)
    {
        try
        {
            WorldTests.same(a, b, chunks, "x");
            return true;
        }
        catch (SelfTest.Fail e)
        {
            return false;
        }
    }

    private static void sameFinal(Run stock, Run turbo)
    {
        SelfTest.check(java.util.Arrays.equals(stock.finalOrigin, turbo.finalOrigin), "the placement ended at different places");
        Set<Long> common = new LinkedHashSet<>(stock.finalView.sigs.keySet());
        common.retainAll(turbo.finalView.sigs.keySet());
        SelfTest.check(common.size() >= 4, "only " + common.size() + " chunks in view in both runs");
        SelfTest.equal(stock.worldHash, turbo.worldHash, "the blocks of the placement in the schematic world after the move");
        if (stock.reloadedView != null && turbo.reloadedView != null)
        {
            // The turned placement: the views that were built while the placement and its neighbours were still being worked on
            // can keep or drop one face at a chunk border, in plain Litematica as well and differently from run to run. What
            // must agree is what a full rebuild of the final world gives.
            Set<Long> mine = new LinkedHashSet<>(turbo.reloadedView.sigs.keySet());
            mine.retainAll(stock.reloadedView.sigs.keySet());
            mine.retainAll(turbo.finalView.sigs.keySet());
            SelfTest.check(mine.size() >= 2, "only " + mine.size() + " test chunks in view in both runs");
            WorldTests.same(withoutUploads(stock.reloadedView), withoutUploads(turbo.reloadedView), mine, "after the move, plain Litematica fully rebuilt vs Turbo fully rebuilt");
        }
        else
        {
            WorldTests.same(withoutUploads(stock.finalView), withoutUploads(turbo.finalView), common, "after the move, plain Litematica vs Turbo");
        }

        SelfTest.equal(stock.markerNew, turbo.markerNew, "the marker block at the new place");
        SelfTest.equal(stock.markerOld, turbo.markerOld, "the block left at the old place");
    }

    /**
     * Before a turned-while-moving comparison: the lone placement put straight where and how the runs end (plain Litematica,
     * no move). The comparison needs 4 of its chunks in the camera's view; fewer is the test window's shape / field of view,
     * told here instead of as a later "only N chunks in view".
     */
    private static void inView(Ctx ctx, String setter, String constant, int[] step, int[] marker) throws Exception
    {
        int[] start = prepare(ctx, false);
        int[] o = {start[0] + step[0], start[1] + step[1], start[2] + step[2]};
        setOrigin(ctx, o[0], o[1], o[2]);
        PlaceTests.turn(ctx, placement, setter, constant);
        Set<Long> area = f.chunksAt(start[0] + 8, start[2]);
        settle(ctx, area);
        String m = stateAt(ctx, o[0] + marker[0], o[1] + marker[1], o[2] + marker[2]);
        SelfTest.check(m.contains("gold_block"), "the turned placement is not where the test expects it (no marker block at its place: " + m + ")");
        Set<Long> seen = new LinkedHashSet<>(area);
        seen.retainAll(f.renderedKeys());
        SelfTest.check(seen.size() >= 4, "TEST ENVIRONMENT: only " + seen.size() + " of the " + area.size()
                                         + " chunks the turned placement ends on are in the camera's view (game window shape / field of view), not a mod problem");
    }

    private static void scenario(Ctx ctx, SelfTest.Tests tests, String name, int stepMs, int[]... steps)
    {
        tests.add("move/same-as-stock-" + name, () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Run[] r = both(ctx, stepMs, steps);

                SelfTest.check(r[1].markerNew.contains("gold_block"), "the marker block is not at the new place: " + r[1].markerNew);
                SelfTest.check(r[1].markerOld.contains("gold_block") == false, "the marker block is still at the old place");
            });
        });
    }

    interface Body
    {
        void run() throws Exception;
    }

    /** Puts the lone placement away and the standard zones back, whatever happens. */
    static void guarded(Ctx ctx, Body body) throws Exception
    {
        f.zonesEnabled(false);

        try
        {
            body.run();
        }
        finally
        {
            try
            {
                restore(ctx);

                if (placement != null)
                {
                    Object old = placement;
                    placement = null;
                    ctx.onMain(() -> {
                        Object manager = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
                        Ctx.call(manager, "removeSchematicPlacement", old);
                        return null;
                    });
                }

                f.zonesEnabled(true);
                f.configure(true, 3, true, true);
                f.awaitIdle("the standard test zones back", f.allChunks(), null);
            }
            catch (Exception e)
            {
                throw new SelfTest.Fail("could not put the standard test zones back: " + e);
            }
        }
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        scenario(ctx, tests, "one-step-inside-chunk", 0, new int[] {1, 0, 0});
        scenario(ctx, tests, "one-step-across-chunk", 0, new int[] {17, 0, 0});
        scenario(ctx, tests, "up-and-sideways", 0, new int[] {5, 3, -4});
        scenario(ctx, tests, "many-small-steps", 40, new int[] {1, 0, 0}, new int[] {2, 0, 0}, new int[] {3, 0, 1}, new int[] {4, 1, 1}, new int[] {5, 1, 2}, new int[] {6, 1, 2});
        scenario(ctx, tests, "there-and-back", 30, new int[] {9, 0, 0}, new int[] {18, 0, 2}, new int[] {0, 0, 0});
        scenario(ctx, tests, "diagonal-far", 0, new int[] {19, 1, 6});
        scenario(ctx, tests, "pauses-between-steps", 700, new int[] {4, 0, 0}, new int[] {8, 1, 0});

        tests.add("move/ghost-shifted-by-the-distance", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Run r = run(ctx, true, 0, new int[][] {{5, 2, -3}}, null);
                SelfTest.check(r.activeRightAfter, "the shifted view was not shown right after the move");
                SelfTest.check(r.shownOrigins.isEmpty() == false, "the shifted view has no chunk");
                int matched = 0;

                for (int[] shown : r.shownOrigins)
                {
                    // every shown chunk is a chunk of the grid, moved by exactly the distance
                    SelfTest.check(Math.floorMod(shown[0] - 5, 16) == 0 && Math.floorMod(shown[2] + 3, 16) == 0 && Math.floorMod(shown[1] - 2, 16) == 0,
                                   "a shown chunk at " + shown[0] + "," + shown[1] + "," + shown[2] + " is not a chunk of the grid shifted by 5,2,-3");
                }

                for (int[] b : r.before)
                {
                    boolean found = false;

                    for (int[] shown : r.shownOrigins)
                    {
                        found |= b[0] + 5 == shown[0] && b[1] + 2 == shown[1] && b[2] - 3 == shown[2];
                    }

                    SelfTest.check(found, "the chunk at " + b[0] + "," + b[1] + "," + b[2] + " from before the move is not shown shifted by 5,2,-3");
                    ++matched;
                }

                SelfTest.check(matched == r.before.size() && matched > 0, "chunks from before the move shown shifted: " + matched);
            });
        });

        tests.add("move/no-rebuild-per-step", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                int[][] steps = {{1, 0, 0}, {2, 0, 0}, {3, 0, 0}, {4, 0, 0}, {5, 0, 0}, {6, 0, 0}};
                Run turbo = run(ctx, true, 20, steps, null);
                Run stock = run(ctx, false, 20, steps, null);
                String trace = " (builds after each step: Turbo" + turbo.buildsAfterStep + ", plain Litematica" + stock.buildsAfterStep + ")";
                if (stock.buildsWhileMoving == 0 && f.plainHasNoWorkers())
                {
                    throw SelfTest.skip("plain Litematica 0.24 has no build workers, so there is no plain rebuild per step to compare with" + trace);
                }

                SelfTest.check(stock.buildsWhileMoving > 0, "plain Litematica built nothing while moving, the scenario proves nothing" + trace);
                // the first steps may build the chunks the view starts from once (older versions); after that a step costs no build at all
                SelfTest.check(turbo.buildsWhileMoving * 2 <= stock.buildsWhileMoving, "Turbo built " + turbo.buildsWhileMoving + " chunks while moving, plain Litematica " + stock.buildsWhileMoving + trace);
                SelfTest.check(turbo.buildsAfterStepList.get(steps.length - 1).equals(turbo.buildsAfterStepList.get(1)), "more chunk builds after the second step" + trace);
                SelfTest.check(turbo.buildsInTotal > 0, "Turbo never rebuilt after the move");
            });
        });

        tests.add("move/rebuilt-when-it-stops", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Run r = run(ctx, true, 0, new int[][] {{20, 0, 0}}, null);
                SelfTest.check(r.activeRightAfter, "no shifted view during the move");
                SelfTest.check(r.activeAfterSettle == false, "the shifted view is still there after everything was rebuilt");
                SelfTest.check(r.buildsInTotal > 0, "nothing was rebuilt after the move");
                SelfTest.check(r.markerNew.contains("gold_block"), "the marker block is not at the new place: " + r.markerNew);
            });
        });

        tests.add("move/swap-complete-view", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Run r = run(ctx, true, 0, new int[][] {{13, 0, 5}}, null);
                SelfTest.check(r.activeRightAfter, "no shifted view during the move");
                SelfTest.check(r.incompleteWhenEnded == false, "the shifted view went away while chunks of the real view were still unbuilt");
            });
        });

        tests.add("move/settle-time-setting", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Object config = Ctx.call(f.configClass, "get");
                Ctx.setField(config, "ghostSettleMs", 1500);
                // the complete copy builds pieces in the background while the view is held (it is tested by its own tests); only the real rebuild must wait
                Ctx.setField(config, "completeCopy", false);
                Run r = run(ctx, true, 0, new int[][] {{7, 0, 0}}, run -> {
                    long builds = f.stats("totalBuilds");
                    Thread.sleep(700);
                    SelfTest.check(ghostActive(ctx), "the shifted view ended before the settle time of 1500 ms");
                    SelfTest.equal(builds, f.stats("totalBuilds"), "chunk builds before the settle time was over");
                });
                SelfTest.check(r.activeAfterSettle == false, "the shifted view never ended");
            });
        });

        tests.add("move/catch-up-fixed-budget", () -> {
            // the fixed render-thread budget instead of the automatic one: slower, but every block arrives and the view completes
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Object config = Ctx.call(f.configClass, "get");
                Ctx.setField(config, "catchUpAuto", false);
                Ctx.setField(config, "catchUpBudgetMs", 2);
                int[][] steps = {{19, 1, 6}};
                Run turbo = run(ctx, true, 0, steps, null);
                Run stock = run(ctx, false, 0, steps, null);
                SelfTest.check(turbo.activeRightAfter, "no shifted view during the move");
                SelfTest.check(turbo.activeAfterSettle == false, "the shifted view never ended");
                SelfTest.equal(stock.worldHash, turbo.worldHash, "the blocks of the placement in the schematic world");
                SelfTest.equal(stock.markerNew, turbo.markerNew, "the marker block at the new place");
            });
        });

        tests.add("knownbug/move/fixed-budget-final-view", () -> {
            // the same move as above: the finished view should be exactly plain Litematica's
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Object config = Ctx.call(f.configClass, "get");
                Ctx.setField(config, "catchUpAuto", false);
                Ctx.setField(config, "catchUpBudgetMs", 2);
                int[][] steps = {{19, 1, 6}};
                Run stock = run(ctx, false, 0, steps, null);
                Run again = run(ctx, false, 0, steps, null);
                sameFinal(stock, again); // plain Litematica must agree with itself, or this proves nothing
                Run turbo = run(ctx, true, 0, steps, null);
                sameFinal(stock, turbo);
            });
        });

        tests.add("knownbug/move/near-chunks-keep-block-buffers", () -> {
            // after a move across a chunk border, the chunks in front of the player should hold the same uploaded
            // block buffers as with plain Litematica (seen on 1.19.4 and 1.20.1: none, while the build result is the same)
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                int[][] steps = {{17, 0, 0}};
                Run turbo = run(ctx, true, 0, steps, null);
                Run stock = run(ctx, false, 0, steps, null);

                for (Long k : stock.finalView.sigs.keySet())
                {
                    if (turbo.finalView.sigs.containsKey(k))
                    {
                        List<String> a = new ArrayList<>();
                        List<String> b = new ArrayList<>();

                        for (String l : stock.finalView.sigs.get(k).lines)
                        {
                            if (UPLOAD_STATE.matcher(l).matches())
                            {
                                a.add(l);
                            }
                        }

                        for (String l : turbo.finalView.sigs.get(k).lines)
                        {
                            if (UPLOAD_STATE.matcher(l).matches())
                            {
                                b.add(l);
                            }
                        }

                        SelfTest.check(a.equals(b), "chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k) + " holds different uploaded buffers: plain Litematica " + a.size() + " lines, Turbo " + b.size()
                                       + (a.isEmpty() || b.isEmpty() ? "" : " (first difference: " + new Fixture.Sig(a).diff(new Fixture.Sig(b)) + ")"));
                    }
                }
            });
        });

        tests.add("move/ghost-setting-off", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Object config = Ctx.call(f.configClass, "get");
                Ctx.setField(config, "ghostMove", false);
                Run[] r = both(ctx, 0, new int[][] {{11, 0, 0}});
                SelfTest.check(r[1].activeRightAfter == false, "a shifted view was shown with the 'smooth moving' setting off");
            });
        });

        tests.add("move/turbo-off-no-ghost", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Run r = run(ctx, false, 60, new int[][] {{3, 0, 0}, {6, 0, 0}, {11, 0, 0}}, null);
                SelfTest.check(r.activeRightAfter == false, "a shifted view was shown with Turbo off");
                SelfTest.check(r.buildsWhileMoving > 0, "plain Litematica did not rebuild at the move");
            });
        });

        tests.add("move/second-placement-no-ghost", () -> {
            // the built chunks would also hold the other placement, which must not move along
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                f.configure(true, 3, true, true);
                prepare(ctx, true);
                f.zonesEnabled(true, "A");
                f.awaitIdle("a second placement shown", f.chunksOf("A"), null);
                int[] o = origin(ctx);
                setOrigin(ctx, o[0] + 6, o[1], o[2]);
                SelfTest.check(ghostActive(ctx) == false, "a placement was moved with a shifted view although another placement is shown");
                settle(ctx, f.chunksAt(o[0] + 6, o[2]));
                String arrived = "";

                for (int tries = 0; tries < 200 && arrived.contains("gold_block") == false; tries++)
                {
                    arrived = stateAt(ctx, o[0] + 6 + Fixture.MARKER[0], o[1] + Fixture.MARKER[1], o[2] + Fixture.MARKER[2]);
                    Thread.sleep(50);
                }

                SelfTest.check(arrived.contains("gold_block"), "the marker block did not arrive: " + arrived);
            });
        });

        tests.add("move/hidden-placement-no-ghost", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                prepare(ctx, true);
                ctx.onMain(() -> Ctx.call(placement, "setRenderSchematic", false));
                SelfTest.equal(false, ctx.onMain(() -> Ctx.call(placement, "isRenderingEnabled")), "the placement's rendering switch");
                ctx.ticks(10);
                int[] o = origin(ctx);
                setOrigin(ctx, o[0] + 3, o[1], o[2]);
                SelfTest.check(ghostActive(ctx) == false, "a hidden placement was moved with a shifted view");
            });
        });

        tests.add("move/removing-ends-ghost", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                Run r = run(ctx, true, 0, new int[][] {{8, 0, 0}}, run -> {
                    SelfTest.check(ghostActive(ctx), "no shifted view during the move");
                    Object old = placement;
                    ctx.onMain(() -> {
                        Object manager = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
                        Ctx.call(manager, "removeSchematicPlacement", old);
                        return null;
                    });
                    SelfTest.check(ghostActive(ctx) == false, "the shifted view stays after the placement was removed");
                    run.removed = true;
                });
                SelfTest.check(r.markerNew.contains("gold_block") == false, "the removed placement still shows its marker block");
            });
        });

        tests.add("move/adding-ends-ghost", () -> {
            f = WorldTests.fixture(ctx);
            guarded(ctx, () -> {
                run(ctx, true, 0, new int[][] {{8, 0, 0}}, run -> {
                    SelfTest.check(ghostActive(ctx), "no shifted view during the move");
                    f.zonesEnabled(true);
                    ctx.ticks(2);
                    SelfTest.check(ghostActive(ctx) == false, "the shifted view stays although another placement came in");
                });
            });
        });

        // {name, setter, constant}, the step before the turn, where the marker block ends up from the turned origin. A placement
        // turns around its origin: each step makes the turned placement end on the blocks of the plain move by 8, in front
        // of the player whatever the window's shape (turned after a move by 8 it reached two chunks to the side or behind)
        Object[][] turns = {{new String[] {"rotated", "setRotation", "CLOCKWISE_90"}, new int[] {39, 0, 0}, new int[] {-3, 2, 3}},
                            {new String[] {"mirrored", "setMirror", "LEFT_RIGHT"}, new int[] {8, 0, 31}, new int[] {3, 2, -3}}};

        for (Object[] t : turns)
        {
            String[] turn = (String[]) t[0];
            int[] step = (int[]) t[1];
            int[] marker = (int[]) t[2];
            tests.add("move/" + turn[0] + "-while-moving-same-as-stock", () -> {
                f = WorldTests.fixture(ctx);
                guarded(ctx, () -> {
                    inView(ctx, turn[1], turn[2], step, marker);
                    Extra turnIt = run -> {
                        PlaceTests.turn(ctx, placement, turn[1], turn[2]);
                        ctx.ticks(2);
                    };
                    Run turbo = run(ctx, true, 0, new int[][] {step}, turnIt);
                    Run stock = run(ctx, false, 0, new int[][] {step}, turnIt);
                    SelfTest.check(turbo.activeAfterSettle == false, "the shifted view stays after the placement was " + turn[0]);
                    sameFinal(stock, turbo);
                });
            });
        }
    }
}
