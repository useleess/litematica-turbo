package com.litematicaturbo.selftest;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * sortcopy/: less memory for see-through sorting. Every test first lets plain Litematica (the setting off) build the test
 * schematics and counts which pieces hold re-sort copies and how big they are, then switches the setting on in the same
 * game. Pieces are found through the world renderer's own list, never by position on screen.
 */
final class SortCopyTests
{
    private static final String SORT = "com.litematicaturbo.core.SortCopies";
    private static final String OFF_KEY = "litematica_turbo.message.sortcopy_off";
    /** Litematica's rules in blocks, with a block of room for the camera standing between two blocks. */
    private static final double KEEP = 47.0;
    private static final double DROP = 65.0;
    /** The sweep promises a piece built while the camera stands still loses its copy within a second; twice that. */
    private static final int SWEEP_WAIT_TICKS = 40;

    private SortCopyTests()
    {
    }

    /** One piece of the schematic: where it is, how far its centre is from the camera, the copies it holds. */
    private static final class Piece
    {
        final String at;
        final long chunk;
        final double distance;
        final int copies;
        final long bytes;
        /** In Litematica's list of pieces in view: only those are built (again) right away. */
        final boolean inView;

        Piece(String at, long chunk, double distance, int copies, long bytes, boolean inView)
        {
            this.at = at;
            this.chunk = chunk;
            this.distance = distance;
            this.copies = copies;
            this.bytes = bytes;
            this.inView = inView;
        }

        @Override
        public String toString()
        {
            return this.at + String.format(Locale.ROOT, " (%.0f blocks, %d copies)", this.distance, this.copies);
        }
    }

    private static final class Census
    {
        final Map<String, Piece> pieces = new LinkedHashMap<>();

        long bytes()
        {
            return this.pieces.values().stream().mapToLong(p -> p.bytes).sum();
        }

        long holding()
        {
            return this.pieces.values().stream().filter(p -> p.copies > 0).count();
        }

        @Override
        public String toString()
        {
            return this.pieces.size() + " pieces, " + this.holding() + " holding copies, " + this.bytes() + " bytes";
        }
    }

    /** The size of one copy: its byte buffers and arrays (one centre per quad). */
    private static long bytes(Object copy) throws IllegalAccessException
    {
        long n = 0;

        for (Class<?> c = copy.getClass(); c != null && c != Object.class; c = c.getSuperclass())
        {
            for (Field f : c.getDeclaredFields())
            {
                if (Modifier.isStatic(f.getModifiers()))
                {
                    continue;
                }

                f.setAccessible(true);
                Object v = f.get(copy);

                if (v instanceof ByteBuffer)
                {
                    n += ((ByteBuffer) v).capacity();
                }
                else if (v != null && v.getClass().isArray())
                {
                    n += (long) Array.getLength(v) * (v.getClass().getComponentType().isPrimitive() ? 4 : 12);
                }
            }
        }

        return Math.max(1, n);
    }

    private static Object worldRenderer(Ctx ctx) throws Exception
    {
        return StatsTests.renderer(ctx);
    }

    /** Pieces of these chunks that held copies in plain Litematica and still hold some now. */
    private static List<Piece> keptAway(Census plain, Census now, Set<Long> chunks)
    {
        List<Piece> kept = new ArrayList<>();

        for (Piece p : plain.pieces.values())
        {
            Piece q = now.pieces.get(p.at);

            if (p.copies > 0 && chunks.contains(p.chunk) && q != null && q.copies > 0)
            {
                kept.add(q);
            }
        }

        return kept;
    }

    private static Census census(Ctx ctx) throws Exception
    {
        Class<?> sweep = ctx.turbo("SortCopySweep");

        return ctx.onMain(() -> {
            Census census = new Census();
            double[] camera = (double[]) Ctx.call(ctx.turbo("GhostRenderers"), "cameraPos");
            Set<Object> inView = Collections.newSetFromMap(new IdentityHashMap<>());
            inView.addAll((List<?>) Ctx.getField(worldRenderer(ctx), "renderInfos"));

            for (Object piece : (List<?>) Ctx.call(worldRenderer(ctx), "turbo$chunkRenderers"))
            {
                if (piece == null)
                {
                    continue;
                }

                Object origin = Ctx.call(piece, "getOrigin");
                int x = (Integer) Ctx.call(origin, "getX");
                int y = (Integer) Ctx.call(origin, "getY");
                int z = (Integer) Ctx.call(origin, "getZ");
                double dx = x + 8 - camera[0];
                double dy = y + 8 - camera[1];
                double dz = z + 8 - camera[2];
                long size = 0;
                List<?> copies = (List<?>) Ctx.call(sweep, "copies", piece);

                for (Object copy : copies)
                {
                    size += bytes(copy);
                }

                String at = x + "," + y + "," + z;
                census.pieces.put(at, new Piece(at, Fixture.key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), Math.sqrt(dx * dx + dy * dy + dz * dz), copies.size(), size, inView.contains(piece)));
            }

            return census;
        });
    }

    private static void setting(Ctx ctx, boolean on) throws Exception
    {
        Object config = Ctx.call(ctx.cls("com.litematicaturbo.core.TurboConfig"), "get");
        Ctx.setField(config, "enabled", true);
        Ctx.setField(config, "sortCopies", on);
    }

    private static boolean resorting(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> (Boolean) Ctx.call(ctx.turbo("SortCopySweep"), "resorting", worldRenderer(ctx)));
    }

    private static void teleport(Fixture f, double x, double z) throws Exception
    {
        f.game.command(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", x, f.playerY, z));
        f.ctx.ticks(3);
    }

    /** Where the in-world tests stand: the far zones (the see-through zone T among them) 80 blocks and more ahead. */
    private static void toStart(Fixture f) throws Exception
    {
        teleport(f, f.playerChunkX * 16 + 8.5, f.playerChunkZ * 16 + 8.5);
    }

    /** One chunk in front of zone T, looking at it: its first two rows within 40 blocks. */
    private static void toZoneT(Fixture f) throws Exception
    {
        teleport(f, (f.playerChunkX + 3) * 16 + 8.5, (f.playerChunkZ + 4) * 16 + 8.5);
    }

    /** The chunks of zone T whose centres are within 40 blocks (sideways) of the spot in front of it. */
    private static Set<Long> nearRowsOfT(Fixture f)
    {
        Set<Long> near = new HashSet<>();

        for (Long k : f.chunksOf("T"))
        {
            double dx = (Fixture.keyX(k) - f.playerChunkX - 3) * 16;
            double dz = (Fixture.keyZ(k) - f.playerChunkZ - 4) * 16;

            if (Math.sqrt(dx * dx + dz * dz) <= 40)
            {
                near.add(k);
            }
        }

        return near;
    }

    private static Fixture start(Ctx ctx) throws Exception
    {
        Fixture f = WorldTests.fixture(ctx);
        Ctx.call(ctx.cls(SORT), "resetForTest");
        f.configure(true, 3, true, true);
        toStart(f);
        return f;
    }

    /**
     * Where Litematica never sorts again with Turbo's build threads (before 1.19), they are switched off so the near rules
     * can be seen; elsewhere this changes nothing.
     */
    private static void withResorting(Ctx ctx, Fixture f) throws Exception
    {
        if (resorting(ctx) == false)
        {
            f.configure(true, -1, true, true);
            SelfTest.check(resorting(ctx), "re-sorting is still off with Turbo's build threads off");
        }
    }

    private static void finish(Ctx ctx, Fixture f) throws Exception
    {
        Ctx.call(ctx.cls(SORT), "resetForTest");
        setting(ctx, false);
        f.configure(true, 3, true, true);
        toStart(f);
    }

    /** Plain Litematica's copies: the setting off, every piece built again. */
    private static Census plain(Ctx ctx, Fixture f, Set<Long> chunks) throws Exception
    {
        setting(ctx, false);
        f.reload();
        ctx.ticks(3);
        f.awaitIdle("plain Litematica built", chunks, null);
        Census plain = census(ctx);
        SelfTest.check(plain.holding() > 0, "plain Litematica holds no re-sort copy at all (" + plain + "): nothing to test");
        return plain;
    }

    /** The setting on, one sweep done. */
    private static Census on(Ctx ctx) throws Exception
    {
        setting(ctx, true);
        Ctx.call(ctx.cls(SORT), "sweepSoon");
        ctx.ticks(5);
        return census(ctx);
    }

    /** Every piece in view that held copies in plain Litematica and is among these chunks (null = all) holds copies now. */
    private static void holdLikePlain(Census plain, Census now, Set<Long> chunks, double within, String when)
    {
        List<Piece> missing = new ArrayList<>();
        int compared = 0;

        for (Piece p : plain.pieces.values())
        {
            if (p.copies > 0 && p.distance <= within && (chunks == null || chunks.contains(p.chunk)))
            {
                Piece q = now.pieces.get(p.at);

                if (q != null && q.inView == false)
                {
                    continue;
                }

                ++compared;

                if (q == null || q.copies == 0)
                {
                    missing.add(p);
                }
            }
        }

        SelfTest.check(compared > 0, when + ": no piece to compare (plain: " + plain + ")");
        SelfTest.check(missing.isEmpty(), when + ": " + missing.size() + " of " + compared + " pieces hold no copy, unlike plain Litematica, e.g. " + missing.subList(0, Math.min(5, missing.size())));
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("sortcopy/far-pieces-hold-no-copy", () -> {
            Fixture f = start(ctx);

            try
            {
                Census plain = plain(ctx, f, f.allChunks());
                SelfTest.check(plain.pieces.values().stream().anyMatch(p -> p.copies > 0 && p.distance > DROP),
                               "no far piece holds a copy in plain Litematica (" + plain + "): nothing to test");
                Census on = on(ctx);
                boolean resorting = resorting(ctx);
                List<Piece> far = new ArrayList<>();

                for (Piece p : on.pieces.values())
                {
                    if (p.copies > 0 && (p.distance > DROP || resorting == false))
                    {
                        far.add(p);
                    }
                }

                SelfTest.check(far.isEmpty(), far.size() + " pieces still hold copies that must have been dropped (re-sorting "
                                              + (resorting ? "on: farther than 64 blocks" : "off: every one") + "), e.g. " + far.subList(0, Math.min(5, far.size())));
                SelfTest.check(on.bytes() * 2 <= plain.bytes(), "the copies held are not far below plain Litematica's: " + on + " vs plain " + plain);
                Agent.OUT.println("sortcopy: plain " + plain + ", setting on " + on);
            }
            finally
            {
                finish(ctx, f);
            }
        });

        tests.add("sortcopy/near-pieces-keep-copy", () -> {
            Fixture f = start(ctx);

            try
            {
                withResorting(ctx, f);
                toZoneT(f);
                Census plain = plain(ctx, f, nearRowsOfT(f));
                holdLikePlain(plain, on(ctx), null, KEEP, "setting on");
            }
            finally
            {
                finish(ctx, f);
            }
        });

        tests.add("sortcopy/rebuilt-when-near", () -> {
            Fixture f = start(ctx);
            Set<Long> rows = nearRowsOfT(f);

            try
            {
                withResorting(ctx, f);
                toZoneT(f);
                Census plain = plain(ctx, f, rows);
                Fixture.Capture expected = new Fixture.Capture("plain Litematica");
                expected.sigs.putAll(f.describeAll(rows));

                toStart(f);
                Census away = on(ctx);
                List<Piece> kept = keptAway(plain, away, rows);

                // a piece built or uploaded just before the sweep keeps its copy until the next one (within a second)
                for (int tick = 0; kept.isEmpty() == false && tick < SWEEP_WAIT_TICKS; tick += 5)
                {
                    ctx.ticks(5);
                    kept = keptAway(plain, census(ctx), rows);
                }

                SelfTest.check(kept.isEmpty(), "pieces of zone T kept their copies 80 blocks away, e.g. " + kept.subList(0, Math.min(5, kept.size())));

                toZoneT(f);
                ctx.ticks(5);
                f.awaitIdle("zone T built again", rows, null);
                holdLikePlain(plain, census(ctx), rows, KEEP, "back next to zone T");
                Fixture.Capture again = new Fixture.Capture("rebuilt near");
                again.sigs.putAll(f.describeAll(rows));
                WorldTests.same(expected, again, rows, "zone T rebuilt near the player");
            }
            finally
            {
                finish(ctx, f);
            }
        });

        tests.add("sortcopy/messages-translated", () -> {
            String lang = AuditTests.resource(ctx.cls(SORT).getClassLoader(), "assets/litematica_turbo/lang/en_us.json");
            SelfTest.check(lang != null, "no en_us.json");

            for (String key : new String[] {OFF_KEY, "config.name.turbosortcopies"})
            {
                SelfTest.check(lang.contains("\"" + key + "\": \""), "en_us.json has no English text for " + key);
            }

            Method translate = ctx.cls("fi.dy.masa.malilib.util.StringUtils").getMethod("translate", String.class, Object[].class);
            String text = (String) translate.invoke(null, OFF_KEY, new Object[] {"reason-4242"});
            SelfTest.check(text.equals(OFF_KEY) == false && text.contains("reason-4242"), "the message is not translated by the game: " + text);
        });

        tests.add("safety/sortcopy-off-after-error", () -> {
            Fixture f = start(ctx);
            Class<?> sort = ctx.cls(SORT);

            try
            {
                Census plain = plain(ctx, f, f.allChunks());
                Census on = on(ctx);
                SelfTest.check(on.holding() < plain.holding(), "no copy dropped before the error (" + on + " vs plain " + plain + "): nothing to test");

                Ctx.call(sort, "setFaultForTest", true);
                Ctx.call(sort, "sweepSoon");
                ctx.ticks(10);
                SelfTest.check(Ctx.call(sort, "failure") != null, "the error did not switch less memory for see-through sorting off");
                String chat = SafetyTests.chatText(ctx);
                SelfTest.check(chat.contains(OFF_KEY), "no chat line says less memory for see-through sorting is off:\n" + chat);
                SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");
                Ctx.call(sort, "setFaultForTest", false);

                ctx.ticks(3);
                f.awaitIdle("dropped pieces built again after the error", f.allChunks(), null);
                holdLikePlain(plain, census(ctx), f.allChunks(), Double.MAX_VALUE, "after the error");
            }
            finally
            {
                finish(ctx, f);
            }
        });
    }
}
