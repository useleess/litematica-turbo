package com.litematicaturbo.selftest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * stats/: the Turbo part of Litematica's [Litematica] F3 line. BuildStats counts builds and skipped slices, its
 * summary has the documented format, and Litematica's own debug line (WorldRendererSchematic.getDebugInfoRenders,
 * what F3 shows) gets " | Turbo T:.. Q:.. ..." appended only while Turbo and "Show F3 stats" are on - with either
 * off it is exactly Litematica's own line.
 *
 * BuildStats is static: the counters keep what these tests add for the rest of the game, so later checks compare
 * differences, never absolute values.
 */
final class StatsTests
{
    static final Pattern SUMMARY = Pattern.compile("Turbo T:(\\d+) Q:(\\d+) B/s:\\d+ avg:\\d+[.,]\\dms main:\\d+ms/s skip:(\\d+)%");

    private StatsTests()
    {
    }

    static void register(Ctx ctx, SelfTest.Tests tests) throws Exception
    {
        Class<?> stats = ctx.cls("com.litematicaturbo.core.BuildStats");

        tests.add("stats/counts-builds", () -> {
            long builds = (Long) Ctx.call(stats, "totalBuilds");
            long nanos = (Long) Ctx.call(stats, "totalBuildNanos");
            long main = (Long) Ctx.call(stats, "totalMainThreadNanos");
            Ctx.call(stats, "recordBuild", 2_000_000L, false);
            Ctx.call(stats, "recordBuild", 3_000_000L, true);
            SelfTest.equal(builds + 2, Ctx.call(stats, "totalBuilds"), "builds counted");
            SelfTest.equal(nanos + 5_000_000L, Ctx.call(stats, "totalBuildNanos"), "build time counted");
            SelfTest.equal(main + 3_000_000L, Ctx.call(stats, "totalMainThreadNanos"), "only the render-thread build counts as main-thread time");
        });

        tests.add("stats/summary-format", () -> {
            Thread.sleep(1100); // the summary is recomputed at most once a second
            String s = (String) Ctx.call(stats, "summary", 4, 9);
            java.util.regex.Matcher m = SUMMARY.matcher(s);
            SelfTest.check(m.matches(), "summary format: " + s);
            SelfTest.equal("4", m.group(1), "T: (threads)");
            SelfTest.equal("9", m.group(2), "Q: (queued)");
        });

        tests.add("stats/skip-share", () -> {
            AtomicLong scanned = (AtomicLong) Ctx.getStatic(stats, "sectionsScanned");
            AtomicLong skipped = (AtomicLong) Ctx.getStatic(stats, "sectionsSkipped");
            long s0 = scanned.get(), k0 = skipped.get();
            // enough slices that this test's share dominates whatever the game counted before
            long add = Math.max(1000, (s0 + 1) * 10);
            Ctx.call(stats, "recordSections", (int) add, (int) (add * 3 / 4));
            SelfTest.equal(s0 + add, scanned.get(), "slices scanned");
            SelfTest.equal(k0 + add * 3 / 4, skipped.get(), "slices skipped");
            Thread.sleep(1100);
            java.util.regex.Matcher m = SUMMARY.matcher((String) Ctx.call(stats, "summary", 1, 0));
            SelfTest.check(m.matches(), "summary format");
            SelfTest.equal(String.valueOf((k0 + add * 3 / 4) * 100 / (s0 + add)), m.group(3), "skip: share of skipped slices");
        });

        tests.add("stats/summary-steady", () -> {
            // within one second the line does not change (F3 is drawn every frame)
            Thread.sleep(1100);
            String a = (String) Ctx.call(stats, "summary", 7, 7);
            Ctx.call(stats, "recordBuild", 1_000_000L, false);
            String b = (String) Ctx.call(stats, "summary", 7, 7);
            SelfTest.equal(a, b, "summary within the same second");
        });

        SettingsTests.Live live = new SettingsTests.Live(ctx);

        tests.add("stats/f3-line-turbo-on", () -> {
            Thread.sleep(1100); // the summary is cached for a second: the line must show this dispatcher's numbers
            withDispatcher(ctx, "showStats", (renderer, dispatcher, stock) -> {
                String line = (String) Ctx.call(renderer, "getDebugInfoRenders");
                SelfTest.check(line.startsWith(stock + " | Turbo "), "F3 line with Turbo on: '" + line + "' (Litematica's own: '" + stock + "')");
                java.util.regex.Matcher m = SUMMARY.matcher(line.substring(stock.length() + 3));
                SelfTest.check(m.matches(), "Turbo part of the F3 line: " + line);
                SelfTest.equal(String.valueOf(Ctx.call(dispatcher, "turbo$threadCount")), m.group(1), "T: = the dispatcher's build threads");
                SelfTest.equal(String.valueOf(Ctx.call(dispatcher, "turbo$queuedTasks")), m.group(2), "Q: = the dispatcher's queued builds");
                SelfTest.equal("2", m.group(1), "T: with two build threads");
            });
        });

        tests.add("stats/f3-line-stats-off", () -> live.restoring(() -> {
            live.setOption(SettingsTests.find("SHOW_STATS"), false);
            withDispatcher(ctx, "enabled", (renderer, dispatcher, stock) ->
                SelfTest.equal(stock, Ctx.call(renderer, "getDebugInfoRenders"), "F3 line with Show F3 stats off = Litematica's own (the line with Turbo off)"));
        }));

        tests.add("stats/f3-line-turbo-off", () -> live.restoring(() -> {
            live.setOption(SettingsTests.find("ENABLED"), false);
            withDispatcher(ctx, "showStats", (renderer, dispatcher, stock) ->
                SelfTest.equal(stock, Ctx.call(renderer, "getDebugInfoRenders"), "F3 line with Turbo off = Litematica's own (the line with Show F3 stats off)"));
        }));

        tests.add("stats/f3-line-no-dispatcher", () -> ctx.onMain(() -> {
            // no schematic loaded: Litematica has no build dispatcher; the line must stay Litematica's own
            Object renderer = renderer(ctx);
            SelfTest.check(Ctx.getField(renderer, "renderDispatcher") == null, "a build dispatcher exists without a schematic world");
            String line = (String) Ctx.call(renderer, "getDebugInfoRenders");
            SelfTest.check(line.contains("Turbo") == false, "F3 line without a dispatcher: " + line);
            return null;
        }));
    }

    interface DispatcherBody
    {
        void run(Object renderer, Object dispatcher, String stockLine) throws Exception;
    }

    static Object renderer(Ctx ctx) throws Exception
    {
        return Ctx.call(Ctx.call(ctx.cls("fi.dy.masa.litematica.render.LitematicaRenderer"), "getInstance"), "getWorldRenderer");
    }

    /**
     * Litematica only has a build dispatcher while a schematic world is loaded. This gives the live world renderer
     * an idle one for the duration of the body (its patched class, made without starting threads: empty queues,
     * two placeholder build threads), and Litematica's own line: the same call with stockSwitch (a TurboConfig
     * field: showStats or enabled) set to false for a moment. Tests then compare with the other switch.
     */
    static void withDispatcher(Ctx ctx, String stockSwitch, DispatcherBody body) throws Exception
    {
        ctx.onMain(() -> {
            Object renderer = renderer(ctx);
            Field field = Ctx.field(renderer.getClass(), "renderDispatcher");
            Object original = field.get(renderer);

            if (original != null)
            {
                throw SelfTest.skip("a schematic world is loaded (a real dispatcher exists)");
            }

            Object dispatcher = idleDispatcher(field.getType());

            try
            {
                field.set(renderer, dispatcher);
                Class<?> config = ctx.cls("com.litematicaturbo.core.TurboConfig");
                Object c = Ctx.call(config, "get");
                boolean show = (Boolean) Ctx.getField(c, "showStats");
                boolean enabled = (Boolean) Ctx.getField(c, "enabled");
                // Litematica's own line: the patch only appends while both switches are on; this one is turned off
                Ctx.setField(c, stockSwitch, false);
                String stock = (String) Ctx.call(renderer, "getDebugInfoRenders");
                Ctx.setField(c, "showStats", show);
                Ctx.setField(c, "enabled", enabled);
                SelfTest.check(stock.contains("Turbo") == false, "Litematica's own line has Turbo in it: " + stock);
                body.run(renderer, dispatcher, stock);
            }
            finally
            {
                field.set(renderer, original);
            }

            return null;
        });
    }

    static Object idleDispatcher(Class<?> type) throws Exception
    {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Object dispatcher = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type);

        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
        {
            for (Field f : c.getDeclaredFields())
            {
                if (Modifier.isStatic(f.getModifiers()))
                {
                    continue;
                }

                Object empty = emptyOf(f.getType());

                if (empty != null)
                {
                    f.setAccessible(true);
                    setFinal(unsafeClass, unsafe, f, dispatcher, empty);

                    if (f.getName().equals("listWorkerThreads") || f.getName().equals("turbo$threads"))
                    {
                        @SuppressWarnings("unchecked")
                        Collection<Object> threads = (Collection<Object>) empty;
                        threads.add(new Thread("selftest placeholder 1"));
                        threads.add(new Thread("selftest placeholder 2"));
                    }
                }
            }
        }

        return dispatcher;
    }

    /** Sets a (possibly final) instance field through Unsafe. */
    private static void setFinal(Class<?> unsafeClass, Object unsafe, Field f, Object target, Object value) throws Exception
    {
        long offset = (Long) unsafeClass.getMethod("objectFieldOffset", Field.class).invoke(unsafe, f);
        unsafeClass.getMethod("putObject", Object.class, long.class, Object.class).invoke(unsafe, target, offset, value);
    }

    private static Object emptyOf(Class<?> type)
    {
        if (type.isInterface() == false && (type.getName().startsWith("java.util.")) && (type.getModifiers() & Modifier.ABSTRACT) == 0)
        {
            try
            {
                return type.getDeclaredConstructor().newInstance();
            }
            catch (ReflectiveOperationException e)
            {
                return null;
            }
        }

        if (type == List.class || type == Collection.class) return new ArrayList<>();
        if (type == Set.class) return new HashSet<>();
        if (type == Map.class) return new HashMap<>();
        if (type == java.util.concurrent.BlockingQueue.class) return new PriorityBlockingQueue<>();
        if (type == java.util.concurrent.BlockingDeque.class || type == java.util.Deque.class) return new LinkedBlockingDeque<>();
        if (type == Queue.class) return new ConcurrentLinkedQueue<>();
        return null;
    }
}
