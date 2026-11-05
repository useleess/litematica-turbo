package com.litematicaturbo.selftest;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the in-world tests (build/, near/, empty/) share: the test world, a set of test schematics placed in it, and
 * "captures": the schematic world renderer reloaded under one Turbo configuration, waited until every chunk is
 * built, then every chunk's build result described by {@link Digest}. A capture is made once and kept, tests only
 * compare captures.
 *
 * Zones (chunk offsets from the player's chunk, the player looks along +Z): near N (3 x 3 around the player),
 * far A (every block state without a block entity), B (every block state with a block entity), S (sparse: a few
 * blocks in a column of the full world height, most chunks and sections empty), T (translucent / transparent
 * blocks), F (fluids), E (blocks along chunk borders). Everything is generated from the game's block registry.
 */
final class Fixture
{
    static final class Zone
    {
        final String name;
        final int dx;
        final int dz;
        final int size;
        final List<Long> chunks = new ArrayList<>();

        Zone(String name, int dx, int dz, int size)
        {
            this.name = name;
            this.dx = dx;
            this.dz = dz;
            this.size = size;
        }
    }

    /** One chunk's build result: the description lines and a short hash of them. */
    static final class Sig
    {
        final List<String> lines;
        final String hash;

        /**
         * The numbers of the overlay (outline / quad) buffers - counts, sizes, positions to the last float digit - differ
         * from build to build in plain Litematica itself (seen: two builds of the same chunk, Turbo off, 251680 vs
         * 251600), so they cannot tell Turbo's build from Litematica's. Which overlay types exist and the other flags of
         * the overlay, and everything about the block geometry, is still compared. Upload counters are left out too.
         */
        private static final java.util.regex.Pattern OVERLAY_COUNT = java.util.regex.Pattern.compile("(?i).*overlay[^=]*=.*[0-9].*|.*overlay.*primitiveCenters.*|.*[.](generation|capacity)=-?[0-9]+$|.*this[$]0[.].*");

        /**
         * Only where Turbo does not make it (before 1.21.10, no IrisBlockBuffers): with an Iris shader pack on, the vertex format of a block buffer (plain, or Iris's extended one with mc_Entity ...)
         * depends on whether the build happened inside Iris's world-render phase. Seen in two runs, in opposite directions:
         * plain Litematica itself had the plain format in one and the extended one in the other. The geometry is still compared.
         */
        private static final java.util.regex.Pattern BUFFER_FORMAT = java.util.regex.Pattern.compile(".*drawParameters[.]format=.*");

        /** Set while the test shader pack is on in a render era where nothing makes the buffers' format follow it (SodiumTests.withShaders). */
        static volatile boolean ignoreBufferFormat;

        Sig(List<String> all)
        {
            List<String> lines = new java.util.ArrayList<>();

            for (String l : all)
            {
                if (OVERLAY_COUNT.matcher(l).matches() == false && (ignoreBufferFormat == false || BUFFER_FORMAT.matcher(l).matches() == false))
                {
                    lines.add(l);
                }
            }

            this.lines = lines;
            this.hash = Integer.toHexString(lines.hashCode());
        }

        private static int differing(List<String> a, List<String> b)
        {
            int count = Math.abs(a.size() - b.size());

            for (int i = 0; i < Math.min(a.size(), b.size()); i++)
            {
                if (a.get(i).equals(b.get(i)) == false)
                {
                    ++count;
                }
            }

            return count;
        }

        /** Two lines from the end of their field path on: the block layer in front of it is long enough to hide the difference in a cut message. */
        private static String difference(String a, String b)
        {
            int same = 0;

            while (same < a.length() && same < b.length() && a.charAt(same) == b.charAt(same))
            {
                ++same;
            }

            int from = Math.max(0, same - 60);
            return "'..." + a.substring(from) + "' vs '..." + b.substring(from) + "'";
        }

        /** Why two descriptions differ: the first line that is not the same. */
        String diff(Sig other)
        {
            int n = Math.min(this.lines.size(), other.lines.size());

            for (int i = 0; i < n; i++)
            {
                if (this.lines.get(i).equals(other.lines.get(i)) == false)
                {
                    StringBuilder all = new StringBuilder(differing(this.lines, other.lines) + " lines differ: " + difference(this.lines.get(i), other.lines.get(i)));
                    int shown = 1;

                    for (int j = i + 1; j < n && shown < 5; j++)
                    {
                        if (this.lines.get(j).equals(other.lines.get(j)) == false)
                        {
                            all.append(" | ").append(difference(this.lines.get(j), other.lines.get(j)));
                            ++shown;
                        }
                    }

                    return all.toString();
                }
            }

            return this.lines.size() + " lines vs " + other.lines.size() + " lines";
        }

        /** How many 16-high parts of the chunk have blocks built (the build result says "not empty"). */
        int built()
        {
            int n = 0;

            for (String l : this.lines)
            {
                if (l.matches(".*data[.](empty|blocksEmpty)=false"))
                {
                    ++n;
                }
            }

            return n;
        }

        int count(String prefixOrPart)
        {
            int n = 0;

            for (String l : this.lines)
            {
                if (l.contains(prefixOrPart))
                {
                    ++n;
                }
            }

            return n;
        }
    }

    /** One reload of the renderers under one configuration. */
    static final class Capture
    {
        final String label;
        final Map<Long, Sig> sigs = new LinkedHashMap<>();
        long builds;
        long mainThreadNanos;
        long scannedSections;
        long skippedSections;
        int threads;
        long millis;

        Capture(String label)
        {
            this.label = label;
        }
    }

    private static Fixture instance;
    private static String failure;

    final Ctx ctx;
    final Game game;
    final Map<String, Zone> zones = new LinkedHashMap<>();
    final Map<String, Capture> captures = new HashMap<>();
    /** A capture that failed once is not retried by every test that needs it (each retry would cost its full wait). */
    final Map<String, String> captureFailures = new HashMap<>();
    int playerX, playerY, playerZ, playerChunkX, playerChunkZ, bottom, height;
    /** Lowest layer of the near zone N: a little above the player's feet, inside the 32-block near rule. */
    int nearY;
    int lastOriginY;
    final List<Object> states = new ArrayList<>();
    final Class<?> configClass;
    final Class<?> statsClass;
    private final Object config;
    private final Object schematicWorld;
    private final Object worldRenderer;
    private final Set<Long> expected = new LinkedHashSet<>();
    private final Set<Long> blockless = new LinkedHashSet<>();

    static synchronized Fixture get(Ctx ctx) throws Exception
    {
        if (failure != null)
        {
            throw new SelfTest.Fail(failure);
        }

        if (instance == null)
        {
            try
            {
                instance = new Fixture(ctx);
            }
            catch (SelfTest.Skip s)
            {
                failure = null;
                throw s;
            }
            catch (Throwable t)
            {
                failure = "the in-world test setup failed: " + t;
                t.printStackTrace(Agent.OUT);
                throw new SelfTest.Fail(failure);
            }
        }

        return instance;
    }

    static long key(int cx, int cz)
    {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    static int keyX(long key)
    {
        return (int) (key >> 32);
    }

    static int keyZ(long key)
    {
        return (int) key;
    }

    private Fixture(Ctx ctx) throws Exception
    {
        this.ctx = ctx;
        this.game = new Game(ctx);
        this.configClass = ctx.cls("com.litematicaturbo.core.TurboConfig");
        this.statsClass = ctx.cls("com.litematicaturbo.core.BuildStats");
        this.config = Ctx.call(this.configClass, "get");

        this.game.join("turbo-test");
        // Litematica's "near" rule measures to a renderer that stands on the bottom of the world (one per chunk column),
        // so the player goes down there and floats (spectator mode, no gravity): only then is anything "near"
        this.game.command("gamemode spectator");
        // ... in the middle of its chunk, so the three chunks right in front are all within 32 blocks
        int[] spawn = ctx.onMain(this.game::playerPos);
        this.game.command("tp @s " + (Math.floorDiv(spawn[0], 16) * 16 + 8.5) + " " + (ctx.onMain(this.game::bottomY) + 6) + " " + (Math.floorDiv(spawn[2], 16) * 16 + 8.5) + " 0 0"); // facing +Z, where the zones are

        // the player may still be falling onto the ground while the world loads: wait until the position holds still
        int[] p = ctx.onMain(this.game::playerPos);
        long settleEnd = System.currentTimeMillis() + 60_000L;
        int stillFor = 0;

        while ((stillFor < 20 || p[1] > ctx.onMain(this.game::bottomY) + 30) && System.currentTimeMillis() < settleEnd)
        {
            ctx.ticks(1);
            int[] q = ctx.onMain(this.game::playerPos);
            stillFor = Arrays.equals(p, q) ? stillFor + 1 : 0;
            p = q;
        }

        this.playerX = p[0];
        this.playerY = p[1];
        this.playerZ = p[2];
        this.playerChunkX = Math.floorDiv(p[0], 16);
        this.playerChunkZ = Math.floorDiv(p[2], 16);
        this.bottom = ctx.onMain(this.game::bottomY);
        this.nearY = this.playerY + 2;
        this.height = ctx.onMain(this.game::worldHeight);
        this.states.addAll(ctx.onMain(this.game::allBlockStates));

        this.enableRendering();
        this.zone("N", -1, 1, 3); // in front of the player: Litematica builds only chunks in the view
        this.zone("A", -4, 5, 3);
        this.zone("S", -1, 5, 3);
        this.zone("T", 2, 5, 3);
        this.zone("B", -4, 9, 3);
        this.zone("F", -1, 9, 3);
        this.zone("E", 2, 9, 3);

        for (Zone z : this.zones.values())
        {
            this.expected.addAll(z.chunks);
        }

        // the two chunks of the sparse zone that have no block at all: some versions give them no renderer
        Long[] sparse = this.zones.get("S").chunks.toArray(new Long[0]);
        this.blockless.add(sparse[1]);
        this.blockless.add(sparse[3]);

        this.placeSchematics();
        this.schematicWorld = Ctx.call(ctx.cls("fi.dy.masa.litematica.world.SchematicWorldHandler"), "getSchematicWorld");
        this.worldRenderer = StatsTests.renderer(ctx);

        if (this.schematicWorld == null)
        {
            throw new SelfTest.Fail("Litematica has no schematic world after placing the test schematics");
        }

        // the first build while the placements are still being copied into the schematic world is not a result
        this.awaitIdle("test schematics copied and built", this.expected, null);
    }

    private void zone(String name, int dx, int dz, int size)
    {
        Zone z = new Zone(name, dx, dz, size);

        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                z.chunks.add(key(this.playerChunkX + dx + i, this.playerChunkZ + dz + j));
            }
        }

        this.zones.put(name, z);
    }

    /**
     * The player looks along +Z at the zones. Called again while waiting: on the test box the cursor of a grabbed game
     * window is moved by other windows being tiled / started, which turned the camera and emptied the view.
     */
    void aim()
    {
        try
        {
            Object player = this.game.player();

            for (String f : new String[] {"yaw", "prevYaw", "yRot", "yRotO", "pitch", "prevPitch", "xRot", "xRotO", "lastYaw", "lastPitch"})
            {
                try
                {
                    Ctx.field(player.getClass(), f).setFloat(player, 0.0f);
                }
                catch (ReflectiveOperationException | RuntimeException e)
                {
                    // other name or type
                }
            }
        }
        catch (ReflectiveOperationException e)
        {
            // looking is a convenience: the far zones are in front of a player who has not turned
        }
    }

    /** The player's yaw / pitch, for failure messages: other windows on the test box can turn a grabbed camera (see aim). */
    String cameraAngles() throws Exception
    {
        Object player = this.game.player();
        return angle(player, "yaw", "yRot") + " / " + angle(player, "pitch", "xRot");
    }

    /** The field of the player, by the name it has in this version (as in aim). */
    private static float angle(Object player, String... names) throws ReflectiveOperationException
    {
        for (String name : names)
        {
            try
            {
                return Ctx.field(player.getClass(), name).getFloat(player);
            }
            catch (NoSuchFieldException otherName)
            {
                // the next name
            }
        }

        throw new NoSuchFieldException(String.join(" / ", names));
    }

    /** Switches the drawing (and collecting) of the schematic's block entities, Litematica's "render tile entities" option. */
    void drawBlockEntities(boolean on) throws Exception
    {
        this.ctx.onMain(() -> {
            this.setBlockEntityDrawing(on);
            return null;
        });
    }

    /** On the render thread already. */
    private void setBlockEntityDrawing(boolean on)
    {
        try
        {
            Ctx.call(Ctx.getStatic(this.ctx.cls("fi.dy.masa.litematica.config.Configs$Visuals"), "RENDER_SCHEMATIC_TILE_ENTITIES"), "setBooleanValue", on);
        }
        catch (ReflectiveOperationException e)
        {
            // not an option on this version
        }
    }

    private void enableRendering() throws Exception
    {
        this.ctx.onMain(() -> {
            Class<?> visuals = this.ctx.cls("fi.dy.masa.litematica.config.Configs$Visuals");

            for (String option : new String[] {"ENABLE_RENDERING", "ENABLE_SCHEMATIC_RENDERING"})
            {
                try
                {
                    Ctx.call(Ctx.getStatic(visuals, option), "setBooleanValue", true);
                }
                catch (ReflectiveOperationException e)
                {
                    // not an option on this version
                }
            }

            this.aim();

            return null;
        });
    }

    // ---- the test schematics ----

    private int[] lcg(int seed)
    {
        return new int[] {seed};
    }

    private static int next(int[] state, int bound)
    {
        state[0] = state[0] * 1103515245 + 12345;
        return ((state[0] >>> 8) & 0x7fffff) % bound;
    }

    private List<Object> statesNamed(String... prefixes)
    {
        List<Object> out = new ArrayList<>();

        for (String prefix : prefixes)
        {
            for (Object s : this.states)
            {
                if (String.valueOf(s).startsWith("Block{" + prefix + "}"))
                {
                    out.add(s);
                    break;
                }
            }
        }

        return out;
    }

    private List<Object> statesContaining(int limit, String... keywords)
    {
        List<Object> out = new ArrayList<>();

        for (Object s : this.states)
        {
            String text = Game.describe(s)[0];

            for (String k : keywords)
            {
                if (text.contains(k))
                {
                    out.add(s);
                    break;
                }
            }
        }

        return limit > 0 && out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private boolean isAir(Object s)
    {
        String n = Game.describe(s)[0];
        return n.equals("minecraft:air") || n.equals("minecraft:cave_air") || n.equals("minecraft:void_air");
    }

    /** palette index of a state, adding it. */
    private int paletteIndex(Map<String, Integer> index, List<String[]> palette, Object state)
    {
        String text = String.valueOf(state);
        Integer i = index.get(text);

        if (i == null)
        {
            i = palette.size();
            index.put(text, i);
            palette.add(Game.describe(state));
        }

        return i;
    }

    private void placeSchematics() throws Exception
    {
        File dir = new File(System.getProperty("user.dir"), "schematics");
        Class<?> schematicClass = this.ctx.cls("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        int dataVersion = (Integer) Ctx.getStatic(schematicClass, "MINECRAFT_DATA_VERSION");
        int version = (Integer) Ctx.getStatic(schematicClass, "SCHEMATIC_VERSION");
        List<Object> plain = new ArrayList<>();
        List<Object> withEntity = new ArrayList<>();

        boolean[] entity = this.ctx.onMain(() -> {
            boolean[] flags = new boolean[this.states.size()];

            for (int i = 0; i < flags.length; i++)
            {
                flags[i] = this.game.hasBlockEntity(this.states.get(i));
            }

            return flags;
        });

        for (int i = 0; i < this.states.size(); i++)
        {
            Object s = this.states.get(i);

            if (this.isAir(s) == false)
            {
                (entity[i] ? withEntity : plain).add(s);
            }
        }

        List<Object> stone = this.statesNamed("minecraft:stone");
        List<Object> mixed = this.statesNamed("minecraft:stone", "minecraft:glass", "minecraft:oak_planks", "minecraft:chest", "minecraft:oak_leaves",
                                              "minecraft:water", "minecraft:lava", "minecraft:white_stained_glass", "minecraft:torch", "minecraft:oak_stairs");

        if (stone.isEmpty() || mixed.size() < 3)
        {
            throw new SelfTest.Fail("the block registry has no stone / glass / planks");
        }

        Class<?> holderClass = this.ctx.cls("fi.dy.masa.litematica.data.SchematicHolder");
        Class<?> placementClass = this.ctx.cls("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        Object manager = Ctx.call(this.ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
        Object holder = Ctx.call(holderClass, "getInstance");

        for (Zone z : this.zones.values())
        {
            int sx = z.size * 16;
            int sz = z.size * 16;
            int sy;
            int originY = this.bottom + 8;
            int[] rnd = this.lcg(z.name.charAt(0) * 7919);
            Map<String, Integer> index = new HashMap<>();
            List<String[]> palette = new ArrayList<>();
            palette.add(new String[] {"minecraft:air", ""});
            index.put("air", 0);
            int[] cells;

            switch (z.name)
            {
                case "A":
                case "B":
                {
                    List<Object> list = z.name.equals("A") ? plain : withEntity;
                    int perLayer = sx * sz;
                    sy = Math.max(1, (list.size() + perLayer - 1) / perLayer);
                    cells = new int[sx * sy * sz];

                    for (int i = 0; i < list.size(); i++)
                    {
                        cells[i] = this.paletteIndex(index, palette, list.get(i));
                    }

                    break;
                }
                case "S":
                {
                    // the full world height; blocks at the very bottom and top, on section borders and a few in between
                    sy = this.height;
                    originY = this.bottom;
                    cells = new int[sx * sy * sz];
                    int[][] spots = {
                        {1, 0, 1}, {2, 6, 2}, {17, 40, 33}, {30, sy - 1, 30}, {47, sy - 1, 0}, {0, 15, 40}, {1, 16, 40}, {47, 100, 47}, {20, 63, 20}, {33, 64, 20}};

                    for (int i = 0; i < spots.length; i++)
                    {
                        cells[(spots[i][1] * sz + spots[i][2]) * sx + spots[i][0]] = this.paletteIndex(index, palette, mixed.get(i % mixed.size()));
                    }

                    break;
                }
                case "T":
                case "F":
                {
                    List<Object> pool = z.name.equals("T")
                        ? this.statesContaining(0, "glass", "ice", "slime", "honey", "leaves", "web", "vine", "pane", "water", "bars", "sapling", "flower", "fern", "torch")
                        : this.statesContaining(0, "water", "lava", "kelp", "seagrass", "bubble_column");
                    List<Object> waterlogged = new ArrayList<>();

                    for (Object s : this.states)
                    {
                        if (Game.describe(s)[1].contains("waterlogged=true") && waterlogged.size() < 120)
                        {
                            waterlogged.add(s);
                        }
                    }

                    if (z.name.equals("F"))
                    {
                        pool.addAll(waterlogged);
                    }

                    pool.addAll(stone);
                    sy = z.name.equals("T") ? 12 : 8;
                    cells = new int[sx * sy * sz];
                    int density = z.name.equals("T") ? 60 : 75;

                    for (int i = 0; i < cells.length; i++)
                    {
                        if (next(rnd, 100) < density)
                        {
                            cells[i] = this.paletteIndex(index, palette, pool.get(next(rnd, pool.size())));
                        }
                    }

                    break;
                }
                case "E":
                {
                    sy = 10;
                    cells = new int[sx * sy * sz];

                    for (int y = 0; y < sy; y++)
                    {
                        for (int zz = 0; zz < sz; zz++)
                        {
                            for (int x = 0; x < sx; x++)
                            {
                                boolean border = x % 16 == 15 || x % 16 == 0 || zz % 16 == 15 || zz % 16 == 0;

                                if (border && next(rnd, 100) < 80)
                                {
                                    cells[(y * sz + zz) * sx + x] = this.paletteIndex(index, palette, mixed.get(next(rnd, mixed.size())));
                                }
                            }
                        }
                    }

                    break;
                }
                default: // N: a few blocks overhead, 10 up from the world floor, within 8 blocks of every chunk border too
                {
                    sy = 6;
                    originY = this.nearY;
                    cells = new int[sx * sy * sz];

                    for (int i = 0; i < cells.length; i++)
                    {
                        if (next(rnd, 100) < 6)
                        {
                            cells[i] = this.paletteIndex(index, palette, mixed.get(next(rnd, mixed.size())));
                        }
                    }
                }
            }

            File file = new File(dir, "turbo-test-" + z.name + ".litematic");
            WorldFiles.writeSchematic(file, dataVersion, version, "turbo " + z.name, sx, sy, sz, palette, cells);
            Object schematic = this.load(holder, file);
            int ox = (this.playerChunkX + z.dx) * 16;
            int oz = (this.playerChunkZ + z.dz) * 16;
            int oy = originY;
            this.ctx.onMain(() -> {
                Object origin = this.game.blockPos(ox, oy, oz);
                Object placement = Ctx.call(placementClass, "createFor", schematic, origin, "turbo-" + z.name, true, true);
                Ctx.call(manager, "addSchematicPlacement", placement, false);
                return null;
            });
        }
    }

    /**
     * Litematica's load of a schematic file. On a box that is short of memory or disk speed the read can fail for a while
     * (the file is not yet complete on disk, the game is stalled): ask again until the file is readable, up to a minute.
     */
    private Object load(Object holder, File file) throws Exception
    {
        long deadline = System.currentTimeMillis() + 60_000L;
        long size = -1;

        while (true)
        {
            long now = file.length();
            boolean settled = now > 0 && now == size;
            size = now;
            Object schematic = settled ? this.tryLoad(holder, file) : null;

            if (schematic != null)
            {
                return schematic;
            }

            if (System.currentTimeMillis() > deadline)
            {
                throw new SelfTest.Fail("Litematica could not load the test schematic " + file + " (" + now + " bytes, " + (file.exists() ? "exists" : "missing") + "); " + this.loadErrorsInLog() + "; chat: " + SafetyTests.chatText(this.ctx));
            }

            Thread.sleep(500);
        }
    }

    /** The last log lines that name a schematic load problem: why Litematica gave back nothing. */
    private String loadErrorsInLog()
    {
        try
        {
            List<String> hits = new ArrayList<>();

            for (String line : java.nio.file.Files.readAllLines(new File("logs/latest.log").toPath(), java.nio.charset.StandardCharsets.UTF_8))
            {
                String l = line.toLowerCase();

                if (l.contains("litematic") && (l.contains("error") || l.contains("warn") || l.contains("exception") || l.contains("fail")))
                {
                    hits.add(line.trim());
                }
            }

            return "log: " + hits.subList(Math.max(0, hits.size() - 6), hits.size());
        }
        catch (Exception e)
        {
            return "log unreadable: " + e;
        }
    }

    private Object tryLoad(Object holder, File file) throws Exception
    {
        try
        {
            return this.ctx.onMain(() -> Ctx.call(holder, "getOrLoad", file));
        }
        catch (NoSuchMethodException e)
        {
            return this.ctx.onMain(() -> Ctx.call(holder, "getOrLoad", file.toPath()));
        }
    }

    // ---- a lone placement for the move tests ----

    /** Block position (relative to the lone placement's origin) of the marker block that is always in it. */
    static final int[] MARKER = {3, 2, 3};

    /**
     * Switches every placement of the standard test zones on or off. Turbo moves a placement the cheap way only
     * while it is the only enabled one, so the move tests switch the zones off and use a lone placement.
     */
    void zonesEnabled(boolean enabled, String... only) throws Exception
    {
        List<String> names = new ArrayList<>();

        for (String n : only)
        {
            names.add("turbo-" + n);
        }

        this.ctx.onMain(() -> {
            Object manager = Ctx.call(this.ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");

            for (Object placement : new ArrayList<Object>((java.util.Collection<?>) Ctx.call(manager, "getAllSchematicsPlacements")))
            {
                String name = String.valueOf(Ctx.call(placement, "getName"));

                if (name.startsWith("turbo-") && name.startsWith("turbo-lone-") == false && (names.isEmpty() || names.contains(name)) && (Boolean) Ctx.call(placement, "isEnabled") != enabled)
                {
                    Ctx.call(placement, "toggleEnabled");
                }
            }

            return null;
        });
        this.ctx.ticks(2);
    }

    /**
     * A new lone test placement: 2 x 2 chunks, a sprinkle of mixed blocks overhead plus the marker block, whose
     * origin is at the first chunk of zone N's near row. Returns the placement.
     */
    Object placeLone(String name) throws Exception
    {
        return this.placeLone(name, name);
    }

    /** Writes the 32 x 6 x 32 lone schematic file {@code name} holding the random blocks of {@code seed}. */
    File writeLone(String name, String seed) throws Exception
    {
        File dir = new File(System.getProperty("user.dir"), "schematics");
        Class<?> schematicClass = this.ctx.cls("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        int dataVersion = (Integer) Ctx.getStatic(schematicClass, "MINECRAFT_DATA_VERSION");
        int version = (Integer) Ctx.getStatic(schematicClass, "SCHEMATIC_VERSION");
        List<Object> mixed = this.statesNamed("minecraft:stone", "minecraft:glass", "minecraft:oak_planks", "minecraft:oak_leaves", "minecraft:water",
                                              "minecraft:white_stained_glass", "minecraft:torch", "minecraft:oak_stairs");
        Object gold = this.stateNamed("minecraft:gold_block");
        SelfTest.check(gold != null && mixed.size() >= 3, "the block registry has no gold block / stone / glass");
        int sx = 32, sy = 6, sz = 32;
        Map<String, Integer> index = new HashMap<>();
        List<String[]> palette = new ArrayList<>();
        palette.add(new String[] {"minecraft:air", ""});
        index.put("air", 0);
        int[] cells = new int[sx * sy * sz];
        int[] rnd = this.lcg(seed.hashCode());

        for (int i = 0; i < cells.length; i++)
        {
            if (next(rnd, 100) < 7)
            {
                cells[i] = this.paletteIndex(index, palette, mixed.get(next(rnd, mixed.size())));
            }
        }

        cells[(MARKER[1] * sz + MARKER[2]) * sx + MARKER[0]] = this.paletteIndex(index, palette, gold);
        File file = new File(dir, "turbo-test-" + name + ".litematic");
        WorldFiles.writeSchematic(file, dataVersion, version, "turbo " + name, sx, sy, sz, palette, cells);
        return file;
    }

    Object placeLone(String name, String seed) throws Exception
    {
        File file = this.writeLone(name, seed);
        Object holder = Ctx.call(this.ctx.cls("fi.dy.masa.litematica.data.SchematicHolder"), "getInstance");
        Object schematic = this.load(holder, file);
        Class<?> placementClass = this.ctx.cls("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        return this.ctx.onMain(() -> {
            Object manager = Ctx.call(this.ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");
            Object origin = this.game.blockPos((this.playerChunkX - 1) * 16, this.nearY, (this.playerChunkZ + 1) * 16);
            Object placement = Ctx.call(placementClass, "createFor", schematic, origin, "turbo-lone-" + name, true, true);
            Ctx.call(manager, "addSchematicPlacement", placement, false);
            return placement;
        });
    }

    /** The block chunks (keys) a lone placement touches when its origin is at the given block position. */
    Set<Long> chunksAt(int originX, int originZ)
    {
        Set<Long> out = new LinkedHashSet<>();

        for (int x = Math.floorDiv(originX, 16); x <= Math.floorDiv(originX + 31, 16); x++)
        {
            for (int z = Math.floorDiv(originZ, 16); z <= Math.floorDiv(originZ + 31, 16); z++)
            {
                out.add(key(x, z));
            }
        }

        return out;
    }

    /** Chunk columns that currently have a renderer (in view). */
    Set<Long> renderedKeys() throws Exception
    {
        return this.ctx.onMain(() -> new LinkedHashSet<>(this.renderersOnMain().keySet()));
    }

    /** True when every chunk renderer in view has a finished build (nothing waits to be built). */
    boolean everythingBuilt() throws Exception
    {
        return this.ctx.onMain(() -> {
            for (List<Object> column : this.renderersOnMain().values())
            {
                for (Object r : column)
                {
                    if (built(r) == false)
                    {
                        return false;
                    }
                }
            }

            return true;
        });
    }

    /** True when a chunk column has several renderers (older versions: 16-high sections in view) instead of one per column. */
    boolean sectionRenderers() throws Exception
    {
        return this.ctx.onMain(() -> {
            for (List<Object> column : this.renderersOnMain().values())
            {
                if (column.size() > 1)
                {
                    return true;
                }
            }

            return false;
        });
    }

    /** Waits until nothing is queued, nothing is being built and every chunk in view is built, for about a second. */
    void awaitQuiet(String what) throws Exception
    {
        long deadline = System.currentTimeMillis() + 100_000L;
        int stable = 0;

        while (System.currentTimeMillis() < deadline)
        {
            Thread.sleep(80);
            boolean quiet = this.ctx.onMain(() -> this.placementsIdle() && (Boolean) Ctx.call(this.worldRenderer, "turbo$isIdle")) && this.everythingBuilt();
            stable = quiet ? stable + 1 : 0;

            if (stable >= 14)
            {
                return;
            }
        }

        throw new SelfTest.Fail(what + ": not quiet in 100 seconds");
    }

    /** The origins {x, y, z} of every chunk renderer now in view, as they are drawn. */
    List<int[]> renderOrigins() throws Exception
    {
        return this.ctx.onMain(() -> {
            List<int[]> out = new ArrayList<>();

            for (List<Object> column : this.renderersOnMain().values())
            {
                for (Object r : column)
                {
                    Object o = Ctx.call(r, "getOrigin");
                    out.add(new int[] {(Integer) Ctx.call(o, "getX"), (Integer) Ctx.call(o, "getY"), (Integer) Ctx.call(o, "getZ")});
                }
            }

            return out;
        });
    }

    /** One chunk renderer now in view. */
    Object anyRenderer() throws Exception
    {
        return this.ctx.onMain(() -> {
            for (List<Object> column : this.renderersOnMain().values())
            {
                if (column.isEmpty() == false)
                {
                    return column.get(0);
                }
            }

            throw new SelfTest.Fail("no schematic chunk renderer is in view");
        });
    }

    // ---- configurations, captures ----

    /**
     * Litematica 0.24 (Minecraft 1.21.9 / 1.21.10) ships with its build worker threads commented out: plain, it never
     * builds the queued chunks while the test waits (Turbo starts the workers). The reference for "plain Litematica"
     * there is Turbo with a single build thread and every other Turbo feature off: the same build code, the same
     * result, only the scheduling differs.
     */
    private boolean stoodInForGhost;
    private boolean ghostBefore = true;

    boolean plainHasNoWorkers()
    {
        return this.ctx.pkg.endsWith("mc12110");
    }

    void configure(boolean enabled, int threads, boolean skipEmpty, boolean asyncNear) throws Exception
    {
        if (this.plainHasNoWorkers())
        {
            if (enabled == false)
            {
                enabled = true;
                threads = 1;
                skipEmpty = false;
                asyncNear = false;

                if (this.stoodInForGhost == false)
                {
                    this.stoodInForGhost = true;
                    this.ghostBefore = (Boolean) Ctx.getField(this.config, "ghostMove");
                }

                Ctx.setField(this.config, "ghostMove", false); // plain Litematica shows no shifted view
            }
            else if (this.stoodInForGhost)
            {
                this.stoodInForGhost = false;
                Ctx.setField(this.config, "ghostMove", this.ghostBefore);
            }
        }

        Ctx.setField(this.config, "enabled", enabled);
        Ctx.setField(this.config, "buildThreads", threads);
        Ctx.setField(this.config, "skipEmptySections", skipEmpty);
        Ctx.setField(this.config, "asyncNearRebuilds", asyncNear);
    }

    private long stat(String name) throws Exception
    {
        return (Long) Ctx.call(this.statsClass, name);
    }

    /** Reloads the schematic renderers exactly the way Turbo's own tab does (and nothing more). */
    void reloadLikeTheTab() throws Exception
    {
        this.ctx.onMain(() -> {
            Ctx.call(this.worldRenderer, "setWorldAndLoadRenderers", (Object) null);
            Ctx.call(this.worldRenderer, "setWorldAndLoadRenderers", this.schematicWorld);
            Ctx.call(Ctx.getStatic(this.ctx.cls("fi.dy.masa.litematica.util.SchematicWorldRefresher"), "INSTANCE"), "updateAll");
            return null;
        });
    }

    /** Reloads the schematic renderers the way Turbo's own tab does, so every chunk is built again from scratch. */
    void reload() throws Exception
    {
        this.reloadLikeTheTab();

        // Litematica 0.24 (1.21.9 / 1.21.10) forgets what updateAll() scheduled when its first frame after the reload sets
        // up the new renderers. So there the chunks are asked for again, by name, once the renderers stand (on the other
        // versions that would build every chunk twice and give renderers to chunks that have none).
        if (this.plainHasNoWorkers())
        {
            this.ctx.ticks(3);
            this.rebuildAll();
        }
    }

    /** Marks every test chunk for a new build the way an edit in the schematic world does, keeping the renderers. */
    void touchAll() throws Exception
    {
        this.ctx.onMain(() -> {
            Object refresher = Ctx.getStatic(this.ctx.cls("fi.dy.masa.litematica.util.SchematicWorldRefresher"), "INSTANCE");

            for (Long k : this.expected)
            {
                try
                {
                    Ctx.call(refresher, "markSchematicChunksForRenderUpdate", keyX(k), keyZ(k));
                }
                catch (NoSuchMethodException e)
                {
                    Ctx.call(refresher, "markSchematicChunkForRenderUpdate", this.game.blockPos(keyX(k) * 16 + 8, this.nearY, keyZ(k) * 16 + 8));
                }
            }

            return null;
        });
    }

    /** Asks the renderer to build every test chunk again (the request an edit in the schematic world makes). */
    void rebuildAll() throws Exception
    {
        this.ctx.onMain(() -> {
            for (Long k : this.expected)
            {
                try
                {
                    Ctx.call(this.worldRenderer, "scheduleChunkRenders", keyX(k), keyZ(k));
                }
                catch (NoSuchMethodException e)
                {
                    break;
                }
            }

            return null;
        });
    }

    /** Reloads under a configuration and describes every chunk. Made once per label. */
    Capture capture(String label, boolean enabled, int threads, boolean skipEmpty, boolean asyncNear) throws Exception
    {
        Capture existing = this.captures.get(label);

        if (existing != null)
        {
            return existing;
        }

        String earlier = this.captureFailures.get(label);

        if (earlier != null)
        {
            throw new SelfTest.Fail(earlier);
        }

        try
        {
            Capture c = this.captureNew(label, enabled, threads, skipEmpty, asyncNear);
            this.captures.put(label, c);
            return c;
        }
        catch (SelfTest.Fail e)
        {
            this.captureFailures.put(label, e.getMessage());
            throw e;
        }
    }

    /** Like {@link #capture} but always reloads (the live renderers then show this configuration's result). */
    Capture captureNew(String label, boolean enabled, int threads, boolean skipEmpty, boolean asyncNear) throws Exception
    {
        this.configure(enabled, threads, skipEmpty, asyncNear);
        return this.captureAsIs(label);
    }

    /** Like {@link #captureNew} with the configuration exactly as it is now. */
    Capture captureAsIs(String label) throws Exception
    {
        long builds = this.stat("totalBuilds");
        long main = this.stat("totalMainThreadNanos");
        long scanned = ((AtomicLong) Ctx.getStatic(this.statsClass, "sectionsScanned")).get();
        long skipped = ((AtomicLong) Ctx.getStatic(this.statsClass, "sectionsSkipped")).get();
        long start = System.currentTimeMillis();
        this.reload();
        this.awaitIdle("capture " + label, this.expected, null);

        Capture c = new Capture(label);
        c.millis = System.currentTimeMillis() - start;
        c.builds = this.stat("totalBuilds") - builds;
        c.mainThreadNanos = this.stat("totalMainThreadNanos") - main;
        c.scannedSections = ((AtomicLong) Ctx.getStatic(this.statsClass, "sectionsScanned")).get() - scanned;
        c.skippedSections = ((AtomicLong) Ctx.getStatic(this.statsClass, "sectionsSkipped")).get() - skipped;
        c.threads = this.dispatcherThreads();
        c.sigs.putAll(this.describeAll(this.expected));
        return c;
    }

    int dispatcherThreads() throws Exception
    {
        return this.ctx.onMain(() -> {
            Object dispatcher = Ctx.getField(this.worldRenderer, "renderDispatcher");
            return dispatcher == null ? -1 : (Integer) Ctx.call(dispatcher, "turbo$threadCount");
        });
    }

    /** Why nothing is in view, for a failure message: the screen, Litematica's switches, where the player is. */
    /** A layer view can leave a chunk with nothing to show, and some versions then keep no renderer for it. */
    boolean renderersMayBeMissing;

    private String diagnosis()
    {
        StringBuilder out = new StringBuilder();

        try
        {
            Object screen = this.ctx.screen();
            out.append("screen ").append(screen == null ? "none" : screen.getClass().getName());
        }
        catch (Exception e)
        {
            out.append("screen ?");
        }

        try
        {
            Class<?> visuals = this.ctx.cls("fi.dy.masa.litematica.config.Configs$Visuals");

            for (String option : new String[] {"ENABLE_RENDERING", "ENABLE_SCHEMATIC_RENDERING"})
            {
                try
                {
                    out.append(", ").append(option).append('=').append(Ctx.call(Ctx.getStatic(visuals, option), "getBooleanValue"));
                }
                catch (ReflectiveOperationException e)
                {
                    // not on this version
                }
            }

            Object player = this.game.player();
            out.append(", player ").append(player == null ? "none" : String.valueOf(Game.callAny(player, new String[] {"getBlockPos", "getPos", "blockPosition"})));
            for (String yawName : new String[] {"yaw", "yRot"})
            {
                try
                {
                    out.append(", yaw ").append(Ctx.field(player.getClass(), yawName).getFloat(player));
                    break;
                }
                catch (ReflectiveOperationException | RuntimeException e)
                {
                    // other name
                }
            }

            out.append(", renderInfos ").append(((List<?>) Ctx.getField(this.worldRenderer, "renderInfos")).size());
            out.append(", world ").append(this.worldRenderer == null ? "none" : "renderer set");
        }
        catch (Exception e)
        {
            out.append(", ").append(e);
        }

        return out.toString();
    }

    /** Current chunk renderers by chunk key (only those in view and in render distance). */
    private Map<Long, List<Object>> renderersOnMain() throws Exception
    {
        Map<Long, List<Object>> map = new HashMap<>();
        Map<Object, Integer> heights = new java.util.IdentityHashMap<>();

        for (Object r : new ArrayList<Object>((List<?>) Ctx.getField(this.worldRenderer, "renderInfos")))
        {
            Object origin = Ctx.call(r, "getOrigin");
            int x = (Integer) Ctx.call(origin, "getX");
            int z = (Integer) Ctx.call(origin, "getZ");
            heights.put(r, (Integer) Ctx.call(origin, "getY"));
            map.computeIfAbsent(key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), q -> new ArrayList<>()).add(r);
        }

        // a chunk column has one renderer per 16-high section that Litematica builds: keep them in height order
        for (List<Object> column : map.values())
        {
            column.sort((a, b) -> Integer.compare(heights.get(a), heights.get(b)));
        }

        return map;
    }

    /** True when none of the column's sections has a newer build result than the earlier list of results. */
    private static boolean sameData(List<Object> column, Object earlier) throws ReflectiveOperationException
    {
        List<?> old = (List<?>) earlier;

        if (old.size() != column.size())
        {
            return false;
        }

        for (int i = 0; i < old.size(); i++)
        {
            if (dataOf(column.get(i)) != old.get(i))
            {
                return false;
            }
        }

        return true;
    }

    private static Object dataOf(Object renderer) throws ReflectiveOperationException
    {
        try
        {
            return Ctx.call(renderer, "getChunkRenderData");
        }
        catch (NoSuchMethodException e)
        {
            return Ctx.getField(renderer, "chunkRenderData");
        }
    }

    private static boolean built(Object renderer) throws ReflectiveOperationException
    {
        Object data = dataOf(renderer);

        // a chunk with nothing in it legitimately ends with the shared EMPTY data: finished = no update pending
        if (data == null)
        {
            return false;
        }

        try
        {
            return (Boolean) Ctx.call(renderer, "needsUpdate") == false;
        }
        catch (NoSuchMethodException e)
        {
            return true;
        }
    }

    boolean placementsIdle() throws Exception
    {
        boolean idle = true;

        try
        {
            Object daemon = Ctx.getStatic(this.ctx.cls("fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler"), "INSTANCE");
            idle = (Boolean) Ctx.call(daemon, "hasAnyTasks") == false;
        }
        catch (ClassNotFoundException e)
        {
            // before the daemon: the manager has the queue
        }

        Object manager = Ctx.call(this.ctx.cls("fi.dy.masa.litematica.data.DataManager"), "getSchematicPlacementManager");

        if (Game.has(manager, "hasQueuedChunks", 0))
        {
            idle &= (Boolean) Ctx.call(manager, "hasQueuedChunks") == false;
        }

        return idle;
    }

    /**
     * Waits until every chunk of the set has a finished build, nothing is queued or being built, and that stayed so
     * for about a second. changedFrom: chunks whose build result must be a newer one than these (keys of
     * oldData); empty = no such condition.
     */
    void awaitIdle(String what, Set<Long> chunks, Map<Long, Object> oldData) throws Exception
    {
        long deadline = System.currentTimeMillis() + 100_000L;
        int stable = 0;
        String missing = "";

        while (System.currentTimeMillis() < deadline)
        {
            Thread.sleep(80);
            final String[] why = {""};
            boolean ok = this.ctx.onMain(() -> {
                this.aim();
                if (this.placementsIdle() == false)
                {
                    why[0] = "placements still being copied";
                    return false;
                }

                if ((Boolean) Ctx.call(this.worldRenderer, "turbo$isIdle") == false)
                {
                    why[0] = "builds queued or running";
                    return false;
                }

                Map<Long, List<Object>> map = this.renderersOnMain();

                for (Long k : chunks)
                {
                    List<Object> column = map.get(k);

                    if (column == null && (this.blockless.contains(k) || this.renderersMayBeMissing))
                    {
                        continue;
                    }

                    if (column == null)
                    {
                        StringBuilder none = new StringBuilder();

                        for (Long q : chunks)
                        {
                            none.append(map.containsKey(q) ? "" : " " + keyX(q) + "," + keyZ(q));
                        }

                        why[0] = "chunk " + keyX(k) + "," + keyZ(k) + " has no renderer (without one:" + none + "; not in view?; " + map.size() + " renderers in view, e.g. " + map.keySet().stream().limit(4).map(q -> keyX(q) + "," + keyZ(q)).collect(java.util.stream.Collectors.joining(" ")) + "; player chunk " + this.playerChunkX + "," + this.playerChunkZ + (map.isEmpty() ? "; " + this.diagnosis() : "") + ")";
                        return false;
                    }

                    boolean allBuilt = true;

                    for (Object r : column)
                    {
                        allBuilt &= built(r);
                    }

                    if (allBuilt == false)
                    {
                        why[0] = "chunk " + keyX(k) + "," + keyZ(k) + " not built (" + column.size() + " sections)";
                        return false;
                    }

                    if (oldData != null && oldData.containsKey(k) && sameData(column, oldData.get(k)))
                    {
                        why[0] = "chunk " + keyX(k) + "," + keyZ(k) + " not rebuilt yet";
                        return false;
                    }
                }

                return true;
            });
            missing = why[0];
            stable = ok ? stable + 1 : 0;

            if (stable >= 14)
            {
                return;
            }
        }

        throw new SelfTest.Fail(what + ": not finished in 100 seconds (" + missing + ")");
    }

    /** The build data object of each chunk now (identity, to see later whether it was rebuilt). */
    Map<Long, Object> dataNow(Set<Long> chunks) throws Exception
    {
        return this.ctx.onMain(() -> {
            Map<Long, Object> out = new HashMap<>();
            Map<Long, List<Object>> map = this.renderersOnMain();

            for (Long k : chunks)
            {
                if (map.get(k) != null)
                {
                    List<Object> datas = new ArrayList<>();

                    for (Object r : map.get(k))
                    {
                        datas.add(dataOf(r));
                    }

                    out.put(k, datas);
                }
            }

            return out;
        });
    }

    Map<Long, Sig> describeAll(Set<Long> chunks) throws Exception
    {
        return this.ctx.onMain(() -> {
            Map<Long, Sig> out = new LinkedHashMap<>();
            Map<Long, List<Object>> map = this.renderersOnMain();

            for (Long k : chunks)
            {
                List<Object> column = map.get(k);

                if (column == null)
                {
                    if (this.blockless.contains(k) || this.renderersMayBeMissing)
                    {
                        out.put(k, new Sig(new ArrayList<>(java.util.Collections.singletonList("no renderer: the chunk has nothing to show"))));
                    }

                    continue;
                }

                List<String> lines = new ArrayList<>();
                int section = 0;

                for (Object r : column)
                {
                    String at = "section" + section++;
                    Digest.lines(at + ".data", dataOf(r), lines);

                    for (String field : new String[] {"vertexBufferBlocks", "vertexBufferOverlay"})
                    {
                        try
                        {
                            Digest.lines(at + "." + field, Ctx.getField(r, field), lines);
                        }
                        catch (ReflectiveOperationException e)
                        {
                            // this version keeps its buffers elsewhere
                        }
                    }
                }

                out.put(k, new Sig(lines));
            }

            return out;
        });
    }

    // ---- reading the world ----

    Set<Long> chunksOf(String... zoneNames)
    {
        Set<Long> out = new LinkedHashSet<>();

        for (String n : zoneNames)
        {
            out.addAll(this.zones.get(n).chunks);
        }

        return out;
    }

    Set<Long> allChunks()
    {
        return this.expected;
    }

    Object schematicWorld()
    {
        return this.schematicWorld;
    }

    Object worldRenderer()
    {
        return this.worldRenderer;
    }

    long stats(String name) throws Exception
    {
        return this.stat(name);
    }

    /** The states whose text form is exactly this (one, unless the registry holds duplicates). */
    List<Object> statesForText(String text)
    {
        List<Object> out = new ArrayList<>();

        for (Object s : this.states)
        {
            if (String.valueOf(s).equals(text))
            {
                out.add(s);
            }
        }

        return out;
    }

    /** The state of a block name like "minecraft:stone" (its first state). */
    Object stateNamed(String name)
    {
        List<Object> found = this.statesNamed(name);
        return found.isEmpty() ? null : found.get(0);
    }

    /** The distance from the camera to the chunk, as Litematica's own "near" rule measures it (squared). */
    double nearDistanceSq(long chunk) throws Exception
    {
        return this.ctx.onMain(() -> {
            List<Object> column = this.renderersOnMain().get(chunk);

            if (column == null)
            {
                return Double.NaN;
            }

            double nearest = Double.MAX_VALUE;

            for (Object r : column)
            {
                Object origin = Ctx.call(r, "getOrigin");
                double dx = (Integer) Ctx.call(origin, "getX") + 8 - this.playerX;
                double dy = (Integer) Ctx.call(origin, "getY") + 8 - (this.playerY + 1);
                double dz = (Integer) Ctx.call(origin, "getZ") + 8 - this.playerZ;
                nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
            }

            Object any = column.get(0);
            Object o = Ctx.call(any, "getOrigin");
            this.lastOriginY = (Integer) Ctx.call(o, "getY");
            return nearest;
        });
    }
}
