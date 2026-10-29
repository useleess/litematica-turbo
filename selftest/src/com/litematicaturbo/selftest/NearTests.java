package com.litematicaturbo.selftest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * near/: plain Litematica rebuilds the chunks within 32 blocks of the player right away on the render thread (the
 * stutter when standing inside a schematic); Turbo hands them to the build threads, with the same result. Tests:
 * the chunks next to the player are built with Turbo's near rebuilds on and off exactly like plain Litematica
 * builds them, and edits made in the schematic world next to the player (in the middle of a chunk, on a chunk
 * border, several at once, and taken back) show up in the rebuilt chunks, are the same as plain Litematica's
 * rebuild of the edited world, leave the other chunks alone, and with near rebuilds on cost the render thread
 * nothing.
 */
final class NearTests
{
    private static final String[] EDIT_BLOCKS = {"minecraft:stone", "minecraft:glass", "minecraft:oak_planks", "minecraft:water"};

    private NearTests()
    {
    }

    /** One edit scenario: the world edited next to the player and rebuilt by Turbo (threads, near rebuilds as given). */
    static final class Edit
    {
        Fixture.Capture base;
        Map<Long, Fixture.Sig> edited;
        Fixture.Capture stockAfter;
        Set<Long> changed = new LinkedHashSet<>();
        Set<Long> own = new LinkedHashSet<>();
        long builds;
        long mainThreadNanos;
    }

    private static final Map<String, Edit> EDITS = new HashMap<>();

    /** The near chunks of zone N (Litematica's own 32-block rule, with a margin). */
    private static Set<Long> nearChunks(Fixture f) throws Exception
    {
        Set<Long> near = new LinkedHashSet<>();
        StringBuilder seen = new StringBuilder();

        for (Long k : f.chunksOf("N"))
        {
            double d = f.nearDistanceSq(k);
            seen.append(' ').append(Fixture.keyX(k)).append(',').append(Fixture.keyZ(k)).append('=').append(Math.round(Math.sqrt(d)));

            if (d == d && d < 900)
            {
                near.add(k);
            }
        }

        if (near.isEmpty())
        {
            throw SelfTest.skip("no chunk of the test zone is within 32 blocks of the player (player at " + f.playerX + "," + f.playerY + "," + f.playerZ + ", zone N from y " + f.nearY + ", renderer y " + f.lastOriginY + ", distances" + seen + ")");
        }

        return near;
    }

    private static Set<Long> farChunks(Fixture f)
    {
        return f.chunksOf("A", "B", "S", "T", "F", "E");
    }

    private static String stateText(Ctx ctx, Fixture f, Object pos) throws Exception
    {
        return String.valueOf(ctx.onMain(() -> Ctx.call(f.schematicWorld(), "getBlockState", pos)));
    }

    /** An edit position: block (x, y, z) of the schematic world; chunkDz 0 = the first row of zone N, the one right in front of the player. */
    private static int[] at(Fixture f, int chunkDx, int chunkDz, int inChunkX, int inChunkZ, int dy)
    {
        return new int[] {(f.playerChunkX + chunkDx) * 16 + inChunkX, f.nearY + 2 + dy, (f.playerChunkZ + 1 + chunkDz) * 16 + inChunkZ};
    }

    /**
     * Edits the positions (each to the first of EDIT_BLOCKS it does not already hold), waits for Turbo's rebuild,
     * describes the chunks, then lets plain Litematica rebuild the same edited world, and puts the world back.
     */
    private static Edit edit(Ctx ctx, String id, boolean asyncNear, int[]... positions) throws Exception
    {
        Edit existing = EDITS.get(id);

        if (existing != null)
        {
            return existing;
        }

        Fixture f = WorldTests.fixture(ctx);
        Set<Long> near = nearChunks(f);
        Edit e = new Edit();
        e.base = f.captureNew("near-base-" + id, true, 3, true, asyncNear);
        Object world = f.schematicWorld();
        List<Object> positionsObj = new ArrayList<>();
        List<String> before = new ArrayList<>();
        List<Object> newStates = new ArrayList<>();

        for (int[] p : positions)
        {
            Object pos = f.game.blockPos(p[0], p[1], p[2]);
            String current = stateText(ctx, f, pos);
            Object state = null;

            for (String name : EDIT_BLOCKS)
            {
                Object candidate = f.stateNamed(name);

                if (candidate != null && String.valueOf(candidate).equals(current) == false)
                {
                    state = candidate;
                    break;
                }
            }

            SelfTest.check(state != null, "no block to edit with");
            positionsObj.add(pos);
            before.add(current);
            newStates.add(state);
            Long own = Fixture.key(Math.floorDiv(p[0], 16), Math.floorDiv(p[2], 16));
            SelfTest.check(near.contains(own), "the edited chunk is not one of the near chunks");
            e.own.add(own);
        }

        Map<Long, Object> dataBefore = f.dataNow(e.own);
        long builds = f.stats("totalBuilds");
        long main = f.stats("totalMainThreadNanos");
        ctx.onMain(() -> {
            for (int i = 0; i < positionsObj.size(); i++)
            {
                setBlock(ctx, f, world, positionsObj.get(i), newStates.get(i));
            }

            return null;
        });

        try
        {
            f.awaitIdle("edit " + id, f.allChunks(), dataBefore);
            e.builds = f.stats("totalBuilds") - builds;
            e.mainThreadNanos = f.stats("totalMainThreadNanos") - main;
            e.edited = f.describeAll(f.allChunks());

            // the rebuilt build can be handed over a moment after the idle look: wait (up to 4 s) until each edited chunk shows its edit
            for (int tries = 0; tries < 40; tries++)
            {
                boolean allChanged = true;

                for (Long own : e.own)
                {
                    allChanged &= e.edited.get(own).lines.equals(e.base.sigs.get(own).lines) == false;
                }

                if (allChanged)
                {
                    break;
                }

                Thread.sleep(100);
                e.edited = f.describeAll(f.allChunks());
            }

            for (Long k : f.allChunks())
            {
                if (e.edited.get(k).lines.equals(e.base.sigs.get(k).lines) == false)
                {
                    e.changed.add(k);
                }
            }

            e.stockAfter = f.captureNew("near-stock-" + id, false, 0, true, true);
        }
        finally
        {
            // the world back as it was, for every later capture
            ctx.onMain(() -> {
                for (int i = 0; i < positionsObj.size(); i++)
                {
                    Object original = null;

                    for (Object s : f.statesForText(before.get(i)))
                    {
                        original = s;
                    }

                    setBlock(ctx, f, world, positionsObj.get(i), original);
                }

                return null;
            });
        }

        EDITS.put(id, e);
        return e;
    }

    private static void setBlock(Ctx ctx, Fixture f, Object world, Object pos, Object state) throws Exception
    {
        Game.callAny(world, new String[] {"setBlockState", "setBlock"}, pos, state, 3);
        Ctx.call(Ctx.getStatic(ctx.cls("fi.dy.masa.litematica.util.SchematicWorldRefresher"), "INSTANCE"), "markSchematicChunkForRenderUpdate", pos);
    }

    private static int[] center(Fixture f)
    {
        return at(f, 0, 0, 8, 8, 0);
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("near/zones-are-near-and-far", () -> {
            Fixture f = WorldTests.fixture(ctx);
            WorldTests.turbo(f);
            nearChunks(f);

            for (Long k : farChunks(f))
            {
                double d = f.nearDistanceSq(k);

                if (d != d)
                {
                    continue; // a chunk without a block may have no renderer at all: nothing near about it
                }

                SelfTest.check(d >= 1100, "chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k) + " of a far zone is only " + Math.sqrt(d) + " blocks from the player");
            }
        });

        tests.add("near/built-same-as-stock", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Set<Long> near = nearChunks(f);
            WorldTests.hasGeometry(f, WorldTests.stock(f), near, "near chunks");
            WorldTests.same(WorldTests.stock(f), WorldTests.turbo(f), near, "near chunks built in the background");
        });

        tests.add("near/async-off-same", () -> {
            Fixture f = WorldTests.fixture(ctx);
            WorldTests.same(WorldTests.stock(f), WorldTests.noAsync(f), nearChunks(f), "near chunks built on the render thread by Turbo");
        });

        tests.add("near/far-chunks-same", () -> {
            Fixture f = WorldTests.fixture(ctx);
            WorldTests.same(WorldTests.stock(f), WorldTests.noAsync(f), farChunks(f), "far chunks with near rebuilds off");
            WorldTests.same(WorldTests.stock(f), WorldTests.turbo(f), farChunks(f), "far chunks with near rebuilds on");
        });

        tests.add("near/edit-shows-up", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Edit e = edit(ctx, "center", true, center(f));

            for (Long k : e.own)
            {
                SelfTest.check(e.changed.contains(k), "the edit next to the player did not change the rebuilt chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k)
                               + " (lines before " + e.base.sigs.get(k).lines.size() + ", after " + e.edited.get(k).lines.size() + ", first diff " + e.base.sigs.get(k).diff(e.edited.get(k)) + ")");
            }
        });

        tests.add("near/edit-same-as-stock", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Edit e = edit(ctx, "center", true, center(f));
            Fixture.Capture background = new Fixture.Capture("turbo-after-edit");
            background.sigs.putAll(e.edited);
            WorldTests.same(e.stockAfter, background, f.allChunks(), "edit rebuilt in the background vs plain Litematica");
        });

        tests.add("near/edit-leaves-other-chunks-alone", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Edit e = edit(ctx, "center", true, center(f));

            for (Long k : f.allChunks())
            {
                if (e.own.contains(k) == false)
                {
                    SelfTest.check(e.changed.contains(k) == false, "chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k) + " changed by an edit in another chunk");
                }
            }
        });

        tests.add("near/edit-border-both-chunks", () -> {
            // the block on the east border of the player's chunk: its neighbour's faces change too
            Fixture f = WorldTests.fixture(ctx);
            Edit e = edit(ctx, "border", true, at(f, 0, 0, 15, 8, 1));
            Fixture.Capture background = new Fixture.Capture("turbo-after-border-edit");
            background.sigs.putAll(e.edited);
            WorldTests.same(e.stockAfter, background, f.allChunks(), "border edit rebuilt in the background vs plain Litematica");

            for (Long k : e.own)
            {
                SelfTest.check(e.changed.contains(k), "the border edit did not change its own chunk");
            }
        });

        tests.add("near/many-edits-same-as-stock", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Edit e = edit(ctx, "many", true, at(f, 0, 0, 3, 3, 0), at(f, 1, 0, 5, 9, 1), at(f, -1, 0, 12, 4, 2), at(f, -1, 0, 6, 6, 0), at(f, 1, 0, 2, 13, 1));
            Fixture.Capture background = new Fixture.Capture("turbo-after-edits");
            background.sigs.putAll(e.edited);
            WorldTests.same(e.stockAfter, background, f.allChunks(), "five edits rebuilt in the background vs plain Litematica");
            SelfTest.check(e.changed.containsAll(e.own), "an edited chunk was not changed by its edit");
        });

        tests.add("near/edit-removed-restores", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Set<Long> near = nearChunks(f);
            Fixture.Capture base = f.captureNew("near-restore-base", true, 3, true, true);
            int[] p = center(f);
            Object pos = f.game.blockPos(p[0], p[1], p[2]);
            String before = stateText(ctx, f, pos);
            Object edited = null;

            for (String name : EDIT_BLOCKS)
            {
                Object candidate = f.stateNamed(name);

                if (candidate != null && String.valueOf(candidate).equals(before) == false)
                {
                    edited = candidate;
                    break;
                }
            }

            Object original = f.statesForText(before).get(0);
            Object editedState = edited;
            Long own = Fixture.key(Math.floorDiv(p[0], 16), Math.floorDiv(p[2], 16));
            SelfTest.check(near.contains(own), "the edited chunk is not near");
            Set<Long> one = new LinkedHashSet<>();
            one.add(own);
            Map<Long, Object> data = f.dataNow(one);
            ctx.onMain(() -> {
                setBlock(ctx, f, f.schematicWorld(), pos, editedState);
                return null;
            });
            f.awaitIdle("edit", f.allChunks(), data);
            Map<Long, Object> data2 = f.dataNow(one);
            ctx.onMain(() -> {
                setBlock(ctx, f, f.schematicWorld(), pos, original);
                return null;
            });
            f.awaitIdle("edit taken back", f.allChunks(), data2);
            Fixture.Capture back = new Fixture.Capture("turbo-after-undo");
            back.sigs.putAll(f.describeAll(f.allChunks()));
            WorldTests.same(base, back, f.allChunks(), "world after an edit and its undo vs before");
        });

        tests.add("near/rebuild-off-render-thread", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Edit e = edit(ctx, "center", true, center(f));

            if (e.base.threads <= 0)
            {
                throw SelfTest.skip("no build threads were started");
            }

            SelfTest.check(e.builds >= 1, "the edit caused no build");
            SelfTest.check(e.mainThreadNanos < 2_000_000L, "the edit next to the player was built on the render thread (" + e.mainThreadNanos / 1_000 + " us)");
        });

        tests.add("near/async-off-builds-on-render-thread", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Edit e = edit(ctx, "center-sync", false, center(f));
            SelfTest.check(e.builds >= 1, "the edit caused no build");
            SelfTest.check(e.mainThreadNanos > 0, "with near rebuilds off, the edit next to the player was not built on the render thread (the option does nothing)");
            Fixture.Capture background = new Fixture.Capture("turbo-sync-after-edit");
            background.sigs.putAll(e.edited);
            WorldTests.same(e.stockAfter, background, f.allChunks(), "edit rebuilt on the render thread vs plain Litematica");
        });
    }
}
