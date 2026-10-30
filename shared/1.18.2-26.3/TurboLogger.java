package com.litematicaturbo.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litematicaturbo.core.TurboLog;

/** Connects core's TurboLog to the game's log (slf4j on these versions). */
final class TurboLogger
{
    private TurboLogger()
    {
    }

    static void bind()
    {
        Logger logger = LoggerFactory.getLogger("Litematica Turbo");
        TurboLog.bind(logger::info, logger::warn, logger::error);
    }
}
