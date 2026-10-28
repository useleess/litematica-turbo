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

    private static Fixture f;
    private static Object placement;

    private static boolean ghostActive(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> (Boolean) Ctx.call(ctx.turbo("GhostMove"), "isActive"));
    }

    /** Moves the placement to this origin the way the game's move command does. */
    private static void setOrigin(Ctx ctx, int x, int y, int z) throws Exception
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

    private static int[] origin(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> {
            Object o = Ctx.call(placement, "getOrigin");
            return new int[] {(Integer) Ctx.call(o, "getX"), (Integer) Ctx.call(o, "getY"), (Integer) Ctx.call(o, "getZ")};
        });
    }

    private static String stateAt(Ctx ctx, int x, int y, int z) throws Exception
    {
        return ctx.onMain(() -> String.valueOf(Ctx.call(f.schematicWorld(), "getBlockState", f.game.blockPos(x, y, z))));
    }

    /** A fresh lone placement at its starting place, all built, Turbo (or plain Litematica) configured. */
    private static int[] prepare(Ctx ctx, boolean turbo) throws Exception
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

    private static void settle(Ctx ctx, Set<Long> chunks) throws Exception
    {
        Set<Long> inView = new LinkedHashSet<>();

        // the renderers of a new placement's chunks appear within a few frames
        for (int tries = 0; tries < 400 && inView.isEmpty(); tries++)
        {
            inView.addAll(chunks);
            inView.retainAll(f.renderedKeys());
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
        Ctx.setField(config, "catchUpAuto", true);
        Ctx.setField(config, "catchUpBudgetMs", 8);
    }

    /**
     * One scenario: a fresh placement, the given steps ({dx, dy, dz} from the starting origin, one every stepMs),
     * then wait for everything to settle. extra runs right after the last step (on the test thread).
     */
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
        r.finalView.sigs.putAll(f.describeAll(keys));
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
        Run turbo = run(ctx, true, stepMs, steps, null);
        Run stock = run(ctx, false, stepMs, steps, null);
        sameFinal(stock, turbo);
        return new Run[] {stock, turbo};
    }

    private static void sameFinal(Run stock, Run turbo)
    {
        SelfTest.check(java.util.Arrays.equals(stock.finalOrigin, turbo.finalOrigin), "the placement ended at different places");
        Set<Long> common = new LinkedHashSet<>(stock.finalView.sigs.keySet());
        common.retainAll(turbo.finalView.sigs.keySet());
        SelfTest.check(common.size() >= 4, "only " + common.size() + " chunks in view in both runs");
        WorldTests.same(stock.finalView, turbo.finalView, common, "after the move, plain Litematica vs Turbo");
        SelfTest.equal(stock.markerNew, turbo.markerNew, "the marker block at the new place");
        SelfTest.equal(stock.markerOld, turbo.markerOld, "the block left at the old place");
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

    private interface Body
    {
        void run() throws Exception;
    }

    /** Puts the lone placement away and the standard zones back, whatever happens. */
    private static void guarded(Ctx ctx, Body body) throws Exception
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
    }
}
