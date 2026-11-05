package com.litematicaturbo.selftest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * place/: what a player gets when a placement is added to the world (Turbo copies it into the schematic world faster).
 * The blocks that land in the schematic world are the same as plain Litematica puts there, also when the placement is
 * rotated or mirrored and when a second placement is laid over the first, and the rotation really turns the build.
 */
final class PlaceTests
{
    private static final int W = 100;

    private static Fixture f;
    private static Object placement;
    private static Object placement2;
    private static final Map<String, List<String>> DUMPS = new HashMap<>();

    private PlaceTests()
    {
    }

    private static void remove(Ctx ctx, Object p) throws Exception
    {
        if (p != null)
        {
            ctx.onMain(() -> {
                Object manager = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
                Ctx.call(manager, "removeSchematicPlacement", p);
                return null;
            });
        }
    }

    private static int[] origin(Ctx ctx, Object p) throws Exception
    {
        Object pos = ctx.onMain(() -> Ctx.call(p, "getOrigin"));
        return new int[] {(Integer) Ctx.call(pos, "getX"), (Integer) Ctx.call(pos, "getY"), (Integer) Ctx.call(pos, "getZ")};
    }

    /** Calls placement.setter(enum constant of that name, null...), whatever the version calls the enum. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void turn(Ctx ctx, Object p, String setter, String constant) throws Exception
    {
        ctx.onMain(() -> {
            for (Method m : p.getClass().getMethods())
            {
                if (m.getName().equals(setter) && m.getParameterCount() >= 1 && m.getParameterTypes()[0].isEnum())
                {
                    Object[] args = new Object[m.getParameterCount()];
                    args[0] = Enum.valueOf((Class) m.getParameterTypes()[0], constant);
                    m.invoke(p, args);
                    return null;
                }
            }

            throw new NoSuchMethodException("SchematicPlacement." + setter + "(enum, ...)");
        });
    }

    /** Every block state in the box around the origin, as text; waits until two looks one after the other agree. */
    private static List<String> dump(Ctx ctx, int[] o) throws Exception
    {
        List<String> last = null;
        int agree = 0;

        for (int tries = 0; tries < 120; tries++)
        {
            List<String> now = ctx.onMain(() -> {
                List<String> out = new ArrayList<>();

                for (int y = 0; y < 6; y++)
                {
                    for (int z = -34; z < 66; z++)
                    {
                        for (int x = -34; x < 66; x++)
                        {
                            out.add(String.valueOf(Ctx.call(f.schematicWorld(), "getBlockState", f.game.blockPos(o[0] + x, o[1] + y, o[2] + z))));
                        }
                    }
                }

                return out;
            });

            agree = last != null && last.equals(now) && air(now) < now.size() ? agree + 1 : 0;

            if (agree >= 3)
            {
                return now;
            }

            last = now;
            Thread.sleep(250);
        }

        throw new SelfTest.Fail("the placed blocks never settled in the schematic world");
    }

    static int air(List<String> dump)
    {
        int n = 0;

        for (String s : dump)
        {
            if (s.contains("air"))
            {
                ++n;
            }
        }

        return n;
    }

    private static int count(List<String> dump, String text)
    {
        int n = 0;

        for (String s : dump)
        {
            if (s.contains(text))
            {
                ++n;
            }
        }

        return n;
    }

    private static int firstIndex(List<String> a, List<String> b)
    {
        for (int i = 0; i < a.size(); i++)
        {
            if (a.get(i).equals(b.get(i)) == false)
            {
                return i;
            }
        }

        return 0;
    }

    static String firstDifference(List<String> a, List<String> b)
    {
        if (a.size() != b.size())
        {
            return "different sizes";
        }

        for (int i = 0; i < a.size(); i++)
        {
            if (a.get(i).equals(b.get(i)) == false)
            {
                return "first at (" + (i % W - 34) + "," + (i / (W * W)) + "," + ((i / W) % W - 34) + ") from the origin: '" + a.get(i) + "' vs '" + b.get(i) + "'";
            }
        }

        return "none";
    }

    /** Index of the one gold block, -1 for none, -2 for several. */
    private static int markerIndex(List<String> dump)
    {
        int found = -1;

        for (int i = 0; i < dump.size(); i++)
        {
            if (dump.get(i).contains("gold_block"))
            {
                if (found >= 0)
                {
                    return -2;
                }

                found = i;
            }
        }

        return found;
    }

    /**
     * What the schematic world holds after a fresh lone placement (given turn and mirror, optionally a second placement
     * laid over it), with Turbo on or off. Made once per label.
     */
    private static List<String> placed(Ctx ctx, String label, boolean turbo, String rotation, String mirror, boolean second) throws Exception
    {
        return placed(ctx, label, turbo, rotation, mirror, second, "P1");
    }

    private static List<String> placed(Ctx ctx, String label, boolean turbo, String rotation, String mirror, boolean second, String first) throws Exception
    {
        return placed(ctx, label, turbo, rotation, mirror, second, first, first);
    }

    /** What a fresh lone placement of the file {@code first}, holding the blocks of {@code seed}, leaves in the schematic world. */
    static List<String> placedFile(Ctx ctx, String label, String first, String seed) throws Exception
    {
        return placed(ctx, label, true, null, null, false, first, seed);
    }

    private static List<String> placed(Ctx ctx, String label, boolean turbo, String rotation, String mirror, boolean second, String first, String seed) throws Exception
    {
        String key = label + (turbo ? "/turbo" : "/stock");
        List<String> existing = DUMPS.get(key);

        if (existing != null)
        {
            return existing;
        }

        f.configure(turbo, 3, true, true);
        remove(ctx, placement);
        remove(ctx, placement2);
        placement = null;
        placement2 = null;
        f.awaitQuiet("the schematic world settled before placing");
        placement = f.placeLone(first, seed);
        int[] o = origin(ctx, placement);

        if (rotation != null)
        {
            turn(ctx, placement, "setRotation", rotation);
        }

        if (mirror != null)
        {
            turn(ctx, placement, "setMirror", mirror);
        }

        if (second)
        {
            // laid over the first once that is done: two placement jobs running at the same time decide the shared cells
            // by whichever finishes last, on plain Litematica as well
            f.awaitQuiet("the first placement is in the schematic world");
            placement2 = f.placeLone("P2");
        }

        f.awaitQuiet("both placements are in the schematic world");
        List<String> d = settledDump(ctx, o);
        DUMPS.put(key, d);
        return d;
    }

    /**
     * The schematic world once it stopped changing. Litematica's background jobs can pause longer than awaitQuiet's
     * second on a busy box (26.2: plain Litematica read 202 of 379 blocks short), so two reads apart must match; a block
     * that is really missing stays missing and still fails the comparison.
     */
    private static List<String> settledDump(Ctx ctx, int[] o) throws Exception
    {
        List<String> d = dump(ctx, o);

        for (int i = 0; i < 20; i++)
        {
            Thread.sleep(1500);
            f.awaitQuiet("the placement is in the schematic world");
            List<String> again = dump(ctx, o);

            if (again.equals(d))
            {
                return d;
            }

            d = again;
        }

        throw new SelfTest.Fail("the schematic world was still changing after a minute");
    }

    interface Body
    {
        void run() throws Exception;
    }

    static void guarded(Ctx ctx, Body body) throws Exception
    {
        f = WorldTests.fixture(ctx);
        f.zonesEnabled(false);

        try
        {
            body.run();
        }
        finally
        {
            try
            {
                remove(ctx, placement);
                remove(ctx, placement2);
                placement = null;
                placement2 = null;
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

    private static void sameAsStock(Ctx ctx, SelfTest.Tests tests, String name, String rotation, String mirror, boolean second)
    {
        tests.add("place/" + name, () -> guarded(ctx, () -> {
            List<String> stock = placed(ctx, name, false, rotation, mirror, second);
            List<String> turbo = placed(ctx, name, true, rotation, mirror, second);
            SelfTest.check(stock.size() - air(stock) > 100, "the placement put almost nothing into the world (" + (stock.size() - air(stock)) + " blocks)");
            if (stock.equals(turbo) == false)
            {
                int differing = 0;

                for (int i = 0; i < stock.size(); i++)
                {
                    differing += stock.get(i).equals(turbo.get(i)) ? 0 : 1;
                }

                List<String> first = DUMPS.get("same-as-stock-plain/stock");
                String note = first == null ? "" : "; the first placement alone gives '" + first.get(firstIndex(stock, turbo)) + "' there";
                throw new SelfTest.Fail("the placed blocks differ from plain Litematica: " + firstDifference(stock, turbo) + " (" + differing + " cells differ, plain " + (stock.size() - air(stock)) + " blocks, Turbo " + (turbo.size() - air(turbo)) + ")" + note);
            }
        }));
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        sameAsStock(ctx, tests, "same-as-stock-plain", null, null, false);
        sameAsStock(ctx, tests, "same-as-stock-rotated-90", "CLOCKWISE_90", null, false);
        sameAsStock(ctx, tests, "same-as-stock-rotated-180", "CLOCKWISE_180", null, false);
        sameAsStock(ctx, tests, "same-as-stock-rotated-270", "COUNTERCLOCKWISE_90", null, false);
        sameAsStock(ctx, tests, "same-as-stock-mirrored", null, "LEFT_RIGHT", false);
        sameAsStock(ctx, tests, "same-as-stock-rotated-and-mirrored", "CLOCKWISE_90", "FRONT_BACK", false);
        // Two placements over the same cells: which one ends up in a cell depends on the order the game works through them
        // (not fixed, plain Litematica included), so the promise is that every cell holds the block of one of the two.
        for (boolean turbo : new boolean[] {false, true})
        {
            tests.add("place/two-placements-overlapping-" + (turbo ? "turbo" : "plain"), () -> guarded(ctx, () -> {
                List<String> a = placed(ctx, "same-as-stock-plain", false, null, null, false);
                List<String> b = placed(ctx, "second-alone", false, null, null, false, "P2");
                List<String> both = placed(ctx, "two-overlapping", turbo, null, null, true);
                int fromFirst = 0, fromSecond = 0;

                for (int i = 0; i < both.size(); i++)
                {
                    boolean isFirst = both.get(i).equals(a.get(i)), isSecond = both.get(i).equals(b.get(i));
                    SelfTest.check(isFirst || isSecond, "a cell holds neither placement's block: first at (" + (i % W - 34) + "," + (i / (W * W)) + "," + ((i / W) % W - 34) + ") from the origin is '" + both.get(i) + "', the placements have '" + a.get(i) + "' and '" + b.get(i) + "'");
                    fromFirst += isFirst && isSecond == false ? 1 : 0;
                    fromSecond += isSecond && isFirst == false ? 1 : 0;
                }

                SelfTest.check(both.size() - air(both) > 100, "the placements put almost nothing into the world");
                SelfTest.check(fromFirst + fromSecond > 0, "the two placements do not differ in any cell, the test proves nothing");
            }));
        }

        tests.add("place/marker-block-arrives", () -> guarded(ctx, () -> {
            List<String> d = placed(ctx, "same-as-stock-plain", true, null, null, false);
            int i = markerIndex(d);
            SelfTest.check(i >= 0, "the schematic's gold marker block is not exactly once in the world (" + count(d, "gold_block") + " found)");
            int x = i % W - 34, z = (i / W) % W - 34, y = i / (W * W);
            SelfTest.check(x == Fixture.MARKER[0] && y == Fixture.MARKER[1] && z == Fixture.MARKER[2],
                           "the marker block is at " + x + "," + y + "," + z + " from the origin, the schematic has it at 3,2,3");
        }));

        tests.add("place/rotation-turns-the-build", () -> guarded(ctx, () -> {
            List<String> plain = placed(ctx, "same-as-stock-plain", true, null, null, false);
            List<String> turned = placed(ctx, "same-as-stock-rotated-90", true, "CLOCKWISE_90", null, false);
            SelfTest.check(markerIndex(turned) >= 0, "the marker block is not exactly once in the rotated build");
            SelfTest.check(markerIndex(turned) != markerIndex(plain), "the marker block did not move when the placement was rotated");
            SelfTest.check(plain.equals(turned) == false, "the rotated build is the same as the unrotated one");
        }));

        tests.add("place/mirror-flips-the-build", () -> guarded(ctx, () -> {
            List<String> plain = placed(ctx, "same-as-stock-plain", true, null, null, false);
            List<String> mirrored = placed(ctx, "same-as-stock-mirrored", true, null, "LEFT_RIGHT", false);
            SelfTest.check(markerIndex(mirrored) >= 0 && markerIndex(mirrored) != markerIndex(plain), "the marker block did not move when the placement was mirrored");
        }));

        copyFailure(ctx, tests);
    }

    /**
     * A call of the fast copy that throws (here: bad data, a null block state) switches the copy path off for the session
     * instead of taking the game down: the player gets one chat line, and placements are then copied the plain Litematica way,
     * with exactly plain Litematica's result. Only where the fast copy exists (up to 1.21.10).
     */
    private static void copyFailure(Ctx ctx, SelfTest.Tests tests)
    {
        Class<?> copy;

        try
        {
            copy = ctx.turbo("SchematicCopy");
        }
        catch (ClassNotFoundException e)
        {
            return;
        }

        tests.add("place/copy-failure-switches-off", () -> guarded(ctx, () -> {
            // a label of its own: a cached dump would leave no placement to work on
            List<String> stock = placed(ctx, "copy-failure-stock", false, null, null, false);
            f.configure(true, 3, true, true);
            Object originPos = ctx.onMain(() -> Ctx.call(placement, "getOrigin"));
            Object chunkPos = ctx.cls("net.minecraft.util.math.ChunkPos").getConstructor(originPos.getClass()).newInstance(originPos);

            try
            {
                ctx.onMain(() -> {
                    Object world = f.schematicWorld();
                    Ctx.call(copy, "begin", world, chunkPos);

                    try
                    {
                        Ctx.call(copy, "setBlockState", world, originPos, null, 3);
                    }
                    catch (RuntimeException e)
                    {
                        // plain Litematica's own answer to a null block state, after Turbo handed the call over
                    }
                    finally
                    {
                        Ctx.call(copy, "end");
                    }

                    return null;
                });
                SelfTest.check(Ctx.getStatic(copy, "failure") != null, "the failing copy call did not switch the copy path off");
                ctx.ticks(4);
                String chat = SafetyTests.chatText(ctx);
                SelfTest.check(chat.contains("litematica_turbo.message.copy_off"), "no chat line says the fast copy is off:\n" + chat);
                String lang = AuditTests.resource(copy.getClassLoader(), "assets/litematica_turbo/lang/en_us.json");
                SelfTest.check(lang != null && lang.contains("\"litematica_turbo.message.copy_off\": \"[Litematica Turbo] Fast placement copying hit an error (%s)"),
                               "en_us.json has no English text for the chat line litematica_turbo.message.copy_off");
                SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");
                List<String> off = placed(ctx, "copy-switched-off", true, null, null, false);
                SelfTest.check(stock.equals(off), "the placed blocks with the copy path switched off differ from plain Litematica: " + firstDifference(stock, off));
            }
            finally
            {
                Ctx.setStatic(copy, "failure", null);
                Ctx.setStatic(copy, "warned", false);
            }
        }));
    }
}
