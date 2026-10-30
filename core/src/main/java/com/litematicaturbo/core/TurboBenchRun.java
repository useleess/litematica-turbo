package com.litematicaturbo.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Dev-only benchmark, started with -Dlitematicaturbo.bench=<schematic file> (and quick play into a
 * flat world). Places the schematic, then measures: the initial build, a steady view, a fly-through
 * and a full re-render. Results go to turbo-bench.txt in the game directory.
 * The game side (each version's bench/TurboBench) calls {@link #tick()} every client tick and
 * {@link #frame()} every rendered frame, and does the game calls of {@link Host}.
 */
public final class TurboBenchRun
{
    /** What the benchmark needs from the game. */
    public interface Host
    {
        /** True while a world and a player are loaded. */
        boolean inWorld();

        /** Before 1.20 (no quick play): opens the bench world when on the title screen; true when it did. */
        default boolean openBenchWorld()
        {
            return false;
        }

        void setFlying();

        /**
         * Removes every placement (Litematica saves them per world; never stack up copies from earlier runs),
         * then places the schematic 4 blocks off the player, 4 above the bottom of the world.
         * @return the placement origin {x, y, z}, or null when the schematic could not be loaded
         */
        int[] placeSchematic(File schematic);

        /** Moves the placement one block along x. */
        void nudgePlacement();

        void movePlayer(double x, double y, double z, float yaw, float pitch);

        void screenshot(String fileName);

        /** Starts Minecraft's own profiler (1.21+; does nothing before). */
        default void startProfiler()
        {
        }

        /** Gallery screenshots (-Dlitematicaturbo.shots=true) are only taken on versions that can. */
        default boolean takesGalleryShots()
        {
            return false;
        }

        default boolean debugHudShown()
        {
            return false;
        }

        default void toggleDebugHud()
        {
        }

        /** Litematica re-renders every schematic chunk. */
        void refreshAll();

        /** True when no schematic chunk is waiting to be placed, built or uploaded. */
        boolean idle();

        /** Opens Litematica's config menu on the Turbo tab. */
        void openSettingsTab();

        /** Sets the "build threads" option through the settings tab, like a player would. */
        void setBuildThreads(int threads);

        File gameDirectory();

        void stop();
    }

    private enum Phase { WAIT_WORLD, LOAD, STEADY, MOVE, REBUILD, NUDGE, NUDGE_SETTLE, SETTINGS, SHOTS, DONE }

    private static final int SETTLE_TICKS = 100;
    private static final int IDLE_TICKS_TO_FINISH = 20;
    private static final int TIMEOUT_TICKS = 20 * 300;

    private final Host host;
    private final File schematicFile;
    private final boolean shotsMode;
    private final StringBuilder report = new StringBuilder();
    private Phase phase = Phase.WAIT_WORLD;
    private boolean worldRequested;
    private int phaseTicks;
    private int idleTicks;
    private int[] origin;

    private long lastFrameNanos;
    private long phaseStartNanos;
    private int frames;
    private long frameNanosSum;
    private long worstFrameNanos;
    private int framesOver50ms;
    private long buildsAtStart;
    private long buildNanosAtStart;
    private long mainNanosAtStart;

    public TurboBenchRun(Host host, File schematicFile)
    {
        this.host = host;
        this.schematicFile = schematicFile;
        this.shotsMode = host.takesGalleryShots() && Boolean.getBoolean("litematicaturbo.shots");
    }

    /** The schematic to benchmark (-Dlitematicaturbo.bench), or null when no benchmark was asked for. */
    public static File requestedSchematic()
    {
        String path = System.getProperty("litematicaturbo.bench");

        if (path != null)
        {
            TurboLog.info("Benchmark armed for " + path);
            return new File(path);
        }

        return null;
    }

    public void frame()
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

    public void tick()
    {
        if (this.phase == Phase.WAIT_WORLD && this.worldRequested == false && this.host.openBenchWorld())
        {
            this.worldRequested = true;
            return;
        }

        if (this.host.inWorld() == false || this.phase == Phase.DONE)
        {
            return;
        }

        ++this.phaseTicks;

        switch (this.phase)
        {
            case WAIT_WORLD:
                this.host.setFlying();

                if (this.phaseTicks >= SETTLE_TICKS)
                {
                    this.placeSchematic();
                    this.startPhase(Phase.LOAD);
                }
                break;

            case LOAD:
                this.holdCamera();

                // -Dlitematicaturbo.shots=true: gallery screenshots only (F3 stats while building, settings tab)
                if (this.shotsMode && this.phaseTicks == 1)
                {
                    this.host.toggleDebugHud();
                }
                else if (this.shotsMode && this.phaseTicks == 6)
                {
                    this.host.screenshot("turbo-shot-f3.png");
                }

                if (this.finishedWhenIdle("initial build"))
                {
                    this.host.screenshot("turbo-bench-" + this.label() + ".png");
                    this.startPhase(this.shotsMode ? Phase.SHOTS : Phase.STEADY);
                }
                break;

            case STEADY:
                this.holdCamera();

                if (this.phaseTicks == 20 && Boolean.getBoolean("litematicaturbo.benchProfile"))
                {
                    this.host.startProfiler();
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
                this.host.movePlayer(this.origin[0] - 20 + t * 360, this.origin[1] + 90, this.origin[2] - 20 + t * 270, -45f, 25f);

                if (this.phaseTicks >= 200 && this.finishedWhenIdle("fly-through 10s + catch-up"))
                {
                    this.startPhase(Phase.REBUILD);
                    this.holdCamera();
                    this.host.refreshAll();
                }
                break;

            case REBUILD:
                this.holdCamera();

                if (this.phaseTicks > 2 && this.finishedWhenIdle("full re-render"))
                {
                    this.startPhase(Phase.NUDGE);
                }
                break;

            case NUDGE:
                // Like scrolling the placement: one block every 3 ticks for 6 seconds
                this.holdCamera();

                if (this.phaseTicks % 3 == 0)
                {
                    this.host.nudgePlacement();
                }

                if (this.phaseTicks == 119)
                {
                    this.host.screenshot("turbo-bench-scrolling-" + this.label() + ".png");
                }

                if (this.phaseTicks >= 120)
                {
                    this.endPhase("scrolling placement 6s");
                    this.startPhase(Phase.NUDGE_SETTLE);
                }
                break;

            case NUDGE_SETTLE:
                this.holdCamera();

                if (this.phaseTicks > 2 && this.finishedWhenIdle("after scrolling, until done"))
                {
                    this.host.screenshot("turbo-bench-moved-" + this.label() + ".png");
                    this.writeReport();

                    if (TurboConfig.get().enabled)
                    {
                        this.startPhase(Phase.SETTINGS);
                    }
                    else
                    {
                        // Stock run: the settings check would save enabled=false to the file
                        this.finish();
                    }
                }
                break;

            case SETTINGS:
                // Turbo tab in Litematica's config menu, and a live thread-count change
                if (this.phaseTicks == 1)
                {
                    this.host.openSettingsTab();
                }
                else if (this.phaseTicks == 20)
                {
                    this.host.screenshot("turbo-bench-settings.png");
                    this.host.setBuildThreads(2);
                }
                else if (this.phaseTicks == 60)
                {
                    TurboLog.info("BENCH settings check: config buildThreads=" + TurboConfig.get().buildThreads);
                    this.host.setBuildThreads(0); // back to automatic for the next run
                    this.finish();
                }
                break;

            case SHOTS:
                this.holdCamera();

                if (this.phaseTicks == 1)
                {
                    if (this.host.debugHudShown())
                    {
                        this.host.toggleDebugHud();
                    }

                    this.host.openSettingsTab();
                }
                else if (this.phaseTicks == 40)
                {
                    this.host.screenshot("turbo-shot-settings.png");
                }
                else if (this.phaseTicks == 45)
                {
                    this.finish();
                }
                break;

            default:
        }
    }

    private void finish()
    {
        this.phase = Phase.DONE;
        this.host.stop();
    }

    private void placeSchematic()
    {
        this.origin = this.host.placeSchematic(this.schematicFile);

        if (this.origin == null)
        {
            TurboLog.warn("Benchmark: could not load " + this.schematicFile);
            this.finish();
            return;
        }

        this.report.append(String.format("== %s | threads=%d skipEmptySections=%s asyncNearRebuilds=%s | %s%n",
                                         this.label(),
                                         TurboConfig.get().resolveBuildThreads(TurboConfig.CPU_COUNT),
                                         TurboConfig.get().skipEmptySectionsEnabled(),
                                         TurboConfig.get().asyncNearRebuildsEnabled(),
                                         this.schematicFile.getName()));
        this.holdCamera();
    }

    private void holdCamera()
    {
        if (this.origin != null)
        {
            this.host.movePlayer(this.origin[0] - 20, this.origin[1] + 90, this.origin[2] - 20, -45f, 25f);
        }
    }

    private boolean finishedWhenIdle(String name)
    {
        this.idleTicks = this.host.idle() ? this.idleTicks + 1 : 0;

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

    private void writeReport()
    {
        File out = new File(this.host.gameDirectory(), "turbo-bench.txt");

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
