package com.litematicaturbo.shared;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.litematicaturbo.core.TurboLog;

/** Connects core's TurboLog to the game's log (log4j on these versions). */
final class TurboLogger
{
    private TurboLogger()
    {
    }

    static void bind()
    {
        Logger logger = LogManager.getLogger("Litematica Turbo");
        TurboLog.bind(logger::info, logger::warn, logger::error);
    }
}
