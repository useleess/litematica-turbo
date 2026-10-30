package com.litematicaturbo.selftest;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * The running game as the tests see it: Turbo's version package (com.litematicaturbo.mcXXXX), the class loader
 * that sees Turbo, Litematica, malilib and Minecraft, the client object, and reflection helpers.
 */
final class Ctx
{
    private static final Pattern STARTUP = Pattern.compile("com\\.litematicaturbo\\.mc[0-9]+\\.TurboStartup");

    final ClassLoader loader;
    /** e.g. com.litematicaturbo.mc1201 */
    final String pkg;
    final Class<?> clientClass;
    final Object client;
    /** What holds the open screen: the client, or (26.x) its gui object. */
    private final Object screenHolder;
    private final Field screenField;

    private Ctx(ClassLoader loader, String pkg, Class<?> clientClass, Object client, Object screenHolder, Field screenField)
    {
        this.loader = loader;
        this.pkg = pkg;
        this.clientClass = clientClass;
        this.client = client;
        this.screenHolder = screenHolder;
        this.screenField = screenField;
    }

    /**
     * Waits until Turbo has started (its settings tab is set up) and the client shows its first screen with
     * no loading overlay left. Null when that does not happen in time.
     */
    static Ctx waitForGame(Instrumentation inst, long timeoutMs) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Class<?> startup = null;
        Class<?> clientClass = null;
        int stable = 0;
        long screenWithoutTurbo = 0;
        long loadedSince = 0;

        while (System.currentTimeMillis() < deadline)
        {
            Thread.sleep(1000);

            // Nothing of the game is touched by reflection before its loading ends (the title or a loading error
            // screen class exists): resolving classes from this thread while Forge still loads mods broke its
            // event bus once ("Error computing listener list for LevelEvent$Unload").
            if (startup == null || clientClass == null || loadedSince == 0)
            {
                for (Class<?> c : inst.getAllLoadedClasses())
                {
                    String name = c.getName();

                    if (loadedSince == 0 && name.startsWith("net.minecraft") && (name.endsWith(".TitleScreen") || name.endsWith("LoadingErrorScreen")))
                    {
                        loadedSince = System.currentTimeMillis();
                    }

                    if (startup == null && STARTUP.matcher(name).matches())
                    {
                        startup = c;
                    }
                    else if (clientClass == null && (name.equals("net.minecraft.client.MinecraftClient") || name.equals("net.minecraft.client.Minecraft")))
                    {
                        clientClass = c;
                    }
                }

                if (clientClass == null)
                {
                    continue;
                }
            }

            if (loadedSince == 0 || System.currentTimeMillis() - loadedSince < 5000)
            {
                continue;
            }

            try
            {
                Object client = null;

                for (Field f : clientClass.getDeclaredFields())
                {
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == clientClass)
                    {
                        f.setAccessible(true);
                        client = f.get(null);
                    }
                }

                // the open screen and loading overlay: fields of the client, or (26.x) of its gui object
                Object holder = client;

                if (client != null && instanceFieldOfType(clientClass, "Screen") == null)
                {
                    holder = null;

                    for (Field f : clientClass.getFields())
                    {
                        if (Modifier.isStatic(f.getModifiers()) == false && instanceFieldOfType(f.getType(), "Screen") != null)
                        {
                            holder = f.get(client);
                        }
                    }
                }

                Field screen = holder == null ? null : instanceFieldOfType(holder.getClass(), "Screen");
                Field overlay = holder == null ? null : instanceFieldOfType(holder.getClass(), "Overlay");
                String pkg = null;
                ClassLoader loader = null;
                boolean started = false;

                if (startup != null)
                {
                    pkg = startup.getName().substring(0, startup.getName().lastIndexOf('.'));
                    loader = startup.getClassLoader();
                    // Turbo's start-up has run: it registers the tab options' callbacks, then syncs them from the file
                    Object configBase = Class.forName(pkg + ".TurboSettingsTab", true, loader).getField("ENABLED").get(null);
                    started = getField(configBase, "callback") != null;
                }

                if (client != null && screen != null && started && screen.get(holder) != null && (overlay == null || overlay.get(holder) == null))
                {
                    if (++stable >= 3)
                    {
                        return new Ctx(loader, pkg, clientClass, client, holder, screen);
                    }
                }
                else
                {
                    stable = 0;
                }

                // a screen but Turbo never started: a mod loading error screen; give up early so the run is retried
                if (client != null && screen != null && started == false && screen.get(holder) != null)
                {
                    screenWithoutTurbo = screenWithoutTurbo == 0 ? System.currentTimeMillis() : screenWithoutTurbo;

                    if (System.currentTimeMillis() - screenWithoutTurbo > 120_000L)
                    {
                        Agent.OUT.println("SELFTEST harness: the game shows " + screen.get(holder).getClass().getName() + " but Turbo did not start (mod loading error?)");
                        return null;
                    }
                }
            }
            catch (ReflectiveOperationException | RuntimeException e)
            {
                stable = 0; // classes still initialising
            }
        }

        return null;
    }

    private static Field instanceFieldOfType(Class<?> owner, String simpleTypeName)
    {
        for (Field f : owner.getDeclaredFields())
        {
            if (Modifier.isStatic(f.getModifiers()) == false && f.getType().getSimpleName().equals(simpleTypeName))
            {
                f.setAccessible(true);
                return f;
            }
        }

        return null;
    }

    Object screen() throws IllegalAccessException
    {
        return this.screenField.get(this.screenHolder);
    }

    /** Opens a screen (null = close) the way the game does. */
    void setScreen(Object screen) throws ReflectiveOperationException
    {
        for (Method m : this.screenHolder.getClass().getMethods())
        {
            if ((m.getName().equals("setScreen") || m.getName().equals("openScreen")) && m.getParameterCount() == 1 &&
                m.getParameterTypes()[0] == this.screenField.getType())
            {
                m.invoke(this.screenHolder, screen);
                return;
            }
        }

        throw new NoSuchMethodException("no setScreen/openScreen on " + this.screenHolder.getClass().getName());
    }

    /** Runs the task on the render thread and waits for its result. */
    <T> T onMain(Callable<T> task) throws Exception
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        ((Executor) this.client).execute(() -> {
            try
            {
                future.complete(task.call());
            }
            catch (Throwable t)
            {
                future.completeExceptionally(t);
            }
        });

        try
        {
            // room for a busy test box: 12 games at once stalled 1.17.1-forge's client thread past 30 s (2026-10-30)
            return future.get(120, TimeUnit.SECONDS);
        }
        catch (java.util.concurrent.ExecutionException e)
        {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
        }
    }

    /** Lets the game run a few client ticks. */
    void ticks(int n) throws Exception
    {
        for (int i = 0; i < n; i++)
        {
            this.onMain(() -> null);
            Thread.sleep(60);
        }
    }

    Class<?> cls(String name) throws ClassNotFoundException
    {
        return Class.forName(name, true, this.loader);
    }

    /** A class of Turbo's version package, e.g. turbo("TurboSettingsTab"). */
    Class<?> turbo(String simpleName) throws ClassNotFoundException
    {
        return this.cls(this.pkg + "." + simpleName);
    }

    // ---- reflection helpers ----

    static Field field(Class<?> owner, String name) throws NoSuchFieldException
    {
        for (Class<?> c = owner; c != null; c = c.getSuperclass())
        {
            try
            {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            }
            catch (NoSuchFieldException e)
            {
                // try the superclass
            }
        }

        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    static Object getField(Object obj, String name) throws ReflectiveOperationException
    {
        return field(obj.getClass(), name).get(obj);
    }

    static void setField(Object obj, String name, Object value) throws ReflectiveOperationException
    {
        field(obj.getClass(), name).set(obj, value);
    }

    static Object getStatic(Class<?> owner, String name) throws ReflectiveOperationException
    {
        return field(owner, name).get(null);
    }

    static void setStatic(Class<?> owner, String name, Object value) throws ReflectiveOperationException
    {
        field(owner, name).set(null, value);
    }

    /** Calls the method named name with args.length parameters whose types fit the arguments. */
    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException
    {
        Class<?> owner = target instanceof Class ? (Class<?>) target : target.getClass();
        Object self = target instanceof Class ? null : target;

        for (Class<?> c = owner; c != null; c = c.getSuperclass())
        {
            Method m = fitting(c.getDeclaredMethods(), name, args);

            if (m != null)
            {
                m.setAccessible(true);
                return invoke(m, self, args);
            }
        }

        Method m = fitting(owner.getMethods(), name, args); // interface default methods

        if (m != null)
        {
            return invoke(m, self, args);
        }

        throw new NoSuchMethodException(owner.getName() + "." + name + " with " + args.length + " arguments");
    }

    private static Object invoke(Method m, Object self, Object[] args) throws ReflectiveOperationException
    {
        try
        {
            return m.invoke(self, args);
        }
        catch (java.lang.reflect.InvocationTargetException e)
        {
            Throwable cause = e.getCause();

            if (cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }

            if (cause instanceof Error)
            {
                throw (Error) cause;
            }

            throw e;
        }
    }

    private static Method fitting(Method[] methods, String name, Object[] args)
    {
        outer:
        for (Method m : methods)
        {
            if (m.getName().equals(name) == false || m.getParameterCount() != args.length)
            {
                continue;
            }

            Class<?>[] types = m.getParameterTypes();

            for (int i = 0; i < types.length; i++)
            {
                if (args[i] != null && box(types[i]).isInstance(args[i]) == false)
                {
                    continue outer;
                }
            }

            return m;
        }

        return null;
    }

    private static Class<?> box(Class<?> type)
    {
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        return type;
    }
}
