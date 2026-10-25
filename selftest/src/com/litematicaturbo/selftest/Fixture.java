package com.litematicaturbo.selftest;

import java.io.File;
import java.util.ArrayList;
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

        Sig(List<String> lines)
        {
            this.lines = lines;
            this.hash = Integer.toHexString(lines.hashCode());
        }

        /** Why two descriptions differ: the first line that is not the same. */
        String diff(Sig other)
        {
            int n = Math.min(this.lines.size(), other.lines.size());

            for (int i = 0; i < n; i++)
            {
                if (this.lines.get(i).equals(other.lines.get(i)) == false)
                {
                    return "'" + this.lines.get(i) + "' vs '" + other.lines.get(i) + "'";
                }
            }

            return this.lines.size() + " lines vs " + other.lines.size() + " lines";
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
    int playerX, playerY, playerZ, playerChunkX, playerChunkZ, bottom, height;
    final List<Object> states = new ArrayList<>();
    final Class<?> configClass;
    final Class<?> statsClass;
    private final Object config;
    private final Object schematicWorld;
    private final Object worldRenderer;
    private final Set<Long> expected = new LinkedHashSet<>();

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
        int[] p = ctx.onMain(this.game::playerPos);
        this.playerX = p[0];
        this.playerY = p[1];
        this.playerZ = p[2];
        this.playerChunkX = Math.floorDiv(p[0], 16);
        this.playerChunkZ = Math.floorDiv(p[2], 16);
        this.bottom = ctx.onMain(this.game::bottomY);
        this.height = ctx.onMain(this.game::worldHeight);
        this.states.addAll(ctx.onMain(this.game::allBlockStates));

        this.enableRendering();
        this.zone("N", -1, 0, 3); // in front of the player: Litematica builds only chunks in the view
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

            // the player looks along +Z at the far zones
            try
            {
                Object player = this.game.player();

                for (String f : new String[] {"yaw", "prevYaw", "yRot", "yRotO", "pitch", "prevPitch", "xRot", "xRotO"})
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
                        {1, 0, 1}, {17, 40, 33}, {30, sy - 1, 30}, {47, sy - 1, 0}, {0, 15, 40}, {1, 16, 40}, {47, 100, 47}, {20, 63, 20}, {33, 64, 20}};

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
                    originY = this.bottom + 10;
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

    private Object load(Object holder, File file) throws Exception
    {
        Object schematic;

        try
        {
            schematic = this.ctx.onMain(() -> Ctx.call(holder, "getOrLoad", file));
        }
        catch (NoSuchMethodException e)
        {
            schematic = this.ctx.onMain(() -> Ctx.call(holder, "getOrLoad", file.toPath()));
        }

        if (schematic == null)
        {
            throw new SelfTest.Fail("Litematica could not load the test schematic " + file);
        }

        return schematic;
    }

    // ---- configurations, captures ----

    void configure(boolean enabled, int threads, boolean skipEmpty, boolean asyncNear) throws Exception
    {
        Ctx.setField(this.config, "enabled", enabled);
        Ctx.setField(this.config, "buildThreads", threads);
        Ctx.setField(this.config, "skipEmptySections", skipEmpty);
        Ctx.setField(this.config, "asyncNearRebuilds", asyncNear);
    }

    private long stat(String name) throws Exception
    {
        return (Long) Ctx.call(this.statsClass, name);
    }

    /** Reloads the schematic renderers the way Turbo's own tab does, so every chunk is built again from scratch. */
    private void reload() throws Exception
    {
        this.ctx.onMain(() -> {
            Ctx.call(this.worldRenderer, "setWorldAndLoadRenderers", (Object) null);
            Ctx.call(this.worldRenderer, "setWorldAndLoadRenderers", this.schematicWorld);
            Ctx.call(Ctx.getStatic(this.ctx.cls("fi.dy.masa.litematica.util.SchematicWorldRefresher"), "INSTANCE"), "updateAll");
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

        Capture c = this.captureNew(label, enabled, threads, skipEmpty, asyncNear);
        this.captures.put(label, c);
        return c;
    }

    /** Like {@link #capture} but always reloads (the live renderers then show this configuration's result). */
    Capture captureNew(String label, boolean enabled, int threads, boolean skipEmpty, boolean asyncNear) throws Exception
    {
        this.configure(enabled, threads, skipEmpty, asyncNear);
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

    /** Current chunk renderers by chunk key (only those in view and in render distance). */
    private Map<Long, Object> renderersOnMain() throws Exception
    {
        Map<Long, Object> map = new HashMap<>();

        for (Object r : new ArrayList<Object>((List<?>) Ctx.getField(this.worldRenderer, "renderInfos")))
        {
            Object origin = Ctx.call(r, "getOrigin");
            int x = (Integer) Ctx.call(origin, "getX");
            int z = (Integer) Ctx.call(origin, "getZ");
            map.put(key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), r);
        }

        return map;
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

        if (data == null || data == Ctx.getStatic(data.getClass(), "EMPTY"))
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
        long deadline = System.currentTimeMillis() + 4 * 60_000L;
        int stable = 0;
        String missing = "";

        while (System.currentTimeMillis() < deadline)
        {
            Thread.sleep(80);
            final String[] why = {""};
            boolean ok = this.ctx.onMain(() -> {
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

                Map<Long, Object> map = this.renderersOnMain();

                for (Long k : chunks)
                {
                    Object r = map.get(k);

                    if (r == null)
                    {
                        why[0] = "chunk " + keyX(k) + "," + keyZ(k) + " has no renderer (not in view?; " + map.size() + " renderers in view, e.g. " + map.keySet().stream().limit(4).map(q -> keyX(q) + "," + keyZ(q)).collect(java.util.stream.Collectors.joining(" ")) + "; player chunk " + this.playerChunkX + "," + this.playerChunkZ + ")";
                        return false;
                    }

                    if (built(r) == false)
                    {
                        why[0] = "chunk " + keyX(k) + "," + keyZ(k) + " not built";
                        return false;
                    }

                    if (oldData != null && oldData.containsKey(k) && dataOf(r) == oldData.get(k))
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

        throw new SelfTest.Fail(what + ": not finished in 4 minutes (" + missing + ")");
    }

    /** The build data object of each chunk now (identity, to see later whether it was rebuilt). */
    Map<Long, Object> dataNow(Set<Long> chunks) throws Exception
    {
        return this.ctx.onMain(() -> {
            Map<Long, Object> out = new HashMap<>();
            Map<Long, Object> map = this.renderersOnMain();

            for (Long k : chunks)
            {
                if (map.get(k) != null)
                {
                    out.put(k, dataOf(map.get(k)));
                }
            }

            return out;
        });
    }

    Map<Long, Sig> describeAll(Set<Long> chunks) throws Exception
    {
        return this.ctx.onMain(() -> {
            Map<Long, Sig> out = new LinkedHashMap<>();
            Map<Long, Object> map = this.renderersOnMain();

            for (Long k : chunks)
            {
                Object r = map.get(k);

                if (r == null)
                {
                    continue;
                }

                List<String> lines = new ArrayList<>();
                Digest.lines("data", dataOf(r), lines);

                for (String field : new String[] {"vertexBufferBlocks", "vertexBufferOverlay"})
                {
                    try
                    {
                        Digest.lines(field, Ctx.getField(r, field), lines);
                    }
                    catch (ReflectiveOperationException e)
                    {
                        // this version keeps its buffers elsewhere
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
            Object r = this.renderersOnMain().get(chunk);

            if (r == null)
            {
                return Double.NaN;
            }

            Object origin = Ctx.call(r, "getOrigin");
            double dx = (Integer) Ctx.call(origin, "getX") + 8 - this.playerX;
            double dy = (Integer) Ctx.call(origin, "getY") + 8 - (this.playerY + 1);
            double dz = (Integer) Ctx.call(origin, "getZ") + 8 - this.playerZ;
            return dx * dx + dy * dy + dz * dz;
        });
    }
}
