package com.litematicaturbo.mc1214.bench;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import org.joml.Matrix4f;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;

import com.litematicaturbo.core.BuildStats;
import com.litematicaturbo.core.TurboConfig;
import com.litematicaturbo.core.TurboLog;
import com.litematicaturbo.mc1214.TurboSettingsTab;
import com.litematicaturbo.mc1214.TurboWorldRenderer;

/**
 * Dev-only benchmark, started with -Dlitematicaturbo.bench=<schematic file> (and quick play into a
 * flat world). Places the schematic, then measures: the initial build, a steady view, a fly-through
 * and a full re-render. Results go to turbo-bench.txt in the game directory.
 */
public final class TurboBench implements IClientTickHandler, IRenderer
{
    private enum Phase { WAIT_WORLD, LOAD, STEADY, MOVE, REBUILD, NUDGE, NUDGE_SETTLE, SETTINGS, DONE }

    private static final int SETTLE_TICKS = 100;
    private static final int IDLE_TICKS_TO_FINISH = 20;
    private static final int TIMEOUT_TICKS = 20 * 300;

    private final File schematicFile;
    private final StringBuilder report = new StringBuilder();
    private Phase phase = Phase.WAIT_WORLD;
    private int phaseTicks;
    private int idleTicks;
    private BlockPos origin;
    private SchematicPlacement placement;

    private long lastFrameNanos;
    private long phaseStartNanos;
    private int frames;
    private long frameNanosSum;
    private long worstFrameNanos;
    private int framesOver50ms;
    private long buildsAtStart;
    private long buildNanosAtStart;
    private long mainNanosAtStart;

    private TurboBench(File schematicFile)
    {
        this.schematicFile = schematicFile;
    }

    public static void startIfRequested()
    {
        String path = System.getProperty("litematicaturbo.bench");

        if (path != null)
        {
            TurboBench bench = new TurboBench(new File(path));
            TickHandler.getInstance().registerClientTickHandler(bench);
            RenderEventHandler.getInstance().registerWorldLastRenderer(bench);
            TurboLog.info("Benchmark armed for " + path);
        }
    }

    @Override
    public void onRenderWorldLast(Matrix4f posMatrix, Matrix4f projMatrix)
    {
        long now = System.nanoTime();

        if (this.lastFrameNanos != 0 && this.phase != Phase.WAIT_WORLD && this.phase != Phase.DONE)
        {
            long frame = now - this.lastFrameNanos;
            ++this.frames;
            this.frameNanosSum += frame;
            this.worstFrameNanos = Math.max(this.worstFrameNanos, frame);

            if (frame > 50_000_000L)
            {
                ++this.framesOver50ms;
            }
        }

        this.lastFrameNanos = now;
    }

    @Override
    public void onClientTick(MinecraftClient mc)
    {
        if (mc.world == null || mc.player == null || this.phase == Phase.DONE)
        {
            return;
        }

        ++this.phaseTicks;
        ClientPlayerEntity player = mc.player;

        switch (this.phase)
        {
            case WAIT_WORLD:
                player.getAbilities().flying = true;

                if (this.phaseTicks >= SETTLE_TICKS)
                {
                    this.placeSchematic(mc);
                    this.startPhase(Phase.LOAD);
                }
                break;

            case LOAD:
                this.holdCamera(player);

                if (this.finishedWhenIdle(mc, "initial build"))
                {
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory, "turbo-bench-" + this.label() + ".png", mc.getFramebuffer(), msg -> {});
                    this.startPhase(Phase.STEADY);
                }
                break;

            case STEADY:
                this.holdCamera(player);

                if (this.phaseTicks == 20 && Boolean.getBoolean("litematicaturbo.benchProfile"))
                {
                    mc.toggleDebugProfiler(msg -> TurboLog.info("BENCH profiler: " + msg.getString()));
                }

                if (this.phaseTicks >= 200)
                {
                    this.endPhase("steady view 10s");
                    this.startPhase(Phase.MOVE);
                }
                break;

            case MOVE:
                // Fly diagonally across and past the schematic in 10 seconds
                double t = Math.min(1.0, this.phaseTicks / 200.0);
                player.refreshPositionAndAngles(this.origin.getX() - 20 + t * 360, this.origin.getY() + 90,
                                                this.origin.getZ() - 20 + t * 270, -45f, 25f);
                player.setVelocity(0, 0, 0);

                if (this.phaseTicks >= 200 && this.finishedWhenIdle(mc, "fly-through 10s + catch-up"))
                {
                    this.startPhase(Phase.REBUILD);
                    this.holdCamera(player);
                    SchematicWorldRefresher.INSTANCE.updateAll();
                }
                break;

            case REBUILD:
                this.holdCamera(player);

                if (this.phaseTicks > 2 && this.finishedWhenIdle(mc, "full re-render"))
                {
                    this.startPhase(Phase.NUDGE);
                }
                break;

            case NUDGE:
                // Like scrolling the placement: one block every 3 ticks for 6 seconds
                this.holdCamera(player);

                if (this.phaseTicks % 3 == 0)
                {
                    this.placement.setOrigin(this.placement.getOrigin().add(1, 0, 0), InfoUtils.INFO_MESSAGE_CONSUMER);
                }

                if (this.phaseTicks == 119)
                {
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory, "turbo-bench-scrolling-" + this.label() + ".png", mc.getFramebuffer(), msg -> {});
                }

                if (this.phaseTicks >= 120)
                {
                    this.endPhase("scrolling placement 6s");
                    this.startPhase(Phase.NUDGE_SETTLE);
                }
                break;

            case NUDGE_SETTLE:
                this.holdCamera(player);

                if (this.phaseTicks > 2 && this.finishedWhenIdle(mc, "after scrolling, until done"))
                {
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory, "turbo-bench-moved-" + this.label() + ".png", mc.getFramebuffer(), msg -> {});
                    this.writeReport(mc);

                    if (TurboConfig.get().enabled)
                    {
                        this.startPhase(Phase.SETTINGS);
                    }
                    else
                    {
                        // Stock run: the settings check would save enabled=false to the file
                        this.phase = Phase.DONE;
                        mc.scheduleStop();
                    }
                }
                break;

            case SETTINGS:
                // Turbo tab in Litematica's config menu, and a live thread-count change
                if (this.phaseTicks == 1)
                {
                    TurboSettingsTab.selected = true;
                    GuiBase.openGui(new GuiConfigs());
                }
                else if (this.phaseTicks == 20)
                {
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory, "turbo-bench-settings.png", mc.getFramebuffer(), msg -> {});
                    TurboSettingsTab.BUILD_THREADS.setIntegerValue(2);
                }
                else if (this.phaseTicks == 60)
                {
                    TurboLog.info("BENCH settings check: config buildThreads=" + TurboConfig.get().buildThreads);
                    TurboSettingsTab.BUILD_THREADS.setIntegerValue(0); // back to automatic for the next run
                    this.phase = Phase.DONE;
                    mc.scheduleStop();
                }
                break;

            default:
        }
    }

    private void placeSchematic(MinecraftClient mc)
    {
        LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(this.schematicFile);

        if (schematic == null)
        {
            TurboLog.warn("Benchmark: could not load " + this.schematicFile);
            this.phase = Phase.DONE;
            mc.scheduleStop();
            return;
        }

        // Litematica saves placements per world; never stack up copies from earlier runs
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();

        for (SchematicPlacement old : new ArrayList<>(manager.getAllSchematicsPlacements()))
        {
            manager.removeSchematicPlacement(old, false);
        }

        BlockPos p = mc.player.getBlockPos();
        this.origin = new BlockPos(p.getX() + 4, mc.world.getBottomY() + 4, p.getZ() + 4);
        this.placement = SchematicPlacement.createFor(schematic, this.origin, "bench", true, true);
        manager.addSchematicPlacement(this.placement, false);
        this.report.append(String.format("== %s | threads=%d skipEmptySections=%s asyncNearRebuilds=%s | %s%n",
                                         this.label(),
                                         TurboConfig.get().resolveBuildThreads(TurboConfig.CPU_COUNT),
                                         TurboConfig.get().skipEmptySectionsEnabled(),
                                         TurboConfig.get().asyncNearRebuildsEnabled(),
                                         this.schematicFile.getName()));
        this.holdCamera(mc.player);
    }

    private void holdCamera(ClientPlayerEntity player)
    {
        if (this.origin != null)
        {
            player.refreshPositionAndAngles(this.origin.getX() - 20, this.origin.getY() + 90, this.origin.getZ() - 20, -45f, 25f);
            player.setVelocity(0, 0, 0);
        }
    }

    private boolean finishedWhenIdle(MinecraftClient mc, String name)
    {
        boolean idle = DataManager.getSchematicPlacementManager().hasQueuedChunks() == false &&
                       ((TurboWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer()).turbo$isIdle();
        this.idleTicks = idle ? this.idleTicks + 1 : 0;

        if (this.idleTicks >= IDLE_TICKS_TO_FINISH || this.phaseTicks >= TIMEOUT_TICKS)
        {
            this.endPhase(this.phaseTicks >= TIMEOUT_TICKS ? name + " (TIMED OUT)" : name);
            return true;
        }

        return false;
    }

    private void startPhase(Phase next)
    {
        this.phase = next;
        this.phaseTicks = 0;
        this.idleTicks = 0;
        this.phaseStartNanos = System.nanoTime();
        this.frames = 0;
        this.frameNanosSum = 0;
        this.worstFrameNanos = 0;
        this.framesOver50ms = 0;
        this.buildsAtStart = BuildStats.totalBuilds();
        this.buildNanosAtStart = BuildStats.totalBuildNanos();
        this.mainNanosAtStart = BuildStats.totalMainThreadNanos();
    }

    private void endPhase(String name)
    {
        // The idle wait itself is not part of the measured time
        double seconds = (System.nanoTime() - this.phaseStartNanos) / 1.0e9 - (this.idleTicks / 20.0);
        long builds = BuildStats.totalBuilds() - this.buildsAtStart;
        double buildMs = (BuildStats.totalBuildNanos() - this.buildNanosAtStart) / 1.0e6;
        double mainMs = (BuildStats.totalMainThreadNanos() - this.mainNanosAtStart) / 1.0e6;
        double avgFps = this.frameNanosSum > 0 ? this.frames / (this.frameNanosSum / 1.0e9) : 0;

        String line = String.format("%-28s time %6.2fs | avg %5.0f fps | worst frame %6.1f ms | frames >50ms %4d | builds %5d (avg %6.2f ms) | render-thread build time %8.0f ms%n",
                                    name, seconds, avgFps, this.worstFrameNanos / 1.0e6, this.framesOver50ms,
                                    builds, builds > 0 ? buildMs / builds : 0.0, mainMs);
        this.report.append(line);
        this.report.append("    placement calls " + BuildStats.placeCalls.get() + ", fast " + BuildStats.placeCallsFast.get() + ", re-copies skipped " + BuildStats.placeCallsSkipped.get() + System.lineSeparator());
        TurboLog.info("BENCH " + line.trim());
    }

    private String label()
    {
        return TurboConfig.get().enabled ? "turbo" : "stock";
    }

    private void writeReport(MinecraftClient mc)
    {
        File out = new File(mc.runDirectory, "turbo-bench.txt");

        try
        {
            Files.write(out.toPath(), this.report.toString().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException e)
        {
            TurboLog.warn("Benchmark: could not write " + out + ": " + e);
        }
    }
}
