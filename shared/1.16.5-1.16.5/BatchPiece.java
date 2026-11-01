package com.litematicaturbo.shared;

/** Added to Litematica's chunk renderer: counts how often it got new built data, so batch drawing sees a rebuilt piece. */
public interface BatchPiece
{
    int turbo$batchVersion();
}
