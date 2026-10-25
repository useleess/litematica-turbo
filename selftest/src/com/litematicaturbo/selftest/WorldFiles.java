package com.litematicaturbo.selftest;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Files the tests write themselves, so no world or schematic has to ship with the mod: a minimal flat-world
 * level.dat (in the 1.16.5 format; every newer game upgrades it on load) and .litematic schematics (every
 * Litematica version reads the same region layout; the data version is the running Litematica's own).
 * A tiny NBT writer, plain Java 8.
 */
final class WorldFiles
{
    private static final int DATA_VERSION_1_16_5 = 2586;

    private WorldFiles()
    {
    }

    // ---- NBT: Byte, Short, Integer, Long, Float, Double, String, long[], int[], List (of one kind), Map (compound) ----

    static Map<String, Object> compound(Object... keyValues)
    {
        Map<String, Object> map = new LinkedHashMap<>();

        for (int i = 0; i < keyValues.length; i += 2)
        {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }

        return map;
    }

    static List<Object> list(Object... values)
    {
        List<Object> list = new ArrayList<>();

        for (Object value : values)
        {
            list.add(value);
        }

        return list;
    }

    private static int typeOf(Object v)
    {
        if (v instanceof Byte) return 1;
        if (v instanceof Short) return 2;
        if (v instanceof Integer) return 3;
        if (v instanceof Long) return 4;
        if (v instanceof Float) return 5;
        if (v instanceof Double) return 6;
        if (v instanceof String) return 8;
        if (v instanceof List) return 9;
        if (v instanceof Map) return 10;
        if (v instanceof int[]) return 11;
        if (v instanceof long[]) return 12;
        throw new IllegalArgumentException("not an NBT value: " + v);
    }

    private static void payload(DataOutputStream out, Object v) throws IOException
    {
        switch (typeOf(v))
        {
            case 1: out.writeByte((Byte) v); break;
            case 2: out.writeShort((Short) v); break;
            case 3: out.writeInt((Integer) v); break;
            case 4: out.writeLong((Long) v); break;
            case 5: out.writeFloat((Float) v); break;
            case 6: out.writeDouble((Double) v); break;
            case 8: out.writeUTF((String) v); break;
            case 9:
            {
                List<?> list = (List<?>) v;
                out.writeByte(list.isEmpty() ? 10 : typeOf(list.get(0)));
                out.writeInt(list.size());

                for (Object e : list)
                {
                    payload(out, e);
                }

                break;
            }
            case 10:
            {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
                {
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF((String) e.getKey());
                    payload(out, e.getValue());
                }

                out.writeByte(0);
                break;
            }
            case 11:
            {
                int[] a = (int[]) v;
                out.writeInt(a.length);

                for (int x : a)
                {
                    out.writeInt(x);
                }

                break;
            }
            default:
            {
                long[] a = (long[]) v;
                out.writeInt(a.length);

                for (long x : a)
                {
                    out.writeLong(x);
                }
            }
        }
    }

    static void writeGzipNbt(File file, Map<String, Object> root) throws IOException
    {
        file.getParentFile().mkdirs();

        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file))))
        {
            out.writeByte(10);
            out.writeUTF("");
            payload(out, root);
        }
    }

    // ---- the flat test world ----

    /** A creative, peaceful superflat world (bedrock, 2 dirt, grass), no daylight / weather / mob changes. */
    static void writeLevelDat(File worldDir, String name) throws IOException
    {
        List<Object> layers = list(
            compound("block", "minecraft:bedrock", "height", 1),
            compound("block", "minecraft:dirt", "height", 2),
            compound("block", "minecraft:grass_block", "height", 1));
        Map<String, Object> flat = compound("biome", "minecraft:plains", "lakes", (byte) 0, "features", (byte) 0, "layers", layers,
                                            "structures", compound("structures", compound()));
        Map<String, Object> overworld = compound("type", "minecraft:overworld",
                                                 "generator", compound("type", "minecraft:flat", "settings", flat));
        Map<String, Object> settings = compound("seed", 1L, "generate_features", (byte) 0, "bonus_chest", (byte) 0,
                                                "dimensions", compound("minecraft:overworld", overworld));
        Map<String, Object> data = compound(
            "DataVersion", DATA_VERSION_1_16_5,
            "version", 19133,
            "Version", compound("Id", DATA_VERSION_1_16_5, "Name", "1.16.5", "Snapshot", (byte) 0),
            "LevelName", name,
            "GameType", 1,
            "Difficulty", (byte) 0,
            "DifficultyLocked", (byte) 0,
            "hardcore", (byte) 0,
            "allowCommands", (byte) 1,
            "initialized", (byte) 1,
            "SpawnX", 0, "SpawnY", 5, "SpawnZ", 0, "SpawnAngle", 0.0f,
            "Time", 0L, "DayTime", 6000L, "LastPlayed", System.currentTimeMillis(),
            "raining", (byte) 0, "rainTime", 1000000, "thundering", (byte) 0, "thunderTime", 1000000, "clearWeatherTime", 0,
            "WanderingTraderSpawnDelay", 24000, "WanderingTraderSpawnChance", 25,
            "DataPacks", compound("Enabled", list("vanilla"), "Disabled", new ArrayList<Object>()),
            "GameRules", compound("doDaylightCycle", "false", "doWeatherCycle", "false", "doMobSpawning", "false",
                                  "doInsomnia", "false", "doPatrolSpawning", "false", "doTraderSpawning", "false"),
            "WorldGenSettings", settings);
        writeGzipNbt(new File(worldDir, "level.dat"), compound("Data", data));
    }

    // ---- .litematic ----

    /**
     * One region, cells indexed (y * sizeZ + z) * sizeX + x with indices into palette (0 must be air); each palette
     * entry is {blockName, "key=value,key=value" or ""}.
     */
    static void writeSchematic(File file, int dataVersion, int version, String name, int sx, int sy, int sz,
                               List<String[]> palette, int[] cells) throws IOException
    {
        List<Object> pal = new ArrayList<>();
        int total = 0;

        for (String[] entry : palette)
        {
            Map<String, Object> state = compound("Name", entry[0]);

            if (entry[1].isEmpty() == false)
            {
                Map<String, Object> props = new LinkedHashMap<>();

                for (String kv : entry[1].split(","))
                {
                    int eq = kv.indexOf('=');
                    props.put(kv.substring(0, eq), kv.substring(eq + 1));
                }

                state.put("Properties", props);
            }

            pal.add(state);
        }

        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] packed = new long[(int) (((long) cells.length * bits + 63) / 64)];

        for (int i = 0; i < cells.length; i++)
        {
            if (cells[i] != 0)
            {
                ++total;
            }

            long start = (long) i * bits;
            int index = (int) (start >> 6);
            int offset = (int) (start & 63);
            long value = cells[i];
            packed[index] |= value << offset;
            int end = (int) (((start + bits) - 1) >> 6);

            if (end != index)
            {
                packed[end] |= value >>> (64 - offset);
            }
        }

        Map<String, Object> size = compound("x", sx, "y", sy, "z", sz);
        Map<String, Object> region = compound(
            "Position", compound("x", 0, "y", 0, "z", 0),
            "Size", size,
            "BlockStatePalette", pal,
            "BlockStates", packed,
            "TileEntities", new ArrayList<Object>(),
            "Entities", new ArrayList<Object>(),
            "PendingBlockTicks", new ArrayList<Object>(),
            "PendingFluidTicks", new ArrayList<Object>());
        long now = System.currentTimeMillis();
        Map<String, Object> meta = compound("Name", name, "Author", "turbo-test", "Description", "",
                                            "TimeCreated", now, "TimeModified", now, "EnclosingSize", size,
                                            "RegionCount", 1, "TotalVolume", cells.length, "TotalBlocks", total);
        Map<String, Object> root = compound("MinecraftDataVersion", dataVersion, "Version", version);

        if (version >= 6)
        {
            root.put("SubVersion", 1);
        }

        root.put("Metadata", meta);
        root.put("Regions", compound("main", region));
        writeGzipNbt(file, root);
    }
}
