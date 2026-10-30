package com.litematicaturbo.shared.bench;

import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

import com.litematicaturbo.shared.PlacementQueue;
import com.litematicaturbo.shared.TurboWorldRenderer;

/**
 * The benchmark's Minecraft calls on the Yarn-named versions (see BenchBase); the few that were renamed between
 * them are in BenchCalls, and each version's TurboBench adds its frame hook.
 */
public abstract class BenchHost extends BenchBase
{
    private SchematicPlacement placement;

    protected BenchHost(File schematic)
    {
        super(schematic);
    }

    protected static MinecraftClient mc()
    {
        return MinecraftClient.getInstance();
    }

    @Override
    public void onClientTick(MinecraftClient mc)
    {
        this.run.tick();
    }

    @Override
    public boolean inWorld()
    {
        return mc().world != null && mc().player != null;
    }

    @Override
    public void setFlying()
    {
        BenchCalls.setFlying(mc().player);
    }

    @Override
    public int[] placeSchematic(File file)
    {
        LitematicaSchematic schematic = BenchCalls.load(file);

        if (schematic == null)
        {
            return null;
        }

        SchematicPlacementManager manager = removeAllPlacements();
        BlockPos p = mc().player.getBlockPos();
        BlockPos origin = new BlockPos(p.getX() + 4, BenchCalls.bottomY(mc().world) + 4, p.getZ() + 4);
        this.placement = SchematicPlacement.createFor(schematic, origin, "bench", true, true);
        manager.addSchematicPlacement(this.placement, false);
        return new int[] {origin.getX(), origin.getY(), origin.getZ()};
    }

    @Override
    public void nudgePlacement()
    {
        this.placement.setOrigin(this.placement.getOrigin().add(1, 0, 0), InfoUtils.INFO_MESSAGE_CONSUMER);
    }

    @Override
    public void movePlayer(double x, double y, double z, float yaw, float pitch)
    {
        ClientPlayerEntity player = mc().player;
        player.refreshPositionAndAngles(x, y, z, yaw, pitch);
        player.setVelocity(0, 0, 0);
    }

    @Override
    public void screenshot(String fileName)
    {
        BenchCalls.screenshot(mc(), fileName);
    }

    @Override
    public boolean idle()
    {
        return PlacementQueue.hasQueuedChunks(DataManager.getSchematicPlacementManager()) == false &&
               ((TurboWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer()).turbo$isIdle();
    }

    @Override
    public File gameDirectory()
    {
        return mc().runDirectory;
    }

    @Override
    public void stop()
    {
        mc().scheduleStop();
    }
}
