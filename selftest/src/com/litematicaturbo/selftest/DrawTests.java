package com.litematicaturbo.selftest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * draw/: batch drawing draws the built pieces of the schematic in groups of a 4 x 4 x 4 region with one call. Proved by
 * COUNTING draw calls per frame (never by time), by reading the frame back and comparing it with the plain piece-by-piece
 * drawing from the same camera, by a changed block that shows up, and by a failing batch that switches itself off with
 * a chat line while the drawing goes on. Only Minecraft versions of the render era that has batch drawing run them; the
 * others skip with the reason.
 */
final class DrawTests
{
    private static final String DRAWING = "com.litematicaturbo.core.BatchDrawing";
    private static final String OFF_KEY = "litematica_turbo.message.batch_off";
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    /** A pixel differs when one of its colour channels is further off than this. */
    private static final int CHANNEL_TOLERANCE = 2;
    /**
     * Batched vertices are computed relative to the region, not the piece, so they round differently in the last float
     * digit and some pixels on block edges fall on the other side of the edge. Such pixels are single ones; a missing or
     * misplaced piece changes thousands of pixels in areas. So the picture compare counts only pixels that are far off
     * and allows this share of the frame.
     */
    private static final int EDGE_TOLERANCE = 32;
    private static final int EDGE_SHARE = 300;

    private DrawTests()
    {
    }

    /** The frame as the game drew it: width, height, RGBA bytes. */
    private static final class Shot
    {
        final int width;
        final int height;
        final byte[] rgba;

        Shot(int width, int height, byte[] rgba)
        {
            this.width = width;
            this.height = height;
            this.rgba = rgba;
        }

        /** How many pixels differ from the other frame by more than the tolerance. */
        int differing(Shot other)
        {
            return this.differing(other, CHANNEL_TOLERANCE);
        }

        int differing(Shot other, int tolerance)
        {
            SelfTest.check(this.width == other.width && this.height == other.height, "the two frames have different sizes");
            int n = 0;

            for (int i = 0; i < this.rgba.length; i += 4)
            {
                for (int c = 0; c < 3; c++)
                {
                    if (Math.abs((this.rgba[i + c] & 0xFF) - (other.rgba[i + c] & 0xFF)) > tolerance)
                    {
                        n++;
                        break;
                    }
                }
            }

            return n;
        }

        /** Where the differing pixels are and how far off they are: for the failure message. */
        String where(Shot other, int tolerance)
        {
            int x0 = this.width, y0 = this.height, x1 = -1, y1 = -1, strong = 0;

            for (int i = 0; i < this.rgba.length; i += 4)
            {
                int worst = 0;

                for (int c = 0; c < 3; c++)
                {
                    worst = Math.max(worst, Math.abs((this.rgba[i + c] & 0xFF) - (other.rgba[i + c] & 0xFF)));
                }

                if (worst > tolerance)
                {
                    int x = i / 4 % this.width;
                    int y = i / 4 / this.width;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                    strong += worst > 64 ? 1 : 0;
                }
            }

            return "in x " + x0 + "-" + x1 + ", y " + y0 + "-" + y1 + " of " + this.width + " x " + this.height + ", " + strong + " of them by more than 64";
        }

        /** How many different colours (coarsely) the frame holds: a frame of one colour shows no schematic. */
        int colours()
        {
            java.util.Set<Integer> seen = new java.util.HashSet<>();

            for (int i = 0; i < this.rgba.length; i += 4)
            {
                seen.add(((this.rgba[i] & 0xFF) >> 3) << 10 | ((this.rgba[i + 1] & 0xFF) >> 3) << 5 | (this.rgba[i + 2] & 0xFF) >> 3);
            }

            return seen.size();
        }
    }

    private static Object config(Ctx ctx) throws ReflectiveOperationException
    {
        return Ctx.call(ctx.cls("com.litematicaturbo.core.TurboConfig"), "get");
    }

    private static void batch(Ctx ctx, boolean on) throws Exception
    {
        Ctx.setField(config(ctx), "enabled", true);
        Ctx.setField(config(ctx), "batchDraw", on);
    }

    private static long count(Ctx ctx, String what) throws Exception
    {
        return (Long) Ctx.call(ctx.cls(DRAWING), what);
    }

    /** Skips the test on a version whose render era has no batch drawing yet; otherwise gives it the test world and a clean start. */
    private static Fixture start(Ctx ctx) throws Exception
    {
        try
        {
            ctx.turbo("BatchDraw");
        }
        catch (ClassNotFoundException e)
        {
            throw SelfTest.skip("batch drawing for this render era comes in a later job");
        }

        Fixture f = WorldTests.fixture(ctx);
        Ctx.call(ctx.cls(DRAWING), "resetForTest");
        Ctx.call(ctx.cls(DRAWING), "setFaultForTest", false);
        batch(ctx, true);
        return f;
    }

    private static void finish(Ctx ctx) throws Exception
    {
        Ctx.call(ctx.cls(DRAWING), "resetForTest");
        batch(ctx, true);
    }

    /** Waits until regions have been made and no more are coming. */
    private static void awaitRegions(Ctx ctx) throws Exception
    {
        long deadline = System.currentTimeMillis() + 90_000L;
        long last = -1;
        int stable = 0;

        while (System.currentTimeMillis() < deadline && stable < 25)
        {
            ctx.ticks(1);
            long n = count(ctx, "batchedRegions");
            stable = n > 0 && n == last ? stable + 1 : 0;
            last = n;
        }

        SelfTest.check(stable >= 25, "no region was made, or regions kept being made, in 90 s (" + last + " made)");
    }

    /** Draw calls per frame over about a second of frames. */
    private static double callsPerFrame(Ctx ctx) throws Exception
    {
        long calls = count(ctx, "drawCalls");
        long frames = count(ctx, "frames");
        ctx.ticks(20);
        long frameCount = count(ctx, "frames") - frames;
        SelfTest.check(frameCount >= 5, "only " + frameCount + " frames were drawn in the time measured");
        return (double) (count(ctx, "drawCalls") - calls) / frameCount;
    }

    private static Shot shot(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> {
            Object window = ctx.cls("org.lwjgl.glfw.GLFW").getMethod("glfwGetCurrentContext").invoke(null);
            int[] w = new int[1];
            int[] h = new int[1];
            ctx.cls("org.lwjgl.glfw.GLFW").getMethod("glfwGetFramebufferSize", long.class, int[].class, int[].class).invoke(null, window, w, h);
            Class<?> gl11 = ctx.cls("org.lwjgl.opengl.GL11");
            Class<?> gl30 = ctx.cls("org.lwjgl.opengl.GL30");
            int fbo = mainFramebuffer(ctx, gl30);
            int previous = (Integer) gl11.getMethod("glGetInteger", int.class).invoke(null, GL_READ_FRAMEBUFFER_BINDING);
            ByteBuffer pixels = (ByteBuffer) ctx.cls("org.lwjgl.BufferUtils").getMethod("createByteBuffer", int.class).invoke(null, w[0] * h[0] * 4);
            Method bind = gl30.getMethod("glBindFramebuffer", int.class, int.class);

            try
            {
                bind.invoke(null, GL_READ_FRAMEBUFFER, fbo);
                gl11.getMethod("glReadPixels", int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class)
                    .invoke(null, 0, 0, w[0], h[0], GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            }
            finally
            {
                bind.invoke(null, GL_READ_FRAMEBUFFER, previous);
            }

            byte[] rgba = new byte[w[0] * h[0] * 4];
            pixels.get(rgba);
            return new Shot(w[0], h[0], rgba);
        });
    }

    /** The GL name of the game's main frame buffer: the int field of the client's frame buffer object that is a frame buffer. */
    private static int mainFramebuffer(Ctx ctx, Class<?> gl30) throws Exception
    {
        Object buffer = Game.fieldOfType(ctx.client, "Framebuffer", "RenderTarget");
        SelfTest.check(buffer != null, "the client has no frame buffer object");
        Method isFramebuffer = gl30.getMethod("glIsFramebuffer", int.class);

        for (Class<?> c = buffer.getClass(); c != null; c = c.getSuperclass())
        {
            for (Field field : c.getDeclaredFields())
            {
                if (field.getType() == int.class && java.lang.reflect.Modifier.isStatic(field.getModifiers()) == false)
                {
                    field.setAccessible(true);
                    int value = field.getInt(buffer);

                    if (value != 0 && (Boolean) isFramebuffer.invoke(null, value))
                    {
                        return value;
                    }
                }
            }
        }

        throw new SelfTest.Fail("none of the frame buffer object's numbers is a frame buffer");
    }

    private static void pieceByPiece(Ctx ctx, boolean on) throws Exception
    {
        ctx.onMain(() -> {
            Ctx.call(ctx.cls(DRAWING), "setPausedForTest", on);
            return null;
        });
    }

    /** Waits until the game has drawn one more frame of the schematic. */
    private static void nextFrame(Ctx ctx) throws Exception
    {
        long now = count(ctx, "frames");
        long deadline = System.currentTimeMillis() + 30_000L;

        while (count(ctx, "frames") <= now && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(2);
        }

        SelfTest.check(count(ctx, "frames") > now, "the game drew no frame in 30 s");
    }

    /**
     * The frame the game draws right after the switch: piece by piece (the region buffers are kept) or batched. Frames
     * next to each other, because the game's animated textures (water, lava, fire ...) change with time.
     */
    private static Shot frame(Ctx ctx, boolean pieceByPiece) throws Exception
    {
        pieceByPiece(ctx, pieceByPiece);
        nextFrame(ctx);
        return shot(ctx);
    }

    /**
     * Batched frames equal the plain ones: pairs of neighbouring frames, plain next to plain (what the plain drawing
     * varies by) and plain next to batched; a pair that falls on no animation step shows the real difference, so the
     * smallest differences are compared.
     */
    private static void samePicture(Ctx ctx, String what) throws Exception
    {
        SelfTest.check(((Integer) Ctx.call(ctx.turbo("BatchDraw"), "regionCount")) > 0, what + ": no region buffer exists");
        int natural = Integer.MAX_VALUE;
        int found = Integer.MAX_VALUE;
        Shot plain = null;
        String where = "";

        try
        {
            for (int i = 0; i < 8; i++)
            {
                plain = frame(ctx, true);
                natural = Math.min(natural, plain.differing(frame(ctx, true), EDGE_TOLERANCE));
                Shot batched = frame(ctx, false);
                int differing = plain.differing(batched, EDGE_TOLERANCE);

                if (differing < found)
                {
                    found = differing;
                    where = plain.where(batched, EDGE_TOLERANCE);
                }
            }
        }
        finally
        {
            pieceByPiece(ctx, false);
        }

        int pixels = plain.width * plain.height;
        SelfTest.check(plain.colours() >= 20, what + ": the plain frame shows almost no colour (" + plain.colours() + "): the schematic is not in view");
        SelfTest.check(found <= natural + pixels / EDGE_SHARE, what + ": at best " + found + " of " + pixels + " pixels differ by more than " + EDGE_TOLERANCE + " from the plain drawing, "
                       + "(" + where + ") while two plain frames next to each other differ by " + natural + " at best");
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("draw/fewer-calls-per-frame", () -> {
            start(ctx);

            try
            {
                batch(ctx, false);
                ctx.ticks(10);
                double plain = callsPerFrame(ctx);
                batch(ctx, true);
                awaitRegions(ctx);
                double batched = callsPerFrame(ctx);
                SelfTest.check(plain >= 12, "the test scene needs " + plain + " draw calls per frame piece by piece: too few to show a difference");
                SelfTest.check(batched * 2 <= plain, "batch drawing needs " + batched + " draw calls per frame, drawing piece by piece " + plain + ": not far fewer");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("draw/same-picture", () -> {
            start(ctx);

            try
            {
                awaitRegions(ctx);
                samePicture(ctx, "batched frame");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("draw/changed-block-shows-up", () -> {
            Fixture f = start(ctx);
            Object world = f.schematicWorld();
            Object pos = f.game.blockPos(f.playerChunkX * 16 + 8, f.nearY + 2, (f.playerChunkZ + 1) * 16 + 8);
            Object original = null;

            try
            {
                awaitRegions(ctx);
                Shot plain = frame(ctx, true);
                int natural = plain.differing(frame(ctx, true));
                pieceByPiece(ctx, false);
                String current = String.valueOf(ctx.onMain(() -> Ctx.call(world, "getBlockState", pos)));
                Object state = f.stateNamed("minecraft:gold_block");
                SelfTest.check(state != null, "the block registry has no gold block");

                if (String.valueOf(state).equals(current))
                {
                    state = f.stateNamed("minecraft:emerald_block");
                }

                for (Object candidate : f.statesForText(current))
                {
                    original = candidate;
                }

                Object edit = state;
                ctx.onMain(() -> {
                    Game.callAny(world, new String[] {"setBlockState", "setBlock"}, pos, edit, 3);
                    Ctx.call(Ctx.getStatic(ctx.cls("fi.dy.masa.litematica.util.SchematicWorldRefresher"), "INSTANCE"), "markSchematicChunkForRenderUpdate", pos);
                    return null;
                });

                boolean shown = false;

                for (int tries = 0; tries < 60 && shown == false; tries++)
                {
                    ctx.ticks(3);
                    shown = plain.differing(frame(ctx, true)) > natural + 20;
                }

                pieceByPiece(ctx, false);
                SelfTest.check(shown, "the changed block does not show in the picture");
                ctx.ticks(40);
                awaitRegions(ctx);
                samePicture(ctx, "batched frame after the change");
            }
            finally
            {
                Object restore = original;

                if (restore != null)
                {
                    ctx.onMain(() -> {
                        Game.callAny(world, new String[] {"setBlockState", "setBlock"}, pos, restore, 3);
                        Ctx.call(Ctx.getStatic(ctx.cls("fi.dy.masa.litematica.util.SchematicWorldRefresher"), "INSTANCE"), "markSchematicChunkForRenderUpdate", pos);
                        return null;
                    });
                }

                finish(ctx);
            }
        });

        tests.add("draw/setting-off-draws-piece-by-piece", () -> {
            start(ctx);

            try
            {
                awaitRegions(ctx);
                SelfTest.check(((Integer) Ctx.call(ctx.turbo("BatchDraw"), "regionCount")) > 0, "no region buffer exists with the setting on");
                batch(ctx, false);
                ctx.ticks(5);
                SelfTest.equal(0, Ctx.call(ctx.turbo("BatchDraw"), "regionCount"), "region buffers kept with the setting off");
                long regions = count(ctx, "batchedRegions");
                ctx.ticks(30);
                SelfTest.equal(regions, count(ctx, "batchedRegions"), "regions made with the setting off");
                SelfTest.check(callsPerFrame(ctx) >= 12, "almost nothing is drawn with the setting off");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("draw/error-switches-off-and-draws-piece-by-piece", () -> {
            start(ctx);

            try
            {
                awaitRegions(ctx);
                Ctx.call(ctx.cls(DRAWING), "setFaultForTest", true);
                ctx.ticks(10);
                SelfTest.check(Ctx.call(ctx.cls(DRAWING), "failure") != null, "the error did not switch batch drawing off");
                SelfTest.equal(0, Ctx.call(ctx.turbo("BatchDraw"), "regionCount"), "region buffers kept after the error");
                String chat = SafetyTests.chatText(ctx);
                SelfTest.check(chat.contains(OFF_KEY), "no chat line says batch drawing is off:\n" + chat);
                SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");

                Ctx.call(ctx.cls(DRAWING), "setFaultForTest", false);
                ctx.ticks(10);
                SelfTest.check(callsPerFrame(ctx) >= 12, "nothing is drawn after the error");
                SelfTest.equal(0, Ctx.call(ctx.turbo("BatchDraw"), "regionCount"), "batch drawing came back after it switched itself off");
                SelfTest.check(shot(ctx).colours() >= 20, "the schematic is not drawn after the error");
            }
            finally
            {
                finish(ctx);
            }
        });

        tests.add("draw/messages-translated", () -> {
            start(ctx);
            String lang = AuditTests.resource(ctx.cls(DRAWING).getClassLoader(), "assets/litematica_turbo/lang/en_us.json");
            SelfTest.check(lang != null, "no en_us.json");

            for (String key : new String[] {OFF_KEY, "config.name.turbobatchdraw"})
            {
                SelfTest.check(lang.contains("\"" + key + "\": \""), "en_us.json has no English text for " + key);
            }

            Method translate = ctx.cls("fi.dy.masa.malilib.util.StringUtils").getMethod("translate", String.class, Object[].class);
            String text = (String) translate.invoke(null, OFF_KEY, new Object[] {"reason-4242"});
            SelfTest.check(text.equals(OFF_KEY) == false && text.contains("reason-4242"), "the message is not translated by the game: " + text);
        });
    }
}
