package com.litematicaturbo.mc1165.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import com.litematicaturbo.mc1165.TurboStartup;

/** Forge entry point (for Forgematica). */
@Mod("litematica_turbo")
public class LitematicaTurboForge
{
    public LitematicaTurboForge()
    {
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event)
    {
        TurboStartup.init(FMLPaths.CONFIGDIR.get());
    }
}
