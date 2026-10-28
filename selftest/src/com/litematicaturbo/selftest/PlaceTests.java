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

    private static int air(List<String> dump)
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

    private static String firstDifference(List<String> a, List<String> b)
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
        placement = f.placeLone("P1");
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
            placement2 = f.placeLone("P2");
        }

        f.awaitQuiet("both placements are in the schematic world");
        List<String> d = dump(ctx, o);
        DUMPS.put(key, d);
        return d;
    }

    private interface Body
    {
        void run() throws Exception;
    }

    private static void guarded(Ctx ctx, Body body) throws Exception
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
            SelfTest.check(stock.equals(turbo), "the placed blocks differ from plain Litematica: " + firstDifference(stock, turbo));
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
        sameAsStock(ctx, tests, "same-as-stock-two-placements-overlapping", null, null, true);

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
    }
}
