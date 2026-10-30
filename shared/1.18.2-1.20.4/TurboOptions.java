package com.litematicaturbo.shared;

import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;

/** How the settings tab's options are made with this malilib. */
final class TurboOptions
{
    private TurboOptions()
    {
    }

    static ConfigBoolean bool(String name, boolean defaultValue, String comment, String prettyName)
    {
        return new ConfigBoolean(name, defaultValue, comment, prettyName);
    }

    static ConfigInteger integer(String name, int defaultValue, int min, int max, String comment)
    {
        return new ConfigInteger(name, defaultValue, min, max, comment);
    }

    /** This malilib shows the names given above as they are. */
    static void translateNames(List<IConfigBase> options)
    {
    }
}
