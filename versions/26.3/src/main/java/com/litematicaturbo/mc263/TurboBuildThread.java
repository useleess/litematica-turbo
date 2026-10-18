package com.litematicaturbo.mc263;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import fi.dy.masa.litematica.render.schematic.ChunkRenderTaskSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderWorkerLitematica;

import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.mc263.mixin.ChunkRenderDispatcherLitematicaInvoker;
import com.litematicaturbo.mc263.mixin.ChunkRenderWorkerLitematicaInvoker;

/**
 * Litematica 0.26 has no build threads of its own any more: every schematic chunk is built on the
 * render thread. Each of these takes tasks from Litematica's queue and builds them with its own
 * worker (the upload still happens on the render thread).
 */
public final class TurboBuildThread extends Thread
{
    private final ChunkRenderDispatcherLitematica dispatcher;
    private final ChunkRenderWorkerLitematica worker;
    private volatile boolean running = true;

    public TurboBuildThread(ChunkRenderDispatcherLitematica dispatcher, int index)
    {
        super("Litematica Turbo Builder " + index);
        this.dispatcher = dispatcher;
        this.worker = new ChunkRenderWorkerLitematica(dispatcher);
        this.setDaemon(true);
        this.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
    }

    public void shutdown()
    {
        this.running = false;
        this.interrupt();
    }

    @Override
    public void run()
    {
        while (this.running)
        {
            try
            {
                ChunkRenderTaskSchematic task = ((ChunkRenderDispatcherLitematicaInvoker) this.dispatcher).turbo$getNextChunkUpdate();
                ((ChunkRenderWorkerLitematicaInvoker) this.worker).turbo$processTask(task);
            }
            catch (InterruptedException e)
            {
                return;
            }
            catch (Throwable t)
            {
                // A chunk that fails to build must not take the thread down with it
                TurboLog.warn("Schematic chunk build failed: " + t);
            }
        }
    }
}
