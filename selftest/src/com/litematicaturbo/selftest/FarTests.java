package com.litematicaturbo.selftest;

import java.lang.reflect.Method;

/**
 * far/: less detail far away. Pieces of the schematic at least half the render distance away are drawn without their
 * outline lines. Proved by COUNTING outline draws per frame (never by time) in the same game with the setting on and off:
 * far pieces draw none with it on and some with it off, near pieces draw the same with it on and off, pretended low
 * memory and an error in the check each bring every outline back (the error with a chat line).
 */
final class FarTests
{
    private static final String FAR = "com.litematicaturbo.core.FarDetail";
    private static final String OFF_KEY = "litematica_turbo.message.far_off";

    private FarTests()
    {
    }

    /** Outline draws over about a second of frames. */
    private static final class Outlines
    {
        final long near;
        final long farDrawn;
        final long farSkipped;

        Outlines(long near, long farDrawn, long farSkipped)
        {
            this.near = near;
            this.farDrawn = farDrawn;
            this.farSkipped = farSkipped;
        }

        /** The share of the pieces with outlines that are near: the same with the setting on and off. */
        double nearShare()
        {
            return (double) this.near / (this.near + this.farDrawn + this.farSkipped);
        }

        @Override
        public String toString()
        {
            return "near " + this.near + ", far drawn " + this.farDrawn + ", far left out " + this.farSkipped;
        }
    }

    private static void setting(Ctx ctx, boolean on) throws Exception
    {
        Object config = Ctx.call(ctx.cls("com.litematicaturbo.core.TurboConfig"), "get");
        Ctx.setField(config, "enabled", true);
        Ctx.setField(config, "farDetail", on);
    }

    /** The counters, read between two frames. */
    private static long[] counters(Ctx ctx) throws Exception
    {
        Class<?> far = ctx.cls(FAR);
        return ctx.onMain(() -> new long[] {(Long) Ctx.call(far, "nearDrawn"), (Long) Ctx.call(far, "farDrawn"), (Long) Ctx.call(far, "farSkipped")});
    }

    private static Outlines outlines(Ctx ctx) throws Exception
    {
        ctx.ticks(2);
        long[] before = counters(ctx);
        ctx.ticks(20);
        long[] after = counters(ctx);
        Outlines o = new Outlines(after[0] - before[0], after[1] - before[1], after[2] - before[2]);
        SelfTest.check(o.near > 0, "no near piece drew outline lines (" + o + "): the schematic overlay is not drawn");
        return o;
    }

    private static void start(Ctx ctx) throws Exception
    {
        WorldTests.fixture(ctx);
        Ctx.call(ctx.cls(FAR), "resetForTest");
        setting(ctx, true);
    }

    private static void finish(Ctx ctx) throws Exception
    {
        Ctx.call(ctx.cls(FAR), "resetForTest");
        setting(ctx, true);
    }

    /** Every outline drawn: far pieces draw theirs, none is left out. */
    private static void allDrawn(Outlines o, String when)
    {
        SelfTest.check(o.farDrawn > 0, when + ": far pieces drew no outline lines (" + o + ")");
        SelfTest.equal(0L, o.farSkipped, when + ": outlines left out");
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("far/outlines-left-out-far-away", () -> {
            start(ctx);

            try
            {
                Outlines on = outlines(ctx);
                SelfTest.equal(0L, on.farDrawn, "outline draws of far pieces with the setting on (" + on + ")");
                SelfTest.check(on.farSkipped > 0, "no far piece with outline lines in view (" + on + "): nothing to test");
                setting(ctx, false);
                allDrawn(outlines(ctx), "setting off");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("far/near-outlines-unchanged", () -> {
            start(ctx);

            try
            {
                Outlines on = outlines(ctx);
                setting(ctx, false);
                Outlines off = outlines(ctx);
                SelfTest.check(Math.abs(on.nearShare() - off.nearShare()) <= 0.005,
                               "near pieces draw a different share of the outlines with the setting on (" + on + ") and off (" + off + ")");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("far/low-memory-switches-it-off", () -> {
            start(ctx);
            Class<?> budget = ctx.cls("com.litematicaturbo.core.MemoryBudget");

            try
            {
                SelfTest.check(outlines(ctx).farSkipped > 0, "nothing left out before the memory test");
                Ctx.call(budget, "setMaxMemoryForTest", 1L);
                allDrawn(outlines(ctx), "memory low");
                SelfTest.equal(false, ctx.onMain(() -> Ctx.call(ctx.cls(FAR), "active")), "active while memory is low");
                Ctx.call(budget, "setMaxMemoryForTest", 0L);
                SelfTest.check(outlines(ctx).farSkipped > 0, "far outlines not left out again once memory is fine");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("far/error-switches-off-and-draws-every-outline", () -> {
            start(ctx);

            try
            {
                Ctx.call(ctx.cls(FAR), "setFaultForTest", true);
                ctx.ticks(10);
                SelfTest.check(Ctx.call(ctx.cls(FAR), "failure") != null, "the error did not switch less detail far away off");
                String chat = SafetyTests.chatText(ctx);
                SelfTest.check(chat.contains(OFF_KEY), "no chat line says less detail far away is off:\n" + chat);
                SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");
                Ctx.call(ctx.cls(FAR), "setFaultForTest", false);
                allDrawn(outlines(ctx), "after the error");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("far/messages-translated", () -> {
            String lang = AuditTests.resource(ctx.cls(FAR).getClassLoader(), "assets/litematica_turbo/lang/en_us.json");
            SelfTest.check(lang != null, "no en_us.json");

            for (String key : new String[] {OFF_KEY, "config.name.turbofardetail"})
            {
                SelfTest.check(lang.contains("\"" + key + "\": \""), "en_us.json has no English text for " + key);
            }

            Method translate = ctx.cls("fi.dy.masa.malilib.util.StringUtils").getMethod("translate", String.class, Object[].class);
            String text = (String) translate.invoke(null, OFF_KEY, new Object[] {"reason-4242"});
            SelfTest.check(text.equals(OFF_KEY) == false && text.contains("reason-4242"), "the message is not translated by the game: " + text);
        });
    }
}
