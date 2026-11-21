package com.litematicaturbo.shared;

/**
 * Marks Litematica's world renderer where Turbo switches the see-through re-sort off while its build threads run
 * (WorldRendererSchematicResortMixin, 1.16.5 - 1.18.2): there no re-sort copy is ever used (see SortCopySweep).
 */
public interface ResortOffWithThreads
{
}
