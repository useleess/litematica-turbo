package com.litematicaturbo.shared.bench;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Function;

import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;

import com.litematicaturbo.core.TurboBenchRun;
import com.litematicaturbo.shared.TurboSettingsTab;

/**
 * The game side of the dev-only benchmark (core TurboBenchRun): the part that is the same on every version.
 * BenchHost adds the Minecraft calls of its naming family (Yarn or Mojang names), each version's TurboBench
 * the frame hook and what differs between versions.
 */
public abstract class BenchBase implements TurboBenchRun.Host, IClientTickHandler, IRenderer
{
    protected final TurboBenchRun run;

    protected BenchBase(File schematic)
    {
        this.run = new TurboBenchRun(this, schematic);
    }

    /** Arms the benchmark when -Dlitematicaturbo.bench asks for one. */
    protected static void start(Function<File, BenchBase> create)
    {
        File schematic = TurboBenchRun.requestedSchematic();

        if (schematic != null)
        {
            BenchBase bench = create.apply(schematic);
            TickHandler.getInstance().registerClientTickHandler(bench);
            RenderEventHandler.getInstance().registerWorldLastRenderer(bench);
        }
    }

    /** Litematica saves placements per world; never stack up copies from earlier runs. */
    protected static SchematicPlacementManager removeAllPlacements()
    {
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();

        for (SchematicPlacement old : new ArrayList<>(manager.getAllSchematicsPlacements()))
        {
            manager.removeSchematicPlacement(old, false);
        }

        return manager;
    }

    @Override
    public void refreshAll()
    {
        SchematicWorldRefresher.INSTANCE.updateAll();
    }

    @Override
    public void openSettingsTab()
    {
        TurboSettingsTab.selected = true;
        GuiBase.openGui(new GuiConfigs());
    }

    @Override
    public void setBuildThreads(int threads)
    {
        TurboSettingsTab.BUILD_THREADS.setIntegerValue(threads);
    }
}
