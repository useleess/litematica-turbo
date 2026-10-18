package com.litematicaturbo.mc1182;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric entry point. */
public class LitematicaTurbo implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        TurboStartup.init(FabricLoader.getInstance().getConfigDir());
    }
}
