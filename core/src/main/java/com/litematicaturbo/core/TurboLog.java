package com.litematicaturbo.core;

import java.util.function.Consumer;

/** Tiny logging bridge so the core needs no logging library; each version wires it to its logger. */
public final class TurboLog
{
    private static volatile Consumer<String> info = msg -> System.out.println("[Litematica Turbo] " + msg);
    private static volatile Consumer<String> warn = msg -> System.err.println("[Litematica Turbo] " + msg);

    private TurboLog()
    {
    }

    public static void bind(Consumer<String> infoSink, Consumer<String> warnSink)
    {
        info = infoSink;
        warn = warnSink;
    }

    public static void info(String msg)
    {
        info.accept(msg);
    }

    public static void warn(String msg)
    {
        warn.accept(msg);
    }
}
