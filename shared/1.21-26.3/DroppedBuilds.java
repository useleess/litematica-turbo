package com.litematicaturbo.shared;

import com.litematicaturbo.core.BuildErrors;

/** What happens to a chunk whose build threw an error on a build thread. */
public final class DroppedBuilds
{
    private DroppedBuilds()
    {
    }

    /** The build is dropped (the chunk keeps its last finished picture), the error is logged and the chunk is queued for another build. */
    public static void drop(TurboRenderer renderer, RuntimeException error)
    {
        BuildErrors.drop(error);
        renderer.turbo$cancelCompile();

        if (BuildErrors.mayRebuild())
        {
            renderer.turbo$setNeedsUpdate(false);
        }
    }
}
