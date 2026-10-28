package com.litematicaturbo.selftest;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The running world as the in-world tests (build/, near/, empty/) need it: opening the test world from the title
 * screen, the player, block positions, the block registry. Yarn and Mojang names alike: a name is looked up from a
 * list of alternatives, or found by the type it returns.
 */
final class Game
{
    private static final String[] BLOCK_REGISTRY_CLASSES = {
        "net.minecraft.registry.Registries", "net.minecraft.core.registries.BuiltInRegistries",
        "net.minecraft.util.registry.Registry", "net.minecraft.core.Registry"};
    private static final String[] BLOCK_POS_CLASSES = {"net.minecraft.util.math.BlockPos", "net.minecraft.core.BlockPos"};
    private static final String[] BLOCK_ENTITY_PROVIDERS = {"net.minecraft.block.BlockEntityProvider", "net.minecraft.world.level.block.EntityBlock"};

    final Ctx ctx;
    private Class<?> blockPosClass;

    Game(Ctx ctx)
    {
        this.ctx = ctx;
    }

    // ---- name alternatives ----

    /** Calls the first of the methods named in names that exists with these arguments. */
    static Object callAny(Object target, String[] names, Object... args) throws ReflectiveOperationException
    {
        NoSuchMethodException last = null;

        for (String name : names)
        {
            try
            {
                return Ctx.call(target, name, args);
            }
            catch (NoSuchMethodException e)
            {
                last = e;
            }
        }

        throw last != null ? last : new NoSuchMethodException("no method names given");
    }

    static boolean has(Object target, String name, int args)
    {
        Class<?> owner = target instanceof Class ? (Class<?>) target : target.getClass();

        for (Class<?> c = owner; c != null; c = c.getSuperclass())
        {
            for (Method m : c.getDeclaredMethods())
            {
                if (m.getName().equals(name) && m.getParameterCount() == args)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** The value of the first instance field of the object whose type has this simple name. */
    static Object fieldOfType(Object holder, String... simpleTypeNames) throws ReflectiveOperationException
    {
        for (Class<?> c = holder.getClass(); c != null; c = c.getSuperclass())
        {
            for (Field f : c.getDeclaredFields())
            {
                if (Modifier.isStatic(f.getModifiers()) == false)
                {
                    for (String n : simpleTypeNames)
                    {
                        if (f.getType().getSimpleName().equals(n))
                        {
                            f.setAccessible(true);
                            return f.get(holder);
                        }
                    }
                }
            }
        }

        return null;
    }

    // ---- the world ----

    Object clientWorld() throws ReflectiveOperationException
    {
        return fieldOfType(this.ctx.client, "ClientWorld", "ClientLevel");
    }

    Object player() throws ReflectiveOperationException
    {
        return fieldOfType(this.ctx.client, "ClientPlayerEntity", "LocalPlayer");
    }

    /** Runs a chat command (without the slash) as the player; the test world allows commands. */
    void command(String text) throws Exception
    {
        this.ctx.onMain(() -> {
            Object player = this.player();
            Object handler = fieldOfType(player, "ClientPlayNetworkHandler", "ClientPacketListener");

            if (handler != null)
            {
                for (String name : new String[] {"sendChatCommand", "sendCommand", "sendUnsignedCommand"})
                {
                    try
                    {
                        Ctx.call(handler, name, text);
                        return null;
                    }
                    catch (NoSuchMethodException e)
                    {
                        // an older / newer version names it differently
                    }
                }
            }

            callAny(player, new String[] {"sendChatMessage", "chat"}, "/" + text); // before 1.19.1
            return null;
        });
    }

    /**
     * Writes the flat test world into saves/ of the game folder and opens it from the title screen. Returns when the
     * player is in the world and has stopped falling.
     */
    void join(String levelName) throws Exception
    {
        File worldDir = new File(System.getProperty("user.dir"), "saves/" + levelName);
        WorldFiles.writeLevelDat(worldDir, levelName);
        this.ctx.onMain(() -> {
            this.openWorld(levelName);
            return null;
        });

        long deadline = System.currentTimeMillis() + 5 * 60_000L;

        while (System.currentTimeMillis() < deadline)
        {
            Thread.sleep(250);
            this.ctx.onMain(() -> {
                this.answerPrompts();
                return null;
            });

            if (this.ctx.onMain(() -> this.clientWorld() != null && this.player() != null))
            {
                break;
            }
        }

        if (this.ctx.onMain(() -> this.clientWorld() != null && this.player() != null) == false)
        {
            Object screen = this.ctx.onMain(() -> this.ctx.screen());
            throw new SelfTest.Fail("the test world did not open in 5 minutes (screen: " + (screen == null ? "none" : screen.getClass().getName()) + ")");
        }

        // wait for the player to land: the same position for 3 seconds
        int[] last = null;
        int still = 0;

        for (int i = 0; i < 600 && still < 40; i++)
        {
            Thread.sleep(75);
            int[] now = this.ctx.onMain(this::playerPos);
            still = last != null && now[0] == last[0] && now[1] == last[1] && now[2] == last[2] ? still + 1 : 0;
            last = now;
        }
    }

    /**
     * The screens the game puts in front of an old-format world ("back up and load?", "experimental settings", ...):
     * answered with "go on, no backup" through the screen's own callback, found by its type.
     */
    private void answerPrompts() throws Exception
    {
        Object screen = this.ctx.screen();

        if (screen == null)
        {
            return;
        }

        String name = screen.getClass().getSimpleName();

        if (name.equals("BackupPromptScreen") == false && name.equals("ConfirmScreen") == false && name.equals("ConfirmLinkScreen") == false)
        {
            return;
        }

        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass())
        {
            for (Field f : c.getDeclaredFields())
            {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isInterface() == false)
                {
                    continue;
                }

                for (Method m : f.getType().getMethods())
                {
                    Class<?>[] p = m.getParameterTypes();

                    if (Modifier.isAbstract(m.getModifiers()) == false || m.getReturnType() != void.class)
                    {
                        continue;
                    }

                    Object[] args = null;

                    if (p.length == 2 && p[0] == boolean.class && p[1] == boolean.class)
                    {
                        args = new Object[] {false, false}; // BackupPromptScreen.Callback: no backup, keep the cache
                    }
                    else if (p.length == 1 && p[0] == boolean.class)
                    {
                        args = new Object[] {true}; // ConfirmScreen's BooleanConsumer: yes
                    }

                    if (args != null)
                    {
                        f.setAccessible(true);
                        Object cb = f.get(screen);

                        if (cb != null)
                        {
                            m.setAccessible(true);
                            m.invoke(cb, args);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void openWorld(String levelName) throws Exception
    {
        Method direct = declared(this.ctx.clientClass, "startIntegratedServer", String.class);

        if (direct != null)
        {
            direct.setAccessible(true);
            direct.invoke(this.ctx.client, levelName);
            return;
        }

        for (Method m : this.ctx.clientClass.getMethods())
        {
            String type = m.getReturnType().getSimpleName();

            if (m.getParameterCount() == 0 && (type.equals("IntegratedServerLoader") || type.equals("WorldOpenFlows")))
            {
                Object loader = m.invoke(this.ctx.client);

                for (Method lm : loader.getClass().getMethods())
                {
                    Class<?>[] p = lm.getParameterTypes();

                    if ((lm.getName().equals("start") || lm.getName().equals("openWorld")) && p.length == 2)
                    {
                        if (p[0].getSimpleName().equals("Screen") && p[1] == String.class)
                        {
                            lm.invoke(loader, this.ctx.screen(), levelName);
                            return;
                        }

                        if (p[0] == String.class && p[1] == Runnable.class)
                        {
                            lm.invoke(loader, levelName, (Runnable) () -> { });
                            return;
                        }
                    }
                }
            }
        }

        throw new SelfTest.Fail("no way found to open a world from the title screen on this version");
    }

    private static Method declared(Class<?> owner, String name, Class<?>... params)
    {
        for (Class<?> c = owner; c != null; c = c.getSuperclass())
        {
            try
            {
                return c.getDeclaredMethod(name, params);
            }
            catch (NoSuchMethodException e)
            {
                // next superclass
            }
        }

        return null;
    }

    /** The player's block position {x, y, z}. */
    int[] playerPos() throws ReflectiveOperationException
    {
        Object pos = callAny(this.player(), new String[] {"getBlockPos", "blockPosition"});
        return new int[] {(Integer) Ctx.call(pos, "getX"), (Integer) Ctx.call(pos, "getY"), (Integer) Ctx.call(pos, "getZ")};
    }

    int bottomY() throws ReflectiveOperationException
    {
        try
        {
            return (Integer) callAny(this.clientWorld(), new String[] {"getBottomY", "getMinBuildHeight", "getMinY"});
        }
        catch (NoSuchMethodException e)
        {
            return 0; // before 1.17 the world starts at 0
        }
    }

    int worldHeight() throws ReflectiveOperationException
    {
        try
        {
            return (Integer) callAny(this.clientWorld(), new String[] {"getHeight"});
        }
        catch (NoSuchMethodException e)
        {
            return 256;
        }
    }

    Object blockPos(int x, int y, int z) throws Exception
    {
        if (this.blockPosClass == null)
        {
            for (String n : BLOCK_POS_CLASSES)
            {
                try
                {
                    this.blockPosClass = this.ctx.cls(n);
                    break;
                }
                catch (ClassNotFoundException e)
                {
                    // the other naming
                }
            }
        }

        Constructor<?> c = this.blockPosClass.getConstructor(int.class, int.class, int.class);
        return c.newInstance(x, y, z);
    }

    // ---- blocks ----

    /** Every state of every block in the game's block registry, in registry order. */
    List<Object> allBlockStates() throws Exception
    {
        Iterable<?> registry = null;

        for (String n : BLOCK_REGISTRY_CLASSES)
        {
            try
            {
                registry = (Iterable<?>) Ctx.getStatic(this.ctx.cls(n), "BLOCK");
                break;
            }
            catch (ReflectiveOperationException e)
            {
                // the other naming
            }
        }

        if (registry == null)
        {
            throw new SelfTest.Fail("the block registry was not found");
        }

        List<Object> states = new ArrayList<>();

        for (Object block : registry)
        {
            Object manager = null;

            for (Method m : block.getClass().getMethods())
            {
                String type = m.getReturnType().getSimpleName();

                if (m.getParameterCount() == 0 && (type.equals("StateManager") || type.equals("StateDefinition")))
                {
                    manager = m.invoke(block);
                    break;
                }
            }

            if (manager == null)
            {
                throw new SelfTest.Fail("block " + block + " has no state manager");
            }

            for (Method m : manager.getClass().getMethods())
            {
                if (m.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(m.getReturnType()) && m.getDeclaringClass() != Object.class)
                {
                    states.addAll((List<?>) m.invoke(manager));
                    break;
                }
            }
        }

        return states;
    }

    boolean hasBlockEntity(Object state) throws Exception
    {
        Object block = Ctx.call(state, "getBlock");

        for (String n : BLOCK_ENTITY_PROVIDERS)
        {
            try
            {
                if (this.ctx.cls(n).isInstance(block))
                {
                    return true;
                }
            }
            catch (ClassNotFoundException e)
            {
                // the other naming
            }
        }

        return false;
    }

    /** "minecraft:oak_stairs" and "facing=east,half=bottom" of a state, read from its text form Block{name}[props]. */
    static String[] describe(Object state)
    {
        String s = String.valueOf(state);
        int open = s.indexOf('{');
        int close = s.indexOf('}');
        String name = s.substring(open + 1, close);
        int bracket = s.indexOf('[', close);
        return new String[] {name, bracket < 0 ? "" : s.substring(bracket + 1, s.lastIndexOf(']'))};
    }
}
