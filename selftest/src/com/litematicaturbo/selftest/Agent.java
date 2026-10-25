package com.litematicaturbo.selftest;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;

/**
 * Starts the in-game tests: a Java agent (-javaagent:litematica-turbo-selftest.jar, only added by
 * ingame-test.sh via -PselftestAgent) so that no test code is in the mod or its release jar. With
 * -Dlitematicaturbo.selftest=client it waits until the game client has loaded (Turbo started, a screen open),
 * runs the tests, prints the protocol and quits the game.
 *
 * Everything of Minecraft, Litematica and Turbo is reached by reflection through the class loader that loaded
 * Turbo, so the same compiled tests run on every Fabric / Forge / NeoForge version in versions/.
 */
public final class Agent
{
    /** The process's real stdout: the game later routes System.out through its logger. */
    static final PrintStream OUT = new PrintStream(new FileOutputStream(FileDescriptor.out), true);

    private Agent()
    {
    }

    public static void premain(String args, Instrumentation inst)
    {
        if ("client".equals(System.getProperty("litematicaturbo.selftest")) == false)
        {
            return;
        }

        Thread thread = new Thread(() -> {
            int code = 1;

            try
            {
                Ctx ctx = Ctx.waitForGame(inst, 10 * 60 * 1000L);

                if (ctx == null)
                {
                    OUT.println("SELFTEST harness: the game client did not finish loading (Turbo started, first screen open)");
                }
                else
                {
                    code = SelfTest.run(ctx, System.getProperty("litematicaturbo.selftest.filter", ""));
                }
            }
            catch (Throwable t)
            {
                OUT.println("SELFTEST harness crashed: " + t);
                t.printStackTrace(OUT);
            }

            OUT.flush();
            Runtime.getRuntime().halt(code);
        }, "Litematica Turbo self-test");
        thread.setDaemon(true);
        thread.start();
    }
}
