package com.litematicaturbo.core;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Turbo's schematic build threads, as each version's build dispatcher mixin starts and waits for them. */
public final class BuildThreads
{
    private BuildThreads()
    {
    }

    /** How many build threads to start (0: none, Litematica builds on the render thread). */
    public static int count()
    {
        return TurboConfig.get().resolveBuildThreads(TurboConfig.CPU_COUNT);
    }

    /** One buffer cache per build thread plus a spare (stock: the given number). */
    public static int bufferCaches(int stock)
    {
        int threads = count();
        return threads > 0 ? threads + 1 : stock;
    }

    /** A build thread running the given worker (daemon, just below normal priority). */
    public static Thread newThread(Runnable worker, String name)
    {
        Thread thread = new Thread(worker, name);
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return thread;
    }

    /** "Litematica Turbo Builder n" (n from 1). */
    public static String name(int index)
    {
        return "Litematica Turbo Builder " + index;
    }

    /** Starts the build threads, each running a new worker; both go into the dispatcher's lists. */
    public static <W extends Runnable> void start(Supplier<W> newWorker, List<W> workers, List<Thread> threads)
    {
        int count = count();

        for (int i = 0; i < count; ++i)
        {
            W worker = newWorker.get();
            Thread thread = newThread(worker, name(i + 1));
            thread.start();
            workers.add(worker);
            threads.add(thread);
        }

        logStarted(count);
    }

    public static void logStarted(int count)
    {
        if (count > 0)
        {
            TurboLog.info("Started " + count + " schematic build threads");
        }
    }

    /**
     * stopChunkUpdates() waits for every buffer cache to come back, but a worker only returns its
     * cache after the render thread has run its uploads. Keep running uploads while waiting,
     * otherwise the game freezes when renderers are reloaded during a build.
     */
    public static <C> C takeWhileUploading(BlockingQueue<C> freeCaches, Runnable runUploads) throws InterruptedException
    {
        while (true)
        {
            C cache = freeCaches.poll(5, TimeUnit.MILLISECONDS);

            if (cache != null)
            {
                return cache;
            }

            runUploads.run();
        }
    }
}
