package com.litematicaturbo.shared;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;

/** Free graphics memory as the card reports it (NVIDIA: GL_NVX_gpu_memory_info, AMD: GL_ATI_meminfo), a read-only query. Render thread only. */
public final class GpuMemory
{
    private static final int GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
    private static final int TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    private GpuMemory()
    {
    }

    /** Free graphics memory in KB, or -1 when the card does not report it. */
    public static long freeKb()
    {
        GLCapabilities caps = GL.getCapabilities();
        int kb;

        if (caps.GL_NVX_gpu_memory_info)
        {
            kb = GL11.glGetInteger(GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX);
        }
        else if (caps.GL_ATI_meminfo)
        {
            kb = GL11.glGetInteger(TEXTURE_FREE_MEMORY_ATI);
        }
        else
        {
            return -1;
        }

        GL11.glGetError();
        return kb;
    }
}
