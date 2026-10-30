package com.litematicaturbo.mc12110;

/** Added to Litematica's chunk renderer (ChunkRendererSchematicVboGpuMixin). */
public interface TurboGpuBuffers
{
    /** Closes GPU buffers of layers the current build data no longer uses (render thread). */
    void turbo$dropUnusedGpuBuffers();
}
