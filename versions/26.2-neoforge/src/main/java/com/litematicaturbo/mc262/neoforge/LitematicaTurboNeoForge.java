package com.litematicaturbo.mc262.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

import com.litematicaturbo.mc262.TurboStartup;

/** NeoForge entry point (for Forgematica); client only. */
@Mod(value = "litematica_turbo", dist = Dist.CLIENT)
public class LitematicaTurboNeoForge
{
    public LitematicaTurboNeoForge(IEventBus modBus)
    {
        modBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event)
    {
        TurboStartup.init(FMLPaths.CONFIGDIR.get());
    }
}
