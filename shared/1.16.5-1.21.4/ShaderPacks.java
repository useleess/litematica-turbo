package com.litematicaturbo.shared;

import java.lang.reflect.Method;

import com.litematicaturbo.core.TurboLog;

/**
 * Whether a shader pack is drawing the world. Iris replaces the game's shaders, and batch drawing's region placement is
 * written for the game's own, so batch drawing gives way while one is on. Asked by name (Turbo has no compile-time
 * link to Iris); with no Iris, or one that cannot be asked, nothing is paused / everything is. Render thread only.
 */
final class ShaderPacks
{
    private static final String[] IRIS_API = {"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"};
    private static final String NAME = "Iris";

    private static Object api;
    private static Method inUse;
    private static boolean absent;
    private static boolean unanswerable;

    private ShaderPacks()
    {
    }

    /** The name of the mod whose shaders draw the world differently right now (for the chat and the log), or null when none does. */
    static String drawing()
    {
        if (absent)
        {
            return null;
        }

        if (unanswerable)
        {
            return NAME;
        }

        try
        {
            if (inUse == null)
            {
                find();
            }

            return (Boolean) inUse.invoke(api) ? NAME : null;
        }
        catch (ClassNotFoundException e)
        {
            absent = true;
            return null;
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            TurboLog.warn("Iris is installed but cannot be asked whether shaders are on (" + e + "); batch drawing stays off to be safe");
            unanswerable = true;
            return NAME;
        }
    }

    private static void find() throws ReflectiveOperationException
    {
        ClassNotFoundException missing = null;

        for (String name : IRIS_API)
        {
            try
            {
                Class<?> type = Class.forName(name);
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
                return;
            }
            catch (ClassNotFoundException e)
            {
                missing = e;
            }
        }

        throw missing;
    }
}
