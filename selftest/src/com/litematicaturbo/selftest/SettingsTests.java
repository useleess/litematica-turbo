package com.litematicaturbo.selftest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * settings/: the Turbo tab in Litematica's config menu. Every option saves (changed through its malilib option,
 * the way the tab changes it: TurboConfig follows at once and the file on disk has the new value), loads (a file
 * with the value, read back into TurboConfig and shown in the tab), and applies right away (the switches the
 * patches read change, renderers are reloaded / refreshed on the next tick). The tab itself: Litematica's menu
 * gets a Turbo button that shows exactly Turbo's options, while Litematica's own tabs stay as they were.
 * Every test puts the settings back the way it found them.
 */
final class SettingsTests
{
    /** Tab constant, TurboConfig field and file key, test value (never the default), type. */
    static final class Option
    {
        final String constant;
        final String key;
        final Object value;

        Option(String constant, String key, Object value)
        {
            this.constant = constant;
            this.key = key;
            this.value = value;
        }

        boolean isInt()
        {
            return this.value instanceof Integer;
        }
    }

    static final Option[] OPTIONS = {
        new Option("ENABLED", "enabled", false),
        new Option("BUILD_THREADS", "buildThreads", 3),
        new Option("SKIP_EMPTY_SECTIONS", "skipEmptySections", false),
        new Option("ASYNC_NEAR_REBUILDS", "asyncNearRebuilds", false),
        new Option("GHOST_MOVE", "ghostMove", false),
        new Option("GHOST_SETTLE_MS", "ghostSettleMs", 777),
        new Option("CATCH_UP_AUTO", "catchUpAuto", false),
        new Option("CATCH_UP_BUDGET_MS", "catchUpBudgetMs", 23),
        new Option("SHOW_STATS", "showStats", false),
        new Option("COMPACT_STORAGE", "compactStorage", false),
        new Option("BATCH_DRAW", "batchDraw", false),
        new Option("FAR_DETAIL", "farDetail", false),
        new Option("COMPLETE_COPY", "completeCopy", false),
    };

    private SettingsTests()
    {
    }

    /** The live settings: TurboConfig, its tab and its file, with save / restore. */
    static final class Live
    {
        final Ctx ctx;
        final Class<?> configClass;
        final Class<?> tab;

        Live(Ctx ctx) throws ClassNotFoundException
        {
            this.ctx = ctx;
            this.configClass = ctx.cls("com.litematicaturbo.core.TurboConfig");
            this.tab = ctx.turbo("TurboSettingsTab");
        }

        Object config() throws ReflectiveOperationException
        {
            return Ctx.call(this.configClass, "get");
        }

        Path dir() throws ReflectiveOperationException
        {
            return (Path) Ctx.getStatic(this.configClass, "configDir");
        }

        Path file() throws ReflectiveOperationException
        {
            return this.dir().resolve("litematica_turbo.properties");
        }

        String fileText() throws Exception
        {
            return new String(Files.readAllBytes(this.file()), StandardCharsets.UTF_8);
        }

        Object option(Option o) throws ReflectiveOperationException
        {
            return Ctx.getStatic(this.tab, o.constant);
        }

        Object shown(Option o) throws ReflectiveOperationException
        {
            return Ctx.call(this.option(o), o.isInt() ? "getIntegerValue" : "getBooleanValue");
        }

        /** Changes the option as the tab's widget does (on the render thread). */
        void setOption(Option o, Object value) throws Exception
        {
            this.ctx.onMain(() -> Ctx.call(this.option(o), o.isInt() ? "setIntegerValue" : "setBooleanValue", value));
        }

        /**
         * Changes the option and reads one of the tab's pending-work flags in the same render-thread task (the next
         * client tick applies and clears them).
         */
        boolean setOptionFlag(Option o, Object value, String flag) throws Exception
        {
            return this.ctx.onMain(() -> {
                Ctx.call(this.option(o), o.isInt() ? "setIntegerValue" : "setBooleanValue", value);
                return this.flag(flag);
            });
        }

        boolean flag(String name) throws ReflectiveOperationException
        {
            return (Boolean) Ctx.getStatic(this.tab, name);
        }

        /** Runs body, then puts config object, config dir, file and tab back as they were. */
        void restoring(SelfTest.Body body) throws Exception
        {
            Object config = this.config();
            Path dir = this.dir();
            byte[] file = Files.readAllBytes(this.file());
            Object[] values = new Object[OPTIONS.length];

            for (int i = 0; i < OPTIONS.length; i++)
            {
                values[i] = Ctx.getField(config, OPTIONS[i].key);
            }

            try
            {
                body.run();
            }
            finally
            {
                this.ctx.onMain(() -> {
                    Ctx.call(this.configClass, "set", config);
                    Ctx.setStatic(this.configClass, "configDir", dir);

                    for (int i = 0; i < OPTIONS.length; i++)
                    {
                        Ctx.call(this.option(OPTIONS[i]), OPTIONS[i].isInt() ? "setIntegerValue" : "setBooleanValue", values[i]);
                    }

                    Ctx.call(this.tab, "syncFromConfig");
                    return null;
                });
                Files.write(dir.resolve("litematica_turbo.properties"), file);
                this.ctx.ticks(3);
            }
        }
    }

    static void register(Ctx ctx, SelfTest.Tests tests) throws Exception
    {
        Live live = new Live(ctx);

        tests.add("settings/file-has-every-key", () -> {
            String text = live.fileText();
            Object c = live.config();

            for (Option o : OPTIONS)
            {
                SelfTest.check(text.contains("\n" + o.key + "=" + Ctx.getField(c, o.key) + "\n"),
                               "file line " + o.key + "=" + Ctx.getField(c, o.key) + " missing in:\n" + text);
            }
        });

        tests.add("settings/tab-shows-config", () -> {
            Object c = live.config();

            for (Option o : OPTIONS)
            {
                SelfTest.equal(Ctx.getField(c, o.key), live.shown(o), "tab option " + o.constant);
            }
        });

        for (Option o : OPTIONS)
        {
            tests.add("settings/save/" + o.key, () -> live.restoring(() -> {
                Object before = Ctx.getField(live.config(), o.key);
                SelfTest.check(before.equals(o.value) == false, o.key + " is already " + o.value);
                live.setOption(o, o.value);
                SelfTest.equal(o.value, live.shown(o), "tab option after the change");
                SelfTest.equal(o.value, Ctx.getField(live.config(), o.key), "TurboConfig." + o.key + " right after the change");
                String text = live.fileText();
                SelfTest.check(text.contains("\n" + o.key + "=" + o.value + "\n"), "file does not have " + o.key + "=" + o.value + ":\n" + text);

                for (Option other : OPTIONS)
                {
                    if (other != o)
                    {
                        Object now = Ctx.getField(live.config(), other.key);
                        SelfTest.check(text.contains("\n" + other.key + "=" + now + "\n"), "changing " + o.key + " lost " + other.key + "=" + now);
                    }
                }

                live.setOption(o, before);
                SelfTest.equal(before, Ctx.getField(live.config(), o.key), "TurboConfig." + o.key + " after changing it back");
                SelfTest.check(live.fileText().contains("\n" + o.key + "=" + before + "\n"), "file not changed back to " + o.key + "=" + before);
            }));

            tests.add("settings/load/" + o.key, () -> live.restoring(() -> {
                Path real = live.file(); // load() points TurboConfig at the folder it read (restoring puts it back)
                Path tmp = live.dir().resolve("selftest-load-" + o.key);
                Files.createDirectories(tmp);
                Path file = tmp.resolve("litematica_turbo.properties");
                Files.write(file, ("# written by the self-test\n" + o.key + "=" + o.value + "\n").getBytes(StandardCharsets.UTF_8));
                Object loaded = Ctx.call(live.configClass, "load", tmp);
                Object defaults = live.configClass.getDeclaredConstructor().newInstance();
                SelfTest.equal(o.value, Ctx.getField(loaded, o.key), "loaded " + o.key);

                for (Option other : OPTIONS)
                {
                    if (other != o)
                    {
                        SelfTest.equal(Ctx.getField(defaults, other.key), Ctx.getField(loaded, other.key), "unset " + other.key + " (default)");
                    }
                }

                String rewritten = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                SelfTest.check(rewritten.contains("\n" + o.key + "=" + o.value + "\n") && rewritten.contains("\ncatchUpBudgetMs="),
                               "the loaded file was not rewritten with every option:\n" + rewritten);

                // shown in the tab, without saving anything while it syncs
                byte[] liveFile = Files.readAllBytes(real);
                ctxMain(ctx, () -> {
                    Ctx.call(live.configClass, "set", loaded);
                    Ctx.call(live.tab, "syncFromConfig");
                });
                SelfTest.equal(o.value, live.shown(o), "tab option after loading");
                SelfTest.check(java.util.Arrays.equals(Files.readAllBytes(file), rewritten.getBytes(StandardCharsets.UTF_8)), "showing the loaded values saved the file again");
                Files.delete(file);
                Files.delete(tmp);
                SelfTest.check(java.util.Arrays.equals(liveFile, Files.readAllBytes(real)), "loading touched the game's own settings file");
            }));
        }

        tests.add("settings/load-bad-values", () -> live.restoring(() -> {
            Path tmp = live.dir().resolve("selftest-load-bad");
            Files.createDirectories(tmp);
            Path file = tmp.resolve("litematica_turbo.properties");
            Files.write(file, "buildThreads=lots\nghostSettleMs=-40\ncatchUpBudgetMs=0\nshowStats=nope\n".getBytes(StandardCharsets.UTF_8));
            Object loaded = Ctx.call(live.configClass, "load", tmp);
            SelfTest.equal(0, Ctx.getField(loaded, "buildThreads"), "buildThreads=lots falls back to the default");
            SelfTest.equal(0, Ctx.getField(loaded, "ghostSettleMs"), "ghostSettleMs=-40 clamps to 0");
            SelfTest.equal(1, Ctx.getField(loaded, "catchUpBudgetMs"), "catchUpBudgetMs=0 clamps to 1");
            SelfTest.equal(false, Ctx.getField(loaded, "showStats"), "showStats=nope reads as false");
            Files.delete(file);
            Files.delete(tmp);
        }));

        tests.add("settings/int-ranges", () -> live.restoring(() -> {
            Object[][] ranges = {{"BUILD_THREADS", -1, 16}, {"GHOST_SETTLE_MS", 0, 2000}, {"CATCH_UP_BUDGET_MS", 1, 50}};

            for (Object[] r : ranges)
            {
                Option o = find((String) r[0]);
                SelfTest.equal(r[1], Ctx.call(live.option(o), "getMinIntegerValue"), o.constant + " minimum");
                SelfTest.equal(r[2], Ctx.call(live.option(o), "getMaxIntegerValue"), o.constant + " maximum");
                live.setOption(o, (Integer) r[2] + 1000);
                SelfTest.equal(r[2], Ctx.getField(live.config(), o.key), o.key + " above the maximum");
                live.setOption(o, (Integer) r[1] - 1000);
                SelfTest.equal(r[1], Ctx.getField(live.config(), o.key), o.key + " below the minimum");
            }
        }));

        tests.add("settings/apply/build-threads", () -> live.restoring(() -> {
            Option o = find("BUILD_THREADS");
            int cpus = (Integer) Ctx.getStatic(live.configClass, "CPU_COUNT");
            boolean reload = live.setOptionFlag(o, -1, "reloadRenderers");
            SelfTest.equal(false, Ctx.call(live.config(), "threadingEnabled"), "threadingEnabled with -1 (stock: render thread only)");
            SelfTest.equal(0, Ctx.call(live.config(), "resolveBuildThreads", cpus), "threads with -1");
            SelfTest.equal(false, Ctx.call(live.config(), "asyncNearRebuildsEnabled"), "background near rebuilds without threads");
            SelfTest.check(reload, "a new thread count did not ask for new renderers");
            live.ctx.ticks(3);
            SelfTest.check(live.flag("reloadRenderers") == false, "the renderer reload was not applied on the next tick");
            live.setOption(o, 5);
            SelfTest.equal(5, Ctx.call(live.config(), "resolveBuildThreads", cpus), "threads with 5");
            SelfTest.equal(true, Ctx.call(live.config(), "threadingEnabled"), "threadingEnabled with 5");
            live.setOption(o, 0);
            SelfTest.equal(Math.max(1, Math.min(cpus / 3, 6)), Ctx.call(live.config(), "resolveBuildThreads", cpus), "automatic threads for " + cpus + " CPUs");
        }));

        tests.add("settings/apply/master-switch", () -> live.restoring(() -> {
            Option o = find("ENABLED");
            boolean reload = live.setOptionFlag(o, false, "reloadRenderers");

            for (String check : new String[]{"threadingEnabled", "skipEmptySectionsEnabled", "asyncNearRebuildsEnabled", "ghostMoveEnabled"})
            {
                SelfTest.equal(false, Ctx.call(live.config(), check), check + " with Turbo off (stock Litematica)");
            }

            SelfTest.check(reload, "switching Turbo off did not ask for stock renderers");
            live.setOption(o, true);

            for (String check : new String[]{"threadingEnabled", "skipEmptySectionsEnabled", "asyncNearRebuildsEnabled", "ghostMoveEnabled"})
            {
                SelfTest.equal(true, Ctx.call(live.config(), check), check + " with Turbo on again");
            }
        }));

        tests.add("settings/apply/skip-empty-refresh", () -> live.restoring(() -> {
            Option o = find("SKIP_EMPTY_SECTIONS");
            live.ctx.ticks(2);
            boolean[] flags = live.ctx.onMain(() -> {
                Ctx.call(live.option(o), "setBooleanValue", false);
                return new boolean[]{live.flag("refreshRenders"), live.flag("reloadRenderers")};
            });
            SelfTest.equal(false, Ctx.call(live.config(), "skipEmptySectionsEnabled"), "skipEmptySectionsEnabled");
            SelfTest.check(flags[0], "changing empty-section skipping did not ask for a refresh");
            SelfTest.check(flags[1] == false, "empty-section skipping asked for new renderers (only a refresh is needed)");
            live.ctx.ticks(3);
            SelfTest.check(live.flag("refreshRenders") == false, "the refresh was not applied on the next tick");
        }));

        tests.add("settings/apply/ghost-move", () -> live.restoring(() -> {
            Option o = find("GHOST_MOVE");
            live.setOption(o, false);
            SelfTest.equal(false, Ctx.call(live.config(), "ghostMoveEnabled"), "ghostMoveEnabled");
            live.setOption(o, true);
            SelfTest.equal(true, Ctx.call(live.config(), "ghostMoveEnabled"), "ghostMoveEnabled on again");
        }));

        tests.add("settings/tab/options", () -> live.restoring(() -> ctxMain(ctx, () -> {
            Object gui = ctx.cls("fi.dy.masa.litematica.gui.GuiConfigs").getDeclaredConstructor().newInstance();
            boolean selected = live.flag("selected");

            try
            {
                Ctx.setStatic(live.tab, "selected", false);
                List<String> stock = configNames(gui);
                SelfTest.check(stock.size() > 0, "Litematica's own tab shows no options");

                for (String name : stock)
                {
                    SelfTest.check(name.startsWith("turbo") == false, "Litematica's own tab shows Turbo's " + name);
                }

                Ctx.setStatic(live.tab, "selected", true);
                List<String> turbo = configNames(gui);
                List<String> expected = new ArrayList<>();

                for (Object option : (List<?>) Ctx.getStatic(live.tab, "OPTIONS"))
                {
                    expected.add((String) Ctx.call(option, "getName"));
                }

                SelfTest.equal(expected, turbo, "options on the Turbo tab");
                SelfTest.equal(OPTIONS.length, turbo.size(), "number of Turbo options");
                Ctx.setStatic(live.tab, "selected", false);
                SelfTest.equal(stock, configNames(gui), "Litematica's tab after leaving the Turbo tab");
            }
            finally
            {
                Ctx.setStatic(live.tab, "selected", selected);
            }
        })));

        tests.add("settings/tab/button", () -> live.restoring(() -> TabButton.run(ctx, live)));
    }

    static Option find(String constant)
    {
        for (Option o : OPTIONS)
        {
            if (o.constant.equals(constant))
            {
                return o;
            }
        }

        throw new IllegalArgumentException(constant);
    }

    static List<String> configNames(Object gui) throws ReflectiveOperationException
    {
        List<String> names = new ArrayList<>();

        for (Object wrapper : (List<?>) Ctx.call(gui, "getConfigs"))
        {
            Object config = Ctx.call(wrapper, "getConfig");
            names.add(config == null ? "(label)" : (String) Ctx.call(config, "getName"));
        }

        return names;
    }

    static void ctxMain(Ctx ctx, SelfTest.Body body) throws Exception
    {
        ctx.onMain(() -> {
            body.run();
            return null;
        });
    }
}
