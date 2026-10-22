package com.litematicaturbo.mc1214;

import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.dy.masa.malilib.event.TickHandler;

import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.mc1214.bench.TurboBench;

/** Loader-independent start-up (the Fabric, Forge and NeoForge entry points call init). */
public final class TurboStartup
{
    public static final Logger LOGGER = LoggerFactory.getLogger("Litematica Turbo");

    private TurboStartup()
    {
    }

    public static void init(Path configDir)
    {
        TurboLog.bind(LOGGER::info, LOGGER::warn);
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
                LOGGER.error("Patch did not apply: {}", failure);
            }

            TurboConfig.markIncompatible(failures.size() + " of " + TurboMixinGuard.mixinCount() + " patches do not fit this Litematica version");
            LOGGER.error("Litematica Turbo is OFF for this session: {}", TurboConfig.incompatibility());
        }

        for (String failure : TurboMixinGuard.optionalFailures())
        {
            LOGGER.warn("Settings tab patch did not apply (Turbo tab hidden, Turbo stays on): {}", failure);
        }

        TurboSettingsTab.init();
        TickHandler.getInstance().registerClientTickHandler(TurboSettingsTab.INSTANCE);
        TurboBench.startIfRequested();

        // Dev check (-Dlitematicaturbo.audit=true): every patch applied? Then quit.
        if (Boolean.getBoolean("litematicaturbo.audit"))
        {
            int failed = failures.size() + TurboMixinGuard.optionalFailures().size();
            LOGGER.info("AUDIT {} mixins, failures: {}", TurboMixinGuard.mixinCount(), failed);
            System.exit(failed == 0 ? 0 : 1);
        }
    }
}
