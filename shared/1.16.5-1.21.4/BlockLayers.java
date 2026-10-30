package com.litematicaturbo.shared;

/** Litematica's world renderer draws each block layer in renderBlockLayer(...) on these versions. */
public final class BlockLayers
{
    /** Mixin target of the ghost-move draw swap (WorldRendererSchematicMixin). */
    public static final String METHOD = "renderBlockLayer*";

    private BlockLayers()
    {
    }
}
