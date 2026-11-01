package com.litematicaturbo.shared;

/** How a version moves the drawing of a region to its place in the world: each version's shader has its own way. */
@FunctionalInterface
public interface BatchPlacement
{
    /** Runs the draw with the shader set up for a region whose corner is at x, y, z relative to the camera. */
    void draw(float x, float y, float z, Runnable draw);
}
