package com.litematicaturbo.selftest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * sodium/: Turbo next to Sodium and Iris. Only runs started with with=sodium (or with=sodium,iris) load them
 * (ingame-test.sh, gradle/with-mods.gradle); in a normal run every test here skips with the reason. Each speed-up is
 * proved by the tests of its own group (their bodies run again here, so the counts are the same ones), the schematic
 * must really be on screen, nothing may log a GL error and no Turbo feature may switch itself off. With Iris the test
 * shader pack (ingame-test.sh installs it) is switched on for the shaders tests and off again afterwards.
 */
final class SodiumTests
{
    private static final String[] FEATURES = {"BatchDrawing", "FarDetail", "CompleteCopy", "CompactStorage"};
    private static final Pattern GL_ERROR = Pattern.compile("(?i)gl error|opengl debug message|gl_invalid|glerror|gl_out_of_memory");
    private static final Pattern SWITCHED_OFF = Pattern.compile("litematica_turbo\\.message\\.\\w+_off");
    private static int switchedOffInChatAtStart;
    private static final String[] IRIS_API = {"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"};
    /** The last 12 bytes of every PNG: the empty IEND chunk (length 0, type, CRC). */
    private static final byte[] PNG_END = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    private SodiumTests()
    {
    }

    private static String with()
    {
        return System.getProperty("litematicaturbo.with", "");
    }

    private static boolean loaded(Ctx ctx, String mod) throws Exception
    {
        try
        {
            return (Boolean) Ctx.call(Ctx.call(ctx.cls("net.fabricmc.loader.api.FabricLoader"), "getInstance"), "isModLoaded", mod);
        }
        catch (ClassNotFoundException notFabric)
        {
            return (Boolean) Ctx.call(Ctx.call(ctx.cls("net.neoforged.fml.ModList"), "get"), "isLoaded", mod);
        }
    }

    /** Skips unless the run was started with this mod; fails when it was asked for but the game did not load it. */
    private static void needs(Ctx ctx, String mod) throws Exception
    {
        if (Arrays.asList(with().split(",")).contains(mod) == false)
        {
            throw SelfTest.skip(mod + " is not part of this run (ingame-test.sh ... with=" + mod + "; gradle/with-mods.gradle names the versions it is left out for)");
        }

        SelfTest.check(loaded(ctx, mod), mod + " was asked for with=" + with() + " but the game did not load it");
    }

    private static void rerun(SelfTest.Tests tests, String id) throws Exception
    {
        SelfTest.Body body = tests.list.get(id);
        SelfTest.check(body != null, "the test " + id + " this group reuses does not exist any more");
        body.run();
    }

    private static Class<?> core(Ctx ctx, String name) throws ClassNotFoundException
    {
        return ctx.cls("com.litematicaturbo.core." + name);
    }

    private static long counter(Ctx ctx, String owner, String name) throws Exception
    {
        return (Long) ctx.onMain(() -> Ctx.call(core(ctx, owner), name));
    }

    /** Back to the start of a session for every Turbo feature that can switch itself off. */
    private static void freshStart(Ctx ctx) throws Exception
    {
        for (String feature : FEATURES)
        {
            Ctx.call(core(ctx, feature), "resetForTest");
        }

        switchedOffInChat(ctx, true);
    }

    /** How many switch-off lines the chat holds (older tests leave theirs there); remembers the count when asked to. */
    private static int switchedOffInChat(Ctx ctx, boolean remember) throws Exception
    {
        Matcher off = SWITCHED_OFF.matcher(SafetyTests.chatText(ctx));
        int n = 0;

        while (off.find())
        {
            ++n;
        }

        if (remember)
        {
            switchedOffInChatAtStart = n;
        }

        return n;
    }

    private static void noFeatureOff(Ctx ctx, String when) throws Exception
    {
        for (String feature : FEATURES)
        {
            Object failure = Ctx.call(core(ctx, feature), "failure");
            SelfTest.check(failure == null, when + ": Turbo's " + feature + " switched itself off: " + failure);
        }

        SelfTest.check(switchedOffInChat(ctx, false) == switchedOffInChatAtStart, when + ": the chat says a Turbo feature is off\n" + SafetyTests.chatText(ctx));
    }

    private static List<String> glErrors()
    {
        File log = new File("logs/latest.log");
        List<String> found = new ArrayList<>();

        try
        {
            for (String line : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8))
            {
                if (GL_ERROR.matcher(line).find())
                {
                    found.add(line);
                }
            }
        }
        catch (java.io.IOException e)
        {
            throw new SelfTest.Fail("the game log " + log.getAbsolutePath() + " cannot be read: " + e);
        }

        return found;
    }

    /** The last log lines that mention Iris or shaders (what to read when the pack does not load). */
    private static String irisLog()
    {
        try
        {
            List<String> lines = new ArrayList<>();

            for (String line : Files.readAllLines(new File("logs/latest.log").toPath(), StandardCharsets.UTF_8))
            {
                String lower = line.toLowerCase();

                if (line.startsWith("	at ") == false && (lower.contains("iris") || lower.contains("shader") || lower.contains("exception")))
                {
                    lines.add(line.length() > 300 ? line.substring(0, 300) : line);
                }
            }

            return String.join(" | ", lines.subList(Math.max(0, lines.size() - 12), lines.size()));
        }
        catch (java.io.IOException e)
        {
            return "(game log unreadable: " + e + ")";
        }
    }

    private static void noGlErrors(String when)
    {
        List<String> errors = glErrors();
        SelfTest.check(errors.isEmpty(), when + ": " + errors.size() + " GL error line(s) in the game log, the first: " + (errors.isEmpty() ? "" : errors.get(0)));
    }

    /** The schematic is on screen: its outlines are drawn, a frame holds many colours and (batch eras) draw calls happen. */
    private static void schematicDrawn(Ctx ctx, String when, String shotName) throws Exception
    {
        Fixture f = WorldTests.fixture(ctx);
        f.awaitQuiet(when);
        SelfTest.check(f.everythingBuilt(), when + ": not every piece of the schematic is built");
        long outlines = counter(ctx, "FarDetail", "nearDrawn");
        long calls = counter(ctx, "BatchDrawing", "drawCalls");
        long frames = counter(ctx, "BatchDrawing", "frames");
        ctx.ticks(20);
        SelfTest.check(counter(ctx, "FarDetail", "nearDrawn") > outlines, when + ": no piece of the schematic drew its outline lines (block layers drawn in "
                       + (counter(ctx, "BatchDrawing", "frames") - frames) + " frames, " + (counter(ctx, "BatchDrawing", "drawCalls") - calls) + " draw calls; pieces built: "
                       + f.everythingBuilt() + ", renderers: " + f.renderOrigins().size() + ")");

        boolean batchEra = true;

        try
        {
            ctx.turbo("BatchDraw");
        }
        catch (ClassNotFoundException e)
        {
            batchEra = false;
        }

        if (batchEra)
        {
            SelfTest.check(counter(ctx, "BatchDrawing", "drawCalls") > calls, when + ": no draw call was counted for the schematic");
        }

        int colours = shoot(ctx, shotName).colours();
        SelfTest.check(colours >= 20, when + ": the frame shows almost no colour (" + colours + "): the schematic is not in view");
    }

    /** Every speed-up, each by the test of its own group. */
    private static void speedUps(SelfTest.Tests tests, boolean batch) throws Exception
    {
        if (batch)
        {
            try
            {
                rerun(tests, "draw/fewer-calls-per-frame");
                rerun(tests, "draw/same-picture");
            }
            catch (SelfTest.Skip noBatch)
            {
                // this render era has no batch drawing; the schematic itself is checked by the other steps
            }
        }

        rerun(tests, "far/outlines-left-out-far-away");
        rerun(tests, "copy/drawn-pieces-hold-every-block");
        rerun(tests, "move/same-as-stock-one-step-across-chunk");
        rerun(tests, "build/threads-4-same");
    }

    /**
     * The game's own screenshot, saved for review (the test box brings it back) and read back for the checks. The camera
     * looks at the zones again first: tiling other game windows on the test box moves the grabbed cursor and turns it.
     */
    private static DrawTests.Shot shoot(Ctx ctx, String name) throws Exception
    {
        Fixture f = WorldTests.fixture(ctx);
        ctx.onMain(() -> {
            f.aim();
            return null;
        });
        ctx.ticks(3);
        File file = new File("screenshots", name);
        file.delete();
        Game.screenshot(ctx, name);
        long deadline = System.currentTimeMillis() + 15_000L;

        while (written(file) == false && System.currentTimeMillis() < deadline)
        {
            ctx.ticks(2);
        }

        SelfTest.check(written(file), "the game saved no complete screenshot " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
        return DrawTests.Shot.read(file);
    }

    /** The game writes the PNG on a worker thread: it is complete once it ends with the IEND chunk. */
    private static boolean written(File png)
    {
        byte[] tail = new byte[PNG_END.length];

        try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(png, "r"))
        {
            if (in.length() < tail.length)
            {
                return false;
            }

            in.seek(in.length() - tail.length);
            in.readFully(tail);
            return Arrays.equals(tail, PNG_END);
        }
        catch (java.io.IOException notYet)
        {
            // not there yet, or still held by the game's writer: the caller keeps waiting until its deadline
            return false;
        }
    }

    // ---- Iris ----

    private static Object irisApi(Ctx ctx) throws Exception
    {
        for (String name : IRIS_API)
        {
            try
            {
                return Ctx.call(ctx.cls(name), "getInstance");
            }
            catch (ClassNotFoundException e)
            {
                // the next package name
            }
        }

        throw new SelfTest.Fail("Iris is loaded but has no IrisApi class under " + Arrays.toString(IRIS_API));
    }

    private static boolean shadersInUse(Ctx ctx) throws Exception
    {
        return (Boolean) ctx.onMain(() -> Ctx.call(irisApi(ctx), "isShaderPackInUse"));
    }

    private static void setShaders(Ctx ctx, boolean on) throws Exception
    {
        ctx.onMain(() -> {
            Ctx.call(Ctx.call(irisApi(ctx), "getConfig"), "setShadersEnabledAndApply", on);
            return null;
        });
        long deadline = System.currentTimeMillis() + 25_000L;

        while (shadersInUse(ctx) != on && System.currentTimeMillis() < deadline)
        {
            ctx.ticks(2);
        }

        SelfTest.check(shadersInUse(ctx) == on, (on ? "Iris did not load the test shader pack in 25 s" : "Iris still uses a shader pack 25 s after shaders were switched off")
                                               + ". Log: " + irisLog());
        ctx.ticks(20);
    }

    /**
     * Shaders on or off the way a player meets them on joining a world: Iris switches, then Litematica's renderers are
     * made anew. (Litematica for 1.20.4 loses its renderers for good when Iris switches mid-game, plain Litematica as
     * well; sodium/shaders-toggle-like-stock compares that case with Turbo.)
     */
    private static void switchShaders(Ctx ctx, boolean on) throws Exception
    {
        setShaders(ctx, on);
        Fixture f = WorldTests.fixture(ctx);
        f.reload();
        ctx.ticks(5);
        f.awaitQuiet(on ? "shaders switched on" : "shaders switched off");
    }

    /** Whether the schematic has pieces in view after shaders go on mid-game and after they go off again, Turbo on / off. */
    private static String drawnWhileToggling(Ctx ctx, Fixture f, Object config, boolean turbo) throws Exception
    {
        Ctx.setField(config, "enabled", turbo);
        f.reload();
        ctx.ticks(5);
        f.awaitQuiet(turbo ? "Turbo on" : "plain Litematica");
        setShaders(ctx, true);
        ctx.ticks(40);
        boolean on = f.renderOrigins().isEmpty() == false;
        setShaders(ctx, false);
        ctx.ticks(40);
        boolean off = f.renderOrigins().isEmpty() == false;
        return "drawn with shaders on: " + on + ", after they went off: " + off;
    }

    /** The body with the test shader pack on; shaders are off again afterwards. */
    private static void withShaders(Ctx ctx, SelfTest.Body body) throws Exception
    {
        needs(ctx, "sodium");
        needs(ctx, "iris");
        freshStart(ctx);
        switchShaders(ctx, true);
        Fixture.Sig.shaderPackOn = true;

        try
        {
            body.run();
        }
        finally
        {
            Fixture.Sig.shaderPackOn = false;
            switchShaders(ctx, false);
        }
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("sodium/mods-loaded", () -> {
            needs(ctx, "sodium");
            freshStart(ctx);
            WorldTests.fixture(ctx);
            SelfTest.check(loaded(ctx, "litematica_turbo"), "Turbo is not loaded next to Sodium");
            noFeatureOff(ctx, "at the start");
        });

        tests.add("sodium/schematic-drawn", () -> {
            needs(ctx, "sodium");
            freshStart(ctx);
            schematicDrawn(ctx, "with Sodium", "sodium-check.png");
        });

        tests.add("sodium/speed-ups-work", () -> {
            needs(ctx, "sodium");
            freshStart(ctx);
            speedUps(tests, true);
        });

        tests.add("sodium/moving-a-placement", () -> {
            needs(ctx, "sodium");
            freshStart(ctx);
            rerun(tests, "move/ghost-shifted-by-the-distance");
            rerun(tests, "move/rebuilt-when-it-stops");
            rerun(tests, "copy/pieces-built-nearest-first-and-kept");
        });

        tests.add("sodium/no-feature-switched-off", () -> {
            needs(ctx, "sodium");
            freshStart(ctx);
            schematicDrawn(ctx, "before the check", "sodium-check.png");
            speedUps(tests, true);
            noFeatureOff(ctx, "after every speed-up ran");
        });

        tests.add("sodium/a-feature-error-still-switches-off", () -> {
            needs(ctx, "sodium");
            freshStart(ctx);
            rerun(tests, "far/error-switches-off-and-draws-every-outline");
            rerun(tests, "copy/error-switches-off-with-a-chat-line");
        });

        tests.add("sodium/screenshot", () -> {
            needs(ctx, "sodium");
            freshStart(ctx);
            schematicDrawn(ctx, "with Sodium", "sodium.png");
        });

        tests.add("sodium/no-gl-errors", () -> {
            needs(ctx, "sodium");
            noGlErrors("with Sodium");
        });

        tests.add("sodium/shaders-pack-in-use", () -> withShaders(ctx, () -> {
            SelfTest.check(shadersInUse(ctx), "Iris is not using the test shader pack");
            noGlErrors("with the shader pack on");
        }));

        tests.add("sodium/shaders-pause-batch-drawing", () -> withShaders(ctx, () -> {
            try
            {
                ctx.turbo("BatchDraw");
            }
            catch (ClassNotFoundException e)
            {
                throw SelfTest.skip("this render era has no batch drawing");
            }

            ctx.ticks(10);
            Class<?> batch = core(ctx, "BatchDrawing");
            SelfTest.check(Boolean.FALSE.equals(Ctx.call(batch, "active")), "batch drawing is still on with the shader pack on");
            SelfTest.check(Ctx.call(batch, "failure") == null, "batch drawing counts the pause as an error: " + Ctx.call(batch, "failure"));
            SelfTest.check(SafetyTests.chatText(ctx).contains("litematica_turbo.message.batch_paused"), "no chat line says batch drawing is paused:\n" + SafetyTests.chatText(ctx));
            SelfTest.check(shoot(ctx, "sodium-shaders-paused.png").colours() >= 20, "the schematic is not drawn while batch drawing is paused");
            switchShaders(ctx, false);
            ctx.ticks(10);
            SelfTest.check(Boolean.TRUE.equals(Ctx.call(batch, "active")), "batch drawing did not come back after the shaders were switched off");
            switchShaders(ctx, true);
        }));

        tests.add("sodium/shaders-schematic-drawn", () -> withShaders(ctx, () -> schematicDrawn(ctx, "with shaders", "sodium-shaders-check.png")));

        tests.add("sodium/shaders-speed-ups-work", () -> withShaders(ctx, () -> {
            speedUps(tests, false);
            noFeatureOff(ctx, "with shaders");
        }));

        tests.add("sodium/shaders-screenshot", () -> withShaders(ctx, () -> {
            schematicDrawn(ctx, "with shaders", "sodium-iris-shaders.png");
            noGlErrors("after the shaders screenshot");
        }));

        tests.add("sodium/shaders-toggle-like-stock", () -> {
            needs(ctx, "sodium");
            needs(ctx, "iris");
            freshStart(ctx);
            Fixture f = WorldTests.fixture(ctx);
            Object config = Ctx.call(core(ctx, "TurboConfig"), "get");

            try
            {
                String stock = drawnWhileToggling(ctx, f, config, false);
                String turbo = drawnWhileToggling(ctx, f, config, true);
                SelfTest.check(stock.equals(turbo), "switching shaders mid-game: plain Litematica " + stock + ", with Turbo " + turbo);
            }
            finally
            {
                Ctx.setField(config, "enabled", true);
                f.reload();
            }
        });

        tests.add("sodium/shaders-back-off", () -> {
            needs(ctx, "iris");
            freshStart(ctx);
            switchShaders(ctx, true);
            switchShaders(ctx, false);
            schematicDrawn(ctx, "with shaders switched off again", "sodium-shaders-back-off.png");
            noFeatureOff(ctx, "after shaders were switched on and off");
        });
    }
}
