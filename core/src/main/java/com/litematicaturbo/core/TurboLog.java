package com.litematicaturbo.core;

import java.util.function.Consumer;

/** Tiny logging bridge so the core needs no logging library; each version wires it to its logger. */
public final class TurboLog
{
    private static volatile Consumer<String> info = msg -> System.out.println("[Litematica Turbo] " + msg);
    private static volatile Consumer<String> warn = msg -> System.err.println("[Litematica Turbo] " + msg);
    private static volatile Consumer<String> error = msg -> System.err.println("[Litematica Turbo] " + msg);

    private TurboLog()
    {
    }

    public static void bind(Consumer<String> infoSink, Consumer<String> warnSink, Consumer<String> errorSink)
    {
        info = infoSink;
        warn = warnSink;
        error = errorSink;
    }

    public static void info(String msg)
    {
        info.accept(msg);
    }

    public static void warn(String msg)
    {
        warn.accept(msg);
    }

    public static void error(String msg)
    {
        error.accept(msg);
    }
}
