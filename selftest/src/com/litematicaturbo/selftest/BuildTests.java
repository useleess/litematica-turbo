package com.litematicaturbo.selftest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * build/: the core promise - Turbo changes when and where schematic chunks are built, never what is built. The
 * chunk build results of Turbo's background threads (1, 2, 3, 4, 8, 16 and automatic threads) are described
 * (Digest) and compared with plain Litematica's (Turbo's master switch off) for test schematics generated from the
 * game's block registry: every block state, block entities, fluids, transparent / translucent blocks, sparse
 * columns, blocks along chunk borders. Also: nothing missed, nothing built twice, the thread count is applied,
 * the render thread does less.
 */
final class BuildTests
{
    private BuildTests()
    {
    }

    private static void blockEntitiesSame(Fixture f, Fixture.Capture stock, Fixture.Capture turbo)
    {
        int entities = 0;

        for (Long k : WorldTests.zones(f, "B"))
        {
            entities += stock.sigs.get(k).count("BE:");
        }

        if (entities == 0)
        {
            throw SelfTest.skip("placing raw block states created no block entities on this version");
        }

        WorldTests.same(stock, turbo, WorldTests.zones(f, "B"), "block-entity chunks");
    }

    private static int count(String text, String part)
    {
        int n = 0;

        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length()))
        {
            ++n;
        }

        return n;
    }

    /** Lines of the game log that carry the forced build error. */
    private static int faultLines()
    {
        try
        {
            int n = 0;

            for (String line : java.nio.file.Files.readAllLines(new java.io.File("logs/latest.log").toPath(), java.nio.charset.StandardCharsets.UTF_8))
            {
                if (line.contains("test fault in the chunk build"))
                {
                    ++n;
                }
            }

            return n;
        }
        catch (java.io.IOException e)
        {
            throw new SelfTest.Fail("the game log cannot be read: " + e);
        }
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("build/baseline-repeatable", () -> {
            Fixture f = WorldTests.fixture(ctx);
            // two builds of the same thing by plain Litematica must agree, or no comparison means anything
            WorldTests.same(WorldTests.stock(f), WorldTests.stockAgain(f), f.allChunks(), "plain Litematica twice");
        });

        tests.add("build/geometry-exists", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture stock = WorldTests.stock(f);

            for (String zone : new String[] {"N", "A", "B", "S", "T", "F", "E"})
            {
                WorldTests.hasGeometry(f, stock, WorldTests.zones(f, zone), "zone " + zone);
            }
        });

        tests.add("render/draw-block-entities-while-rebuilt", () -> {
            // The schematic's block entities are drawn while Turbo's threads rebuild the chunks that hold them: the render
            // thread must never meet a block entity list that a build thread is still filling (on Litematica 1.21.11 / 26.x
            // that was a ConcurrentModificationException in WorldRendererSchematic.prepareBlockEntities and the game ended).
            // Between rounds Litematica's display options change the way the compat/ tests change them (layer views,
            // translucent, fluids, a renderer reload): those make other builds (e.g. chunks left with only block entities).
            Fixture f = WorldTests.fixture(ctx);
            f.configure(true, 3, true, true);
            f.drawBlockEntities(true);
            String[] steps = {"", "SINGLE_LAYER", "ALL_ABOVE", "ALL_BELOW", "ALL", "RENDER_BLOCKS_AS_TRANSLUCENT", "RENDER_BLOCKS_AS_TRANSLUCENT",
                              "ENABLE_SCHEMATIC_FLUIDS", "ENABLE_SCHEMATIC_FLUIDS", "reload", "", ""};

            java.util.Map<Object, Boolean> options = new java.util.LinkedHashMap<>();

            try
            {
                f.renderersMayBeMissing = true;

                for (String step : steps)
                {
                    java.util.Map<Long, Object> before = step.isEmpty() ? f.dataNow(f.allChunks()) : null;

                    if (step.equals("reload"))
                    {
                        f.reloadLikeTheTab();
                    }
                    else if (step.startsWith("ALL") || step.equals("SINGLE_LAYER"))
                    {
                        CompatTests.setMode(ctx, step);
                        CompatTests.setLayer(ctx, f.nearY + 2);
                    }
                    else if (step.isEmpty() == false)
                    {
                        Object opt;

                        try
                        {
                            opt = Ctx.getStatic(ctx.cls("fi.dy.masa.litematica.config.Configs$Visuals"), step);
                        }
                        catch (ReflectiveOperationException e)
                        {
                            continue; // not an option of this Litematica
                        }

                        boolean value = (Boolean) Ctx.call(opt, "getBooleanValue");
                        options.putIfAbsent(opt, value);
                        Ctx.call(opt, "setBooleanValue", value == false);
                    }

                    f.touchAll();
                    f.awaitIdle("rebuilt with the block entities drawn" + (step.isEmpty() ? "" : " after " + step), f.allChunks(), before);
                }
            }
            finally
            {
                f.renderersMayBeMissing = false;
                CompatTests.setMode(ctx, "ALL");

                for (java.util.Map.Entry<Object, Boolean> e : options.entrySet())
                {
                    Ctx.call(e.getKey(), "setBooleanValue", e.getValue());
                }

                f.configure(true, 3, true, true);
                ctx.ticks(40);
            }
        });

        zone(ctx, tests, "all-states-same", "A");
        tests.add("build/block-entities-same", () -> {
            Fixture f = WorldTests.fixture(ctx);
            blockEntitiesSame(f, WorldTests.stock(f), WorldTests.turbo(f));
        });
        zone(ctx, tests, "translucent-same", "T");
        zone(ctx, tests, "fluids-same", "F");
        zone(ctx, tests, "sparse-same", "S");
        zone(ctx, tests, "region-edges-same", "E");
        zone(ctx, tests, "near-set-same", "N");

        for (int n : new int[] {1, 2, 4, 8, 16})
        {
            tests.add("build/threads-" + n + "-same", () -> {
                Fixture f = WorldTests.fixture(ctx);
                WorldTests.same(WorldTests.stock(f), WorldTests.threads(f, n), f.allChunks(), n + " build thread(s)");
            });
        }

        tests.add("build/threads-auto-same", () -> {
            Fixture f = WorldTests.fixture(ctx);
            WorldTests.same(WorldTests.stock(f), WorldTests.automatic(f), f.allChunks(), "automatic thread count");
        });

        tests.add("build/threads-agree", () -> {
            Fixture f = WorldTests.fixture(ctx);
            WorldTests.same(WorldTests.threads(f, 1), WorldTests.threads(f, 2), f.allChunks(), "1 vs 2 threads");
            WorldTests.same(WorldTests.threads(f, 2), WorldTests.threads(f, 3), f.allChunks(), "2 vs 3 threads");
            WorldTests.same(WorldTests.threads(f, 3), WorldTests.threads(f, 4), f.allChunks(), "3 vs 4 threads");
            WorldTests.same(WorldTests.threads(f, 4), WorldTests.threads(f, 8), f.allChunks(), "4 vs 8 threads");
        });

        tests.add("build/repeat-parallel-stable", () -> {
            Fixture f = WorldTests.fixture(ctx);
            WorldTests.same(WorldTests.threads(f, 4), WorldTests.repeat4(f), f.allChunks(), "4 threads twice");
        });

        tests.add("build/no-chunk-missed", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Set<Long> all = f.allChunks();

            for (Fixture.Capture c : new Fixture.Capture[] {WorldTests.stock(f), WorldTests.threads(f, 4), WorldTests.automatic(f)})
            {
                SelfTest.equal(all.size(), c.sigs.size(), "chunks built in '" + c.label + "'");

                for (Long k : all)
                {
                    SelfTest.check(c.sigs.containsKey(k), "chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k) + " never built in '" + c.label + "'");
                }
            }
        });

        tests.add("build/built-once", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture stock = WorldTests.stock(f);
            int chunks = f.allChunks().size();

            for (int n : new int[] {2, 4, 8})
            {
                Fixture.Capture c = WorldTests.threads(f, n);
                // plain Litematica builds no chunk that has no block in it at all: Turbo must build exactly those, no fewer
                SelfTest.check(c.builds >= stock.builds, n + " threads: " + c.builds + " builds, plain Litematica made " + stock.builds + " for " + chunks + " chunks (a chunk was not built)");
                SelfTest.check(c.builds <= Math.max(stock.builds, chunks) + 2,
                               n + " threads: " + c.builds + " builds for " + chunks + " chunks, plain Litematica needed " + stock.builds + " (chunks built twice)");
            }
        });

        tests.add("build/error-drops-the-build-and-rebuilds", () -> {
            Class<?> errors;

            try
            {
                ctx.turbo("DroppedBuilds");
                errors = ctx.cls("com.litematicaturbo.core.BuildErrors");
            }
            catch (ClassNotFoundException e)
            {
                throw SelfTest.skip("this version has no dropped-build guard");
            }

            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture normal = WorldTests.turbo(f);
            String key = "litematica_turbo.message.build_dropped";
            Ctx.call(errors, "resetForTest");
            String chatBefore = SafetyTests.chatText(ctx);
            int logBefore = faultLines();
            Ctx.call(errors, "armFaultForTest");
            Fixture.Capture faulted = f.captureNew("t3-build-fault", true, 3, true, true);
            ctx.ticks(10);
            SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone after the build error");
            SelfTest.equal(1, (Integer) Ctx.call(errors, "dropped"), "builds dropped after one forced error");
            WorldTests.same(normal, faulted, f.allChunks(), "the build after the dropped one");
            String chat = SafetyTests.chatText(ctx);
            // the chat is kept in several lists on some versions, so "once" is shown by a second dropped build adding nothing
            SelfTest.check(count(chat, key) > count(chatBefore, key), "no chat line tells the player about the dropped build: " + chat);
            Ctx.call(errors, "armFaultForTest");
            f.captureNew("t3-build-fault-again", true, 3, true, true);
            ctx.ticks(10);
            SelfTest.equal(2, (Integer) Ctx.call(errors, "dropped"), "builds dropped after a second forced error");
            SelfTest.equal(count(chat, key), count(SafetyTests.chatText(ctx), key), "chat lines about dropped builds after a second one (the player is told once)");
            SelfTest.check(faultLines() > logBefore, "the game log has no stack of the dropped build");
        });

        tests.add("build/render-thread-time-drops", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture stock = WorldTests.stock(f);
            Fixture.Capture turbo = WorldTests.threads(f, 4);

            if (stock.mainThreadNanos < 50_000_000L)
            {
                throw SelfTest.skip("plain Litematica builds off the render thread here already (" + stock.mainThreadNanos / 1_000_000L + " ms on it)");
            }

            SelfTest.check(turbo.mainThreadNanos * 2 < stock.mainThreadNanos,
                           "render-thread build time with Turbo " + turbo.mainThreadNanos / 1_000_000L + " ms, plain Litematica " + stock.mainThreadNanos / 1_000_000L + " ms");
        });

        tests.add("build/thread-count-applied", () -> {
            Fixture f = WorldTests.fixture(ctx);

            for (int n : new int[] {1, 2, 3, 4, 8, 16})
            {
                int started = WorldTests.threads(f, n).threads;

                // Litematica of the oldest versions starts worker threads only for a count above one: Turbo asks for two then
                SelfTest.check(started == n || (n == 1 && started == 2), "build threads started for the setting " + n + ": " + started);
            }

            int automatic = WorldTests.automatic(f).threads;
            f.configure(true, 0, true, true); // captures are kept, so the config may hold another setting by now
            Object config = Ctx.call(f.configClass, "get");
            Object cpu = Ctx.getStatic(f.configClass, "CPU_COUNT");
            SelfTest.equal(Ctx.call(config, "resolveBuildThreads", cpu), automatic, "build threads for the automatic setting");
        });

        tests.add("build/reload-waits-for-a-running-build", () -> {
            Fixture f = WorldTests.fixture(ctx);
            f.awaitQuiet("before the reload");
            Object renderer = f.anyRenderer();

            try
            {
                Ctx.field(renderer.getClass(), "builderCache");
            }
            catch (NoSuchFieldException e)
            {
                throw SelfTest.skip("this Litematica keeps no buffer builders per chunk renderer: clearing one never touches a running build");
            }

            Thread renderThread = ctx.onMain(Thread::currentThread);
            ReentrantLock build = (ReentrantLock) Ctx.call(renderer, "turbo$buildLock");
            CompletableFuture<Void> reload;
            boolean waited;
            // this thread stands for a build thread in the middle of that chunk; a reload (shaders switched, F3+A) clears it
            build.lock();

            try
            {
                reload = CompletableFuture.runAsync(() -> {
                    try
                    {
                        f.reloadLikeTheTab();
                    }
                    catch (Exception e)
                    {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                });
                long deadline = System.currentTimeMillis() + 30_000L;

                while (build.hasQueuedThread(renderThread) == false && reload.isDone() == false && System.currentTimeMillis() < deadline)
                {
                    Thread.sleep(5);
                }

                waited = build.hasQueuedThread(renderThread);
            }
            finally
            {
                build.unlock();
            }

            reload.get(120, TimeUnit.SECONDS);
            f.reload();
            f.awaitQuiet("after the reload");
            SelfTest.check(waited, "the renderers were reloaded while a build of one of their chunks was still running (its buffers ended from two threads)");
        });

        tests.add("build/overlay-same", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture stock = WorldTests.stock(f);
            Fixture.Capture turbo = WorldTests.turbo(f);
            int overlay = 0;

            for (Long k : f.allChunks())
            {
                Fixture.Sig a = stock.sigs.get(k);
                Fixture.Sig b = turbo.sigs.get(k);
                java.util.List<String> la = new java.util.ArrayList<>();
                java.util.List<String> lb = new java.util.ArrayList<>();

                for (String l : a.lines)
                {
                    if (l.toLowerCase().contains("overlay"))
                    {
                        la.add(l);
                    }
                }

                for (String l : b.lines)
                {
                    if (l.toLowerCase().contains("overlay"))
                    {
                        lb.add(l);
                    }
                }

                SelfTest.check(la.equals(lb), "overlay of chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k) + " differs");
                overlay += la.size();
            }

            if (overlay == 0)
            {
                throw SelfTest.skip("no overlay data is visible in the build result on this version");
            }
        });
    }

    private static void zone(Ctx ctx, SelfTest.Tests tests, String name, String zone)
    {
        tests.add("build/" + name, () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture stock = WorldTests.stock(f);
            WorldTests.hasGeometry(f, stock, WorldTests.zones(f, zone), "zone " + zone);
            WorldTests.same(stock, WorldTests.turbo(f), WorldTests.zones(f, zone), "zone " + zone);
        });
    }
}
