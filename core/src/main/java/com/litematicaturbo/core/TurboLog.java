package com.litematicaturbo.core;

import java.util.function.Consumer;
import java.util.logging.Logger;

/** Tiny logging bridge so the core needs no logging library; each version wires it to its logger. */
public final class TurboLog
{
    private static final Logger BEFORE_BIND = Logger.getLogger("Litematica Turbo");
    private static volatile Consumer<String> info = BEFORE_BIND::info;
    private static volatile Consumer<String> warn = BEFORE_BIND::warning;
    private static volatile Consumer<String> error = BEFORE_BIND::severe;

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
