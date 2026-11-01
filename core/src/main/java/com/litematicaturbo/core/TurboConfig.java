package com.litematicaturbo.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings shared by every Minecraft version. Stored in config/litematica_turbo.properties
 * and read once when the game starts.
 */
public final class TurboConfig
{
    public static final String FILE_NAME = "litematica_turbo.properties";

    private static volatile TurboConfig instance = new TurboConfig();

    /** Master switch: false makes every patch behave like stock Litematica. */
    public boolean enabled = true;
    /** Schematic chunk build threads; 0 = pick from the CPU count. */
    public int buildThreads = 0;
    /** Skip 16-block-high slices of a chunk column that hold nothing to draw. */
    public boolean skipEmptySections = true;
    /** Never rebuild schematic chunks on the render thread when worker threads exist. */
    public boolean asyncNearRebuilds = true;
    /** Append build statistics to Litematica's F3 line. */
    public boolean showStats = true;
    /** While a placement is being moved, draw the already built schematic shifted instead of rebuilding it. */
    public boolean ghostMove = true;
    /** How long a placement must stay still before the real rebuild starts. */
    public int ghostSettleMs = 250;
    /** Size the catch-up work from the current frame time instead of catchUpBudgetMs. */
    public boolean catchUpAuto = true;
    /** Render-thread milliseconds per tick for copying the moved placement into the schematic world. */
    public int catchUpBudgetMs = 8;
    /** Keep the blocks of huge, mostly empty schematic regions in sparse sections, and refuse files that cannot fit in memory. */
    public boolean compactStorage = true;
    /** Draw the built opaque pieces of a 4 x 4 x 4 region with one call instead of one call each. */
    public boolean batchDraw = true;
    /** Leave out the outline lines of schematic pieces far away (see FarDetail). */
    public boolean farDetail = true;

    private static Path configDir;

    /** Why Turbo switched itself off for this session (a patch did not fit this Litematica), or null. */
    private static volatile String incompatible;
    /** The file's "enabled" value while switched off for incompatibility (kept as the user set it). */
    private boolean enabledInFile = true;

    /** Fail-safe: run as stock Litematica for this session, without changing the saved setting. */
    public static void markIncompatible(String reason)
    {
        TurboConfig config = instance;
        incompatible = reason;
        config.enabledInFile = config.enabled;
        config.enabled = false;
    }

    public static String incompatibility()
    {
        return incompatible;
    }

    /** The master switch as set by the user (stays off for the session after {@link #markIncompatible}). */
    public void setEnabled(boolean value)
    {
        if (incompatible != null)
        {
            this.enabledInFile = value;
            this.enabled = false;
        }
        else
        {
            this.enabled = value;
        }
    }

    public static TurboConfig get()
    {
        return instance;
    }

    public static void set(TurboConfig config)
    {
        instance = config;
    }

    /**
     * Read once: availableProcessors() is a system call (slow on Windows), and threadingEnabled()
     * runs several hundred times per frame.
     */
    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    public boolean threadingEnabled()
    {
        return this.enabled && this.resolveBuildThreads(CPU_COUNT) > 0;
    }

    public boolean skipEmptySectionsEnabled()
    {
        return this.enabled && this.skipEmptySections;
    }

    public boolean asyncNearRebuildsEnabled()
    {
        return this.threadingEnabled() && this.asyncNearRebuilds;
    }

    public boolean compactStorageEnabled()
    {
        return this.enabled && this.compactStorage;
    }

    public boolean batchDrawEnabled()
    {
        return this.enabled && this.batchDraw;
    }

    public boolean farDetailEnabled()
    {
        return this.enabled && this.farDetail;
    }

    public boolean ghostMoveEnabled()
    {
        return this.enabled && this.ghostMove;
    }

    public int resolveBuildThreads(int cpuCount)
    {
        if (this.enabled == false || this.buildThreads < 0)
        {
            return 0;
        }

        if (this.buildThreads > 0)
        {
            return Math.min(this.buildThreads, 16);
        }

        return Math.max(1, Math.min(cpuCount / 3, 6));
    }

    public static TurboConfig load(Path configDir)
    {
        Path file = configDir.resolve(FILE_NAME);
        TurboConfig config = new TurboConfig();

        if (Files.isRegularFile(file))
        {
            Properties props = new Properties();

            try (InputStream in = Files.newInputStream(file))
            {
                props.load(in);
                config.read(props);
            }
            catch (IOException | RuntimeException e)
            {
                TurboLog.warn("Could not read " + file + ", using defaults: " + e);
            }
        }

        // Always rewrite so new options show up in old files
        config.save(file);
        TurboConfig.configDir = configDir;

        // Dev A/B benchmark: run as stock Litematica without touching the file
        if (Boolean.getBoolean("litematicaturbo.stock"))
        {
            config.enabled = false;
        }

        return config;
    }

    void read(Properties props)
    {
        this.enabled = bool(props, "enabled", this.enabled);
        this.buildThreads = integer(props, "buildThreads", this.buildThreads);
        this.skipEmptySections = bool(props, "skipEmptySections", this.skipEmptySections);
        this.asyncNearRebuilds = bool(props, "asyncNearRebuilds", this.asyncNearRebuilds);
        this.showStats = bool(props, "showStats", this.showStats);
        this.ghostMove = bool(props, "ghostMove", this.ghostMove);
        this.ghostSettleMs = Math.max(0, integer(props, "ghostSettleMs", this.ghostSettleMs));
        this.catchUpAuto = bool(props, "catchUpAuto", this.catchUpAuto);
        this.catchUpBudgetMs = Math.max(1, integer(props, "catchUpBudgetMs", this.catchUpBudgetMs));
        this.compactStorage = bool(props, "compactStorage", this.compactStorage);
        this.batchDraw = bool(props, "batchDraw", this.batchDraw);
        this.farDetail = bool(props, "farDetail", this.farDetail);
    }

    /** Writes the settings back to the file they were loaded from. */
    public void save()
    {
        if (configDir != null)
        {
            this.save(configDir.resolve(FILE_NAME));
        }
    }

    void save(Path file)
    {
        String text = "# Litematica Turbo settings - also editable in game: Litematica config menu > Turbo tab\n"
                    + "# enabled: false = behave exactly like stock Litematica\n"
                    + "enabled=" + (incompatible != null ? this.enabledInFile : this.enabled) + "\n"
                    + "# buildThreads: schematic chunk build threads. 0 = automatic, -1 = off (render thread only)\n"
                    + "buildThreads=" + this.buildThreads + "\n"
                    + "# skipEmptySections: skip 16-high slices with nothing to draw\n"
                    + "skipEmptySections=" + this.skipEmptySections + "\n"
                    + "# asyncNearRebuilds: rebuild nearby chunks on worker threads too (removes stutter)\n"
                    + "asyncNearRebuilds=" + this.asyncNearRebuilds + "\n"
                    + "# showStats: add build statistics to the [Litematica] line in F3\n"
                    + "showStats=" + this.showStats + "\n"
                    + "# ghostMove: while moving a placement, show the built schematic shifted and rebuild once it stops\n"
                    + "ghostMove=" + this.ghostMove + "\n"
                    + "# ghostSettleMs: how long the placement must stay still before the real rebuild starts\n"
                    + "ghostSettleMs=" + this.ghostSettleMs + "\n"
                    + "# catchUpAuto: size the re-copy work after a move from the current frame time (smooth at any FPS)\n"
                    + "catchUpAuto=" + this.catchUpAuto + "\n"
                    + "# catchUpBudgetMs: used when catchUpAuto=false - render-thread ms per tick for the re-copy (higher = faster, more stutter)\n"
                    + "catchUpBudgetMs=" + this.catchUpBudgetMs + "\n"
                    + "# compactStorage: keep huge, mostly empty schematics in little memory (and refuse files that cannot fit)\n"
                    + "compactStorage=" + this.compactStorage + "\n"
                    + "# batchDraw: draw the built pieces of a 4 x 4 x 4 region with one call (same picture, fewer calls)\n"
                    + "batchDraw=" + this.batchDraw + "\n"
                    + "# farDetail: leave out the outline lines of schematic pieces far away (only a blur there); off by itself while memory is low\n"
                    + "farDetail=" + this.farDetail + "\n";

        try
        {
            Files.createDirectories(file.getParent());

            try (OutputStream out = Files.newOutputStream(file))
            {
                out.write(text.getBytes("UTF-8"));
            }
        }
        catch (IOException e)
        {
            TurboLog.warn("Could not write " + file + ": " + e);
        }
    }

    private static boolean bool(Properties props, String key, boolean fallback)
    {
        String value = props.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int integer(Properties props, String key, int fallback)
    {
        String value = props.getProperty(key);

        if (value == null)
        {
            return fallback;
        }

        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }
}
