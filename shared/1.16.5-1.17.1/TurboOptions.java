package com.litematicaturbo.shared;

import java.util.List;
import java.util.Locale;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.util.StringUtils;

/** How the settings tab's options are made with this (older) malilib. */
final class TurboOptions
{
    private TurboOptions()
    {
    }

    /** Old malilib labels options with their raw name; show the translated one ("Turbo enabled"). */
    private static String displayName(String name)
    {
        String key = "config.name." + name.toLowerCase(Locale.ROOT);
        String translated = StringUtils.translate(key);
        return key.equals(translated) ? name : translated;
    }

    static ConfigBoolean bool(String name, boolean defaultValue, String comment, String prettyName)
    {
        return new ConfigBoolean(name, defaultValue, comment, prettyName)
        {
            @Override
            public String getConfigGuiDisplayName()
            {
                return displayName(this.getName());
            }
        };
    }

    static ConfigInteger integer(String name, int defaultValue, int min, int max, String comment)
    {
        return new ConfigInteger(name, defaultValue, min, max, comment)
        {
            @Override
            public String getConfigGuiDisplayName()
            {
                return displayName(this.getName());
            }
        };
    }

    /** This malilib shows the names given above as they are. */
    static void translateNames(List<IConfigBase> options)
    {
    }
}
