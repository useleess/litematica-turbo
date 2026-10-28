package com.litematicaturbo.selftest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * compat/: Litematica's own display options keep working with Turbo. With a Litematica option changed (translucent
 * blocks, fluids) the schematic Turbo builds is the one plain Litematica builds, the option really changes
 * what is built (so the comparison is not about nothing), and Turbo's own work never changes Litematica's options.
 */
final class CompatTests
{
    private CompatTests()
    {
    }

    private static Object layerRange(Ctx ctx) throws Exception
    {
        return Ctx.call(ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getRenderLayerRange");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setMode(Ctx ctx, String mode) throws Exception
    {
        ctx.onMain(() -> {
            Object range = layerRange(ctx);

            for (Method m : range.getClass().getMethods())
            {
                if (m.getName().equals("setLayerMode") && m.getParameterCount() >= 1 && m.getParameterTypes()[0].isEnum())
                {
                    Object[] args = new Object[m.getParameterCount()];
                    args[0] = Enum.valueOf((Class) m.getParameterTypes()[0], mode);

                    if (args.length > 1 && m.getParameterTypes()[1] == boolean.class)
                    {
                        args[1] = Boolean.TRUE;
                    }

                    m.invoke(range, args);
                    return null;
                }
            }

            throw new NoSuchMethodException("LayerRange.setLayerMode(enum)");
        });
    }

    private static void setLayer(Ctx ctx, int y) throws Exception
    {
        ctx.onMain(() -> {
            Object range = layerRange(ctx);

            for (Method m : range.getClass().getMethods())
            {
                if (m.getName().equals("setLayerSingle") && m.getParameterCount() >= 1 && m.getParameterTypes()[0] == int.class)
                {
                    Object[] args = new Object[m.getParameterCount()];
                    args[0] = y;

                    for (int i = 1; i < args.length; i++)
                    {
                        args[i] = m.getParameterTypes()[i] == boolean.class ? (Object) Boolean.TRUE : null;
                    }

                    m.invoke(range, args);
                    return null;
                }
            }

            throw new NoSuchMethodException("LayerRange.setLayerSingle(int)");
        });
    }

    private interface Stage
    {
        Fixture.Capture run() throws Exception;
    }

    /** Names who was building when the game threw. */
    private static Fixture.Capture staged(String who, Stage stage) throws Exception
    {
        try
        {
            return stage.run();
        }
        catch (SelfTest.Fail | SelfTest.Skip e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new SelfTest.Fail("while " + who + " built: " + e);
        }
    }

    private static void layerScenario(Ctx ctx, SelfTest.Tests tests, String name, String mode)
    {
        tests.add("compat/layer-" + name + "-same-as-stock", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture full = WorldTests.turbo(f);
            int y = f.nearY + 2;

            try
            {
                setMode(ctx, mode);
                setLayer(ctx, y);
                Fixture.Capture stock = staged("plain Litematica", () -> f.captureNew("layer-" + name + "-stock", false, 0, true, true));
                Fixture.Capture turbo = staged("Turbo", () -> f.captureNew("layer-" + name + "-turbo", true, 3, true, true));
                WorldTests.same(stock, turbo, f.allChunks(), "layer view '" + name + "'");
                int differing = 0;

                for (Long k : f.allChunks())
                {
                    if (turbo.sigs.get(k).lines.equals(full.sigs.get(k).lines) == false)
                    {
                        ++differing;
                    }
                }

                SelfTest.check(differing > 0, "the layer view '" + name + "' changed nothing that is built, so the comparison proves nothing");
            }
            finally
            {
                setMode(ctx, "ALL");
                f.configure(true, 3, true, true);
                ctx.ticks(40);
            }
        });
    }

    private static void optionScenario(Ctx ctx, SelfTest.Tests tests, String test, String option)
    {
        tests.add("compat/" + test + "-same-as-stock", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture before = WorldTests.turbo(f);
            Object opt;

            try
            {
                opt = Ctx.getStatic(ctx.cls("fi.dy.masa.litematica.config.Configs$Visuals"), option);
            }
            catch (ReflectiveOperationException e)
            {
                throw SelfTest.skip("this Litematica has no option " + option);
            }

            boolean was = (Boolean) Ctx.call(opt, "getBooleanValue");

            try
            {
                Ctx.call(opt, "setBooleanValue", was == false);
                Fixture.Capture stock = f.captureNew(test + "-stock", false, 0, true, true);
                Fixture.Capture turbo = f.captureNew(test + "-turbo", true, 3, true, true);
                WorldTests.same(stock, turbo, f.allChunks(), "with " + option + " = " + (was == false));
                int differing = 0;

                for (Long k : f.allChunks())
                {
                    if (turbo.sigs.get(k).lines.equals(before.sigs.get(k).lines) == false)
                    {
                        ++differing;
                    }
                }

                if (differing == 0)
                {
                    throw SelfTest.skip(option + " changes nothing in the test schematics here, so the comparison proves nothing");
                }
            }
            finally
            {
                Ctx.call(opt, "setBooleanValue", was);
                f.capture("after-" + test, true, 3, true, true);
            }
        });
    }

    /** Name -> value of Litematica's Visuals / Generic options. */
    private static Map<String, String> litematicaOptions(Ctx ctx) throws Exception
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (String holder : new String[] {"Configs$Visuals", "Configs$Generic", "Configs$Colors", "Configs$InfoOverlays"})
        {
            Class<?> c;

            try
            {
                c = ctx.cls("fi.dy.masa.litematica.config." + holder);
            }
            catch (RuntimeException | ReflectiveOperationException e)
            {
                continue;
            }

            List<?> options = (List<?>) Ctx.getStatic(c, "OPTIONS");

            for (Object o : new ArrayList<Object>(options))
            {
                String value;

                try
                {
                    value = String.valueOf(Ctx.call(o, "getStringValue"));
                }
                catch (NoSuchMethodException e)
                {
                    value = String.valueOf(Ctx.call(o, "getStrings"));
                }

                out.put(holder + "." + Ctx.call(o, "getName"), value);
            }
        }

        return out;
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        layerScenario(ctx, tests, "single", "SINGLE_LAYER");
        layerScenario(ctx, tests, "all-above", "ALL_ABOVE");
        layerScenario(ctx, tests, "all-below", "ALL_BELOW");
        optionScenario(ctx, tests, "translucent-option", "RENDER_BLOCKS_AS_TRANSLUCENT");
        optionScenario(ctx, tests, "fluids-option", "ENABLE_SCHEMATIC_FLUIDS");

        tests.add("compat/litematica-options-untouched", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Map<String, String> before = ctx.onMain(() -> litematicaOptions(ctx));
            SelfTest.check(before.size() > 20, "found only " + before.size() + " Litematica options to watch");
            f.configure(true, 2, false, false);
            f.capture("compat-options-a", true, 2, false, false);
            f.capture("compat-options-b", false, 0, true, true);
            f.configure(true, 3, true, true);
            Map<String, String> after = ctx.onMain(() -> litematicaOptions(ctx));
            SelfTest.equal(before, after, "Litematica's options after Turbo built with other settings");
        });
    }
}
