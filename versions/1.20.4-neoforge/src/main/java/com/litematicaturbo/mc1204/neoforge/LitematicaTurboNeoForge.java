package com.litematicaturbo.mc1204.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import com.litematicaturbo.mc1204.TurboStartup;

/** NeoForge entry point (for Forgematica). */
@Mod("litematica_turbo")
public class LitematicaTurboNeoForge
{
    public LitematicaTurboNeoForge(IEventBus modBus)
    {
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            modBus.addListener(this::onClientSetup);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event)
    {
        TurboStartup.init(FMLPaths.CONFIGDIR.get());
    }
}
