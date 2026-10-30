package com.litematicaturbo.shared;

import java.util.List;
import java.util.Locale;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.util.StringUtils;

/** How the settings tab's options are made with this malilib (0.21+). */
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

    /** malilib 0.21+ shows the raw option name unless a translated name is set. */
    static void translateNames(List<IConfigBase> options)
    {
        for (IConfigBase option : options)
        {
            ((ConfigBase<?>) option).setTranslatedName(StringUtils.translate("config.name." + option.getName().toLowerCase(Locale.ROOT)));
        }
    }
}
