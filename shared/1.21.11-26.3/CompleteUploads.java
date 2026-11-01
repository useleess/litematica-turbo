package com.litematicaturbo.shared;

import fi.dy.masa.litematica.render.schematic.ChunkRenderGpuUploader;

/**
 * Added to Litematica's chunk renderer: a complete-copy piece keeps its graphics buffers in a GPU uploader of its own
 * (stock keeps one per chunk column, which the view's chunk renderer there uses).
 */
public interface CompleteUploads
{
    /** The piece's own uploader; null for every other chunk renderer. */
    ChunkRenderGpuUploader turbo$completeUploader();
}
