package com.litematicaturbo.shared;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

import com.litematicaturbo.core.TurboLog;

/**
 * Makes the vertex format of a schematic block layer's buffer builder agree with what Iris writes into it.
 * Iris chooses twice, at two moments, from one flag only the render thread sets (ImmediateState.isRenderingLevel, true
 * only while the world is drawn): the block layer's pipeline answers with its extended terrain format, and later the
 * builder's constructor decides whether it fills that format's extra elements. Litematica makes the builder on a build
 * thread, so the flag can change in between: the builder then has the extended format and writes plain vertices (the
 * crash "Missing elements in vertex: mc_midTexCoord, at_tangent"), or a plain builder is made while shaders are on and
 * the layer is drawn by a shader that wants the extended elements. Plain Litematica builds the same way and has the same
 * chance. Here the choice is made once, from what the shader pack asks for, and the builder is told. Asked by name (no
 * compile-time link to Iris); with no Iris, or one that cannot be asked, the stock builder is made and this stays off.
 */
public final class IrisBuilders
{
    private static final String STATE = "net.irisshaders.iris.vertices.ImmediateState";
    private static final String FORMATS = "net.irisshaders.iris.vertices.IrisVertexFormats";
    private static final String IRIS = "net.irisshaders.iris.Iris";

    private static volatile boolean resolved;
    private static volatile boolean off;
    private static Object terrain;
    private static Field renderingLevel;
    private static Field extendedWanted;
    private static Method packInUse;
    private static ThreadLocal<Boolean> skipExtension;
    private static volatile Field extending;
    private static volatile int races;

    private IrisBuilders()
    {
    }

    /**
     * Makes the builder with {@code construct} (given the format to use), or returns null when the stock builder should
     * be made: no Iris, a layer whose format Iris does not change, or Iris could not be asked (logged once).
     *
     * @param base  the format the layer's pipeline gave
     * @param plain the game's plain block format
     */
    public static Object create(Object base, Object plain, Function<Object, Object> construct)
    {
        if (off)
        {
            return null;
        }

        try
        {
            if (resolve() == false || (base != plain && base != terrain))
            {
                return null;
            }

            boolean extended = (Boolean) packInUse.invoke(null) && extendedWanted.getBoolean(null);

            if (base == terrain && renderingLevel.getBoolean(null) == false)
            {
                countRace();
            }

            Boolean before = skipExtension.get();
            Object builder;
            skipExtension.set(Boolean.TRUE);

            try
            {
                builder = construct.apply(extended ? terrain : plain);
            }
            finally
            {
                skipExtension.set(before);
            }

            extendingField(builder).setBoolean(builder, extended);
            return builder;
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            TurboLog.warn("Iris is installed but its buffer builders cannot be set up (" + e + "); schematic buffers are made the stock way");
            off = true;
            return null;
        }
    }

    private static void countRace()
    {
        if (races++ == 0)
        {
            TurboLog.info("Iris changed state while a schematic chunk was being built; the chunk's buffer was built in the format the shader pack asks for");
        }
    }

    private static boolean resolve() throws ReflectiveOperationException
    {
        if (resolved == false)
        {
            synchronized (IrisBuilders.class)
            {
                if (resolved == false)
                {
                    load();
                    resolved = true;
                }
            }
        }

        return terrain != null;
    }

    @SuppressWarnings("unchecked")
    private static void load() throws ReflectiveOperationException
    {
        Class<?> state;

        try
        {
            state = Class.forName(STATE);
        }
        catch (ClassNotFoundException noIris)
        {
            return;
        }

        renderingLevel = state.getField("isRenderingLevel");
        extendedWanted = state.getField("renderWithExtendedVertexFormat");
        skipExtension = (ThreadLocal<Boolean>) state.getField("skipExtension").get(null);
        packInUse = Class.forName(IRIS).getMethod("isPackInUseQuick");
        terrain = Class.forName(FORMATS).getField("TERRAIN").get(null);
    }

    private static Field extendingField(Object builder) throws NoSuchFieldException
    {
        Field field = extending;

        if (field == null)
        {
            field = builder.getClass().getDeclaredField("extending");
            field.setAccessible(true);
            extending = field;
        }

        return field;
    }

    // ---- for the tests ----

    /** Whether the builder fills Iris's extended elements exactly when its format is Iris's terrain format. */
    public static boolean consistent(Object builder, Object format) throws ReflectiveOperationException
    {
        return extendingField(builder).getBoolean(builder) == (format == terrain);
    }

    /** Sets the flag only the render thread sets; returns what it was. Null when there is no Iris. */
    public static Boolean setRenderingLevel(boolean now) throws ReflectiveOperationException
    {
        if (resolve() == false)
        {
            return null;
        }

        boolean was = renderingLevel.getBoolean(null);
        renderingLevel.setBoolean(null, now);
        return was;
    }

    /** Iris's extended terrain format, which a block layer's pipeline gives while the world is drawn with shaders on; null without Iris. */
    public static Object terrainFormat() throws ReflectiveOperationException
    {
        return resolve() ? terrain : null;
    }

    /**
     * Test hook (render thread, shaders on): {@code rounds} times, the flag changes between the layer's pipeline answering
     * with the extended format and the builder being made, as it can on a build thread. Returns what is wrong with the
     * builders Turbo makes then (empty when nothing); the stock builder in the same state is made as well and counted in
     * the log, to show the state really is the broken one. A maker returns {builder, AutoCloseable allocator}.
     */
    public static String selfCheck(int rounds, Function<Object, Object[]> turboMaker, Function<Object, Object[]> stockMaker) throws Exception
    {
        if (terrainFormat() == null)
        {
            return "Iris is not loaded";
        }

        StringBuilder problems = new StringBuilder();
        int stockBroken = 0;

        for (int i = 0; i < rounds; ++i)
        {
            boolean was = setRenderingLevel(false);
            Object[] turbo = null;
            Object[] stock = null;

            try
            {
                turbo = turboMaker.apply(terrain);
                stock = stockMaker.apply(terrain);

                if (consistent(turbo[0], terrain) == false)
                {
                    problems.append("round ").append(i).append(": Turbo's builder has the extended format but does not fill it; ");
                }

                if (consistent(stock[0], terrain) == false)
                {
                    ++stockBroken;
                }
            }
            finally
            {
                setRenderingLevel(was);
                close(turbo);
                close(stock);
            }
        }

        TurboLog.info("Iris builder check: " + stockBroken + " of " + rounds + " stock builders were broken by the changed flag, Turbo's: " + (problems.length() == 0 ? "none" : "some"));
        return problems.toString();
    }

    private static void close(Object[] made) throws Exception
    {
        if (made != null)
        {
            ((AutoCloseable) made[1]).close();
        }
    }

    /** How often a build found the flag changed between the pipeline's answer and the builder (a stock builder would be broken then). */
    public static int races()
    {
        return races;
    }
}
