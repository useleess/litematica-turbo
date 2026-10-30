package com.litematicaturbo.shared.bench;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.shared.TurboWorldRenderer;

/**
 * The benchmark's Minecraft calls on the Mojang-named versions (see BenchBase); a version's TurboBench adds
 * the frame hook and the screenshot call.
 */
public abstract class BenchHost extends BenchBase
{
    private SchematicPlacement placement;

    protected BenchHost(File schematic)
    {
        super(schematic);
    }

    protected static Minecraft mc()
    {
        return Minecraft.getInstance();
    }

    @Override
    public void onClientTick(Minecraft mc)
    {
        this.run.tick();
    }

    @Override
    public boolean inWorld()
    {
        return mc().level != null && mc().player != null;
    }

    @Override
    public void setFlying()
    {
        mc().player.getAbilities().flying = true;
    }

    @Override
    public int[] placeSchematic(File file)
    {
        LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file.getAbsoluteFile().toPath());

        if (schematic == null)
        {
            return null;
        }

        SchematicPlacementManager manager = removeAllPlacements();
        BlockPos p = mc().player.blockPosition();
        BlockPos origin = new BlockPos(p.getX() + 4, mc().level.getMinY() + 4, p.getZ() + 4);
        this.placement = SchematicPlacement.createFor(schematic, origin, "bench", true, true);
        manager.addSchematicPlacement(this.placement, false);
        return new int[] {origin.getX(), origin.getY(), origin.getZ()};
    }

    @Override
    public void nudgePlacement()
    {
        this.placement.setOrigin(this.placement.getOrigin().offset(1, 0, 0), InfoUtils.INFO_MESSAGE_CONSUMER);
    }

    @Override
    public void movePlayer(double x, double y, double z, float yaw, float pitch)
    {
        LocalPlayer player = mc().player;
        player.snapTo(x, y, z, yaw, pitch);
        player.setDeltaMovement(0, 0, 0);
    }

    @Override
    public void startProfiler()
    {
        mc().debugClientMetricsStart(msg -> TurboLog.info("BENCH profiler: " + msg.getString()));
    }

    @Override
    public boolean idle()
    {
        return PlacementManagerDaemonHandler.INSTANCE.hasAnyTasks() == false &&
               ((TurboWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer()).turbo$isIdle();
    }

    @Override
    public File gameDirectory()
    {
        return mc().gameDirectory;
    }

    @Override
    public void stop()
    {
        mc().stop();
    }
}
