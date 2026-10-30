package com.litematicaturbo.shared;

import java.nio.file.Path;
import java.util.List;

import fi.dy.masa.malilib.event.TickHandler;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.shared.bench.TurboBench;

/** Loader-independent start-up (the Fabric, Forge and NeoForge entry points call init). */
public final class TurboStartup
{
    private TurboStartup()
    {
    }

    public static void init(Path configDir)
    {
        TurboLogger.bind();
        TurboConfig config = TurboConfig.load(configDir);
        TurboConfig.set(config);
        TurboLog.info(String.format("Loaded: enabled=%s, build threads=%d, skipEmptySections=%s, asyncNearRebuilds=%s",
                                    config.enabled, config.resolveBuildThreads(TurboConfig.CPU_COUNT),
                                    config.skipEmptySections, config.asyncNearRebuilds));

        // Fail-safe: a patch that does not fit the installed Litematica switches Turbo off
        List<String> failures = TurboMixinGuard.verify(TurboStartup.class.getClassLoader());

        if (failures.isEmpty() == false)
        {
            for (String failure : failures)
            {
                TurboLog.error("Patch did not apply: " + failure);
            }

            TurboConfig.markIncompatible(failures.size() + " of " + TurboMixinGuard.mixinCount() + " patches do not fit this Litematica version");
            TurboLog.error("Litematica Turbo is OFF for this session: " + TurboConfig.incompatibility());
        }

        for (String failure : TurboMixinGuard.optionalFailures())
        {
            TurboLog.warn("Settings tab patch did not apply (Turbo tab hidden, Turbo stays on): " + failure);
        }

        TurboSettingsTab.init();
        TickHandler.getInstance().registerClientTickHandler(SettingsTicker.INSTANCE);
        TurboBench.startIfRequested();

        // Dev check (-Dlitematicaturbo.audit=true): every patch applied? Then quit.
        if (Boolean.getBoolean("litematicaturbo.audit"))
        {
            int failed = failures.size() + TurboMixinGuard.optionalFailures().size();
            TurboLog.info("AUDIT " + TurboMixinGuard.mixinCount() + " mixins, failures: " + failed);
            System.exit(failed == 0 ? 0 : 1);
        }
    }
}
