package com.litematicaturbo.selftest;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * memory/: a huge, mostly empty schematic region is kept in sparse sections instead of one packed array for the whole
 * box. Counted in bytes (never timed): far fewer bytes held than the dense array, the same blocks when read, edited,
 * saved and placed, a red translated message instead of loading a file that cannot fit, and a failing sparse storage
 * that switches itself off with a chat line while the schematic is still stored the plain way.
 */
final class MemoryTests
{
    private static final int SX = 512;
    private static final int SY = 128;
    private static final int SZ = 512;
    private static final int BITS = 2;
    private static final String CONTAINER = "fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer";
    private static final String BIT_ARRAY = "fi.dy.masa.litematica.schematic.container.LitematicaBitArray";
    private static final String STORAGE = "com.litematicaturbo.core.CompactStorage";
    private static final String MEMORY_KEY = "litematica_turbo.message.out_of_memory";
    private static final String OFF_KEY = "litematica_turbo.message.storage_off";

    private MemoryTests()
    {
    }

    /** A test-made huge, mostly empty region: twenty small clusters of blocks, each inside one 16-block section. */
    private static final class Region
    {
        final long[] words = new long[(int) ((long) SX * SY * SZ * BITS / 64)];
        final Map<Long, Integer> blocks = new LinkedHashMap<>();

        Region()
        {
            int seed = 12345;

            for (int c = 0; c < 20; c++)
            {
                seed = seed * 1103515245 + 12345;
                int x0 = ((seed >>> 8) % (SX / 16)) * 16 + 3;
                seed = seed * 1103515245 + 12345;
                int y0 = ((seed >>> 8) % (SY / 16)) * 16 + 3;
                seed = seed * 1103515245 + 12345;
                int z0 = ((seed >>> 8) % (SZ / 16)) * 16 + 3;

                for (int i = 0; i < 64; i++)
                {
                    int id = 1 + (i + c) % 3;
                    long index = ((long) (y0 + i / 16) * SZ + (z0 + (i / 4) % 4)) * SX + x0 + i % 4;
                    this.blocks.put(index, id);
                    this.put(index, id);
                }
            }
        }

        private void put(long index, int id)
        {
            long start = index * BITS;
            this.words[(int) (start >> 6)] |= (long) id << (start & 63);
        }
    }

    private static Object config(Ctx ctx) throws ReflectiveOperationException
    {
        return Ctx.call(ctx.cls("com.litematicaturbo.core.TurboConfig"), "get");
    }

    private static void compact(Ctx ctx, boolean on) throws ReflectiveOperationException
    {
        Ctx.setField(config(ctx), "compactStorage", on);
    }

    private static Object container(Ctx ctx, long[] words) throws ReflectiveOperationException
    {
        Constructor<?> c = ctx.cls(CONTAINER).getConstructor(int.class, int.class, int.class, int.class, long[].class);
        return c.newInstance(SX, SY, SZ, BITS, words.clone());
    }

    private static boolean isSparse(Object container) throws ReflectiveOperationException
    {
        return Ctx.call(container, "getArray").getClass().getSimpleName().equals("SparseBitArray");
    }

    private static int idAt(Method getAt, Object array, long index) throws ReflectiveOperationException
    {
        return (Integer) getAt.invoke(array, index);
    }

    /** Back to plain defaults, whatever a test did. */
    private static void reset(Ctx ctx) throws Exception
    {
        Ctx.call(ctx.cls(STORAGE), "resetForTest");
        compact(ctx, true);
    }

    /** Litematica's own loading of a file through its schematic holder (render thread; a file is only read the first time it is asked for). */
    private static Object load(Ctx ctx, File file) throws Exception
    {
        Object holder = Ctx.call(ctx.cls("fi.dy.masa.litematica.data.SchematicHolder"), "getInstance");

        try
        {
            return ctx.onMain(() -> Ctx.call(holder, "getOrLoad", file));
        }
        catch (NoSuchMethodException e)
        {
            return ctx.onMain(() -> Ctx.call(holder, "getOrLoad", file.toPath()));
        }
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("memory/sparse/far-fewer-bytes-than-dense", () -> {
            try
            {
                Region region = new Region();
                Object sparse = container(ctx, region.words);
                Object array = Ctx.call(sparse, "getArray");
                SelfTest.check(isSparse(sparse), "the huge, mostly empty region is stored as " + array.getClass().getName());
                long held = (Long) Ctx.call(array, "allocatedBytes");
                long dense = (Long) Ctx.call(array, "denseBytes");
                SelfTest.equal((long) region.words.length * 8, dense, "bytes of the dense array");
                SelfTest.check(held > 0 && held * 20 <= dense, "the sparse sections hold " + held + " bytes, the dense array " + dense);

                compact(ctx, false);
                Object plain = container(ctx, region.words);
                SelfTest.check(isSparse(plain) == false, "the setting off still stored the region sparse");
                long plainHeld = ((long[]) Ctx.call(plain, "getBackingLongArray")).length * 8L;
                SelfTest.equal(dense, plainHeld, "bytes held with the setting off");
            }
            finally
            {
                reset(ctx);
            }
        });

        tests.add("memory/sparse/same-blocks-as-dense", () -> {
            try
            {
                Region region = new Region();
                Object sparse = container(ctx, region.words);
                compact(ctx, false);
                Object plain = container(ctx, region.words);
                SelfTest.check(isSparse(sparse) && isSparse(plain) == false, "the two containers do not differ in how they store");
                Object a = Ctx.call(sparse, "getArray");
                Object b = Ctx.call(plain, "getArray");
                Method getAt = ctx.cls(BIT_ARRAY).getMethod("getAt", long.class);
                SelfTest.equal(Ctx.call(b, "size"), Ctx.call(a, "size"), "size of the array");

                for (Map.Entry<Long, Integer> e : region.blocks.entrySet())
                {
                    SelfTest.equal(e.getValue(), idAt(getAt, a, e.getKey()), "sparse block id at cell " + e.getKey());
                    SelfTest.equal(e.getValue(), idAt(getAt, b, e.getKey()), "dense block id at cell " + e.getKey());
                }

                long cells = (long) SX * SY * SZ;

                for (long i = 0; i < cells; i += 7919)
                {
                    SelfTest.equal(idAt(getAt, b, i), idAt(getAt, a, i), "block id at sampled cell " + i);
                }

                SelfTest.check(Arrays.equals(region.words, (long[]) Ctx.call(sparse, "getBackingLongArray")), "saving the sparse region does not give the packed array it was read from");
            }
            finally
            {
                reset(ctx);
            }
        });

        tests.add("memory/sparse/edits-and-palette-growth-like-dense", () -> {
            Fixture f = WorldTests.fixture(ctx);

            try
            {
                Region region = new Region();
                Object sparse = container(ctx, region.words);
                compact(ctx, false);
                Object plain = container(ctx, region.words);
                String[] names = {"minecraft:stone", "minecraft:glass", "minecraft:oak_planks", "minecraft:gold_block", "minecraft:dirt"};

                // the fifth new block does not fit the 2-bit palette: both containers widen it and copy the whole box
                for (int i = 0; i < names.length; i++)
                {
                    Object state = f.stateNamed(names[i]);
                    SelfTest.check(state != null, "the block registry has no " + names[i]);

                    for (Object container : new Object[] {sparse, plain})
                    {
                        Ctx.call(container, "set", 100 + i, 5, 200 + i, state);
                    }
                }

                SelfTest.check(isSparse(sparse), "the region is not sparse any more after its palette grew");
                SelfTest.check((Long) Ctx.call(Ctx.call(sparse, "getArray"), "allocatedBytes") * 20 <= (Long) Ctx.call(Ctx.call(sparse, "getArray"), "denseBytes"),
                                "the grown region holds almost as much as a dense array");
                SelfTest.check(Arrays.equals((long[]) Ctx.call(plain, "getBackingLongArray"), (long[]) Ctx.call(sparse, "getBackingLongArray")),
                                "the edited sparse region saves differently from the edited dense one");

                for (int i = 0; i < names.length; i++)
                {
                    SelfTest.equal(String.valueOf(Ctx.call(plain, "get", 100 + i, 5, 200 + i)), String.valueOf(Ctx.call(sparse, "get", 100 + i, 5, 200 + i)), "block at the edit " + i);
                }
            }
            finally
            {
                reset(ctx);
            }
        });

        tests.add("memory/place/same-blocks-as-plain-litematica", () -> PlaceTests.guarded(ctx, () -> {
            try
            {
                compact(ctx, false);
                List<String> plain = PlaceTests.placedFile(ctx, "memory-plain", "Mplain", "M1");
                int before = (Integer) Ctx.call(ctx.cls(STORAGE), "sparseRegions");
                compact(ctx, true);
                Ctx.call(ctx.cls(STORAGE), "setForceForTest", true);
                List<String> sparse = PlaceTests.placedFile(ctx, "memory-sparse", "Msparse", "M1");
                SelfTest.check((Integer) Ctx.call(ctx.cls(STORAGE), "sparseRegions") > before, "the placed schematic was not stored in sparse sections");
                SelfTest.check(plain.size() - PlaceTests.air(plain) > 100, "the placement put almost nothing into the world");
                SelfTest.check(plain.equals(sparse), "the placed blocks differ from plain Litematica: " + PlaceTests.firstDifference(plain, sparse));
            }
            finally
            {
                reset(ctx);
            }
        }));

        tests.add("memory/budget/too-big-file-gets-a-translated-message", () -> {
            try
            {
                Fixture f = WorldTests.fixture(ctx);
                File normal = f.writeLone("memory-normal", "M1");
                File tooBig = f.writeLone("memory-too-big", "M1");
                File settingOff = f.writeLone("memory-setting-off", "M1");
                Class<?> storage = ctx.cls(STORAGE);
                SelfTest.check(load(ctx, normal) != null, "the small test schematic does not load with the normal memory");
                int refusals = (Integer) Ctx.call(storage, "refusals");

                Ctx.call(ctx.cls("com.litematicaturbo.core.MemoryBudget"), "setBudgetForTest", 1L);
                SelfTest.check(load(ctx, tooBig) == null, "a file that does not fit the pretended memory was loaded");
                SelfTest.equal(refusals + 1, Ctx.call(storage, "refusals"), "files refused for memory");
                SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");

                compact(ctx, false);
                SelfTest.check(load(ctx, settingOff) != null, "with the setting off the file is not loaded like plain Litematica does");
                SelfTest.equal(refusals + 1, Ctx.call(storage, "refusals"), "files refused with the setting off");
            }
            finally
            {
                reset(ctx);
            }
        });

        tests.add("memory/safety/error-switches-off-and-loads-dense", () -> {
            WorldTests.fixture(ctx);

            try
            {
                Region region = new Region();
                Class<?> storage = ctx.cls(STORAGE);
                Ctx.call(storage, "setFaultForTest", true);
                Object container = container(ctx, region.words);
                SelfTest.check(isSparse(container) == false, "a failing sparse storage still gave a sparse region");
                SelfTest.check(Ctx.call(storage, "failure") != null, "the error did not switch the sparse storage off");
                SelfTest.check(Arrays.equals(region.words, (long[]) Ctx.call(container, "getBackingLongArray")), "the schematic is not stored densely and unchanged after the error");

                Ctx.call(storage, "setFaultForTest", false);
                SelfTest.check(isSparse(container(ctx, region.words)) == false, "the sparse storage came back after it switched itself off");
                ctx.ticks(4);
                String chat = SafetyTests.chatText(ctx);
                SelfTest.check(chat.contains(OFF_KEY), "no chat line says the compact storage is off:\n" + chat);
                SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");
            }
            finally
            {
                reset(ctx);
            }
        });

        tests.add("memory/messages/translated", () -> {
            String lang = AuditTests.resource(ctx.cls(STORAGE).getClassLoader(), "assets/litematica_turbo/lang/en_us.json");
            SelfTest.check(lang != null, "no en_us.json");

            for (String key : new String[] {MEMORY_KEY, OFF_KEY, "config.name.turbocompactstorage"})
            {
                SelfTest.check(lang.contains("\"" + key + "\": \""), "en_us.json has no English text for " + key);
            }

            Method translate = ctx.cls("fi.dy.masa.malilib.util.StringUtils").getMethod("translate", String.class, Object[].class);
            String text = (String) translate.invoke(null, MEMORY_KEY, new Object[] {4242L});
            SelfTest.check(text.equals(MEMORY_KEY) == false && text.contains("4242"), "the memory message is not translated by the game: " + text);
        });
    }
}
