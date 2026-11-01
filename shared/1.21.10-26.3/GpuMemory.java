package com.litematicaturbo.shared;

/** Minecraft 1.21.10 draws through Mojang's graphics API, which reports no free memory: the complete copy keeps to the Java limit only. */
public final class GpuMemory
{
    private GpuMemory()
    {
    }

    /** Free graphics memory in KB, or -1 when it is not reported. */
    public static long freeKb()
    {
        return -1;
    }
}
