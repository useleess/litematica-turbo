package com.litematicaturbo.shared;

import java.util.List;
import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;

import com.litematicaturbo.core.TurboConfig;

/**
 * The "Turbo" tab in Litematica's config menu. The malilib options here are only a view of
 * {@link TurboConfig}: every change is applied immediately and saved to the properties file.
 */
public final class TurboSettingsTab
{
    public static final ConfigBoolean ENABLED = TurboOptions.bool("turboEnabled", true,
            "Master switch. Off = Litematica behaves exactly like without Turbo.", "Turbo enabled");
    public static final ConfigInteger BUILD_THREADS = TurboOptions.integer("turboBuildThreads", 0, -1, 16,
            "Threads that build schematic chunks in the background.\n0 = automatic (from your CPU), -1 = none (stock: render thread only).");
    public static final ConfigBoolean SKIP_EMPTY_SECTIONS = TurboOptions.bool("turboSkipEmptySections", true,
            "Skip 16-block-high slices of a chunk that hold nothing to draw.", "Skip empty sections");
    public static final ConfigBoolean ASYNC_NEAR_REBUILDS = TurboOptions.bool("turboAsyncNearRebuilds", true,
            "Rebuild chunks near you in the background too, instead of on the render thread.\nRemoves the stutter when standing in a schematic.", "Background near rebuilds");
    public static final ConfigBoolean GHOST_MOVE = TurboOptions.bool("turboGhostMove", true,
            "While moving a placement, show the already built schematic shifted\nand only rebuild once it stops moving. Smooth scrolling.\n(Only when a single placement is enabled.)", "Smooth placement moving");
    public static final ConfigInteger GHOST_SETTLE_MS = TurboOptions.integer("turboGhostSettleMs", 250, 0, 2000,
            "How long (ms) a moved placement must stay still before the real rebuild starts.");
    public static final ConfigBoolean CATCH_UP_AUTO = TurboOptions.bool("turboCatchUpAuto", true,
            "Size the rebuild work after a move from your current frame time,\nso it never causes a dropped frame. Turn off to use the fixed budget below.", "Automatic catch-up");
    public static final ConfigInteger CATCH_UP_BUDGET_MS = TurboOptions.integer("turboCatchUpBudgetMs", 8, 1, 50,
            "Used when Automatic catch-up is off: render-thread milliseconds per tick for\nre-copying a moved placement. Higher = finishes sooner but stutters.\nUp to ~16 stays smooth; 50 is stock Litematica.");
    public static final ConfigBoolean SHOW_STATS = TurboOptions.bool("turboShowStats", true,
            "Add Turbo's build statistics to the [Litematica] line in the F3 screen.", "Show F3 stats");
    public static final ConfigBoolean COMPACT_STORAGE = TurboOptions.bool("turboCompactStorage", true,
            "Keep huge, mostly empty schematics in little memory (only the parts that hold blocks use memory),\nand show a message instead of running out of memory when a file is too big.\nApplies to schematics loaded from now on.", "Compact storage for huge schematics");
    public static final ConfigBoolean BATCH_DRAW = TurboOptions.bool("turboBatchDraw", true,
            "Draw the built pieces of the schematic in groups (4 x 4 x 4 pieces), one call per group instead of one per piece.\nThe picture stays the same, the game has less to do. Works on Minecraft 1.16.5 - 1.21.4.", "Batch drawing");

    public static final List<IConfigBase> OPTIONS = ImmutableList.of(
            ENABLED, BUILD_THREADS, SKIP_EMPTY_SECTIONS, ASYNC_NEAR_REBUILDS,
            GHOST_MOVE, GHOST_SETTLE_MS, CATCH_UP_AUTO, CATCH_UP_BUDGET_MS, SHOW_STATS, COMPACT_STORAGE, BATCH_DRAW);

    /** Whether the Turbo tab is the one shown in Litematica's config menu. */
    public static boolean selected;

    private static boolean syncing;
    private static boolean reloadRenderers;
    private static boolean refreshRenders;
    private static boolean warnedIncompatible;

    private TurboSettingsTab()
    {
    }

    public static void init()
    {
        for (IConfigBase option : OPTIONS)
        {
            @SuppressWarnings("unchecked")
            ConfigBase<IConfigBase> config = (ConfigBase<IConfigBase>) option;
            config.setValueChangeCallback(c -> onChanged());
        }

        syncFromConfig();
    }

    /** Shows the current values (also picks up edits made to the file). */
    public static void syncFromConfig()
    {
        TurboConfig c = TurboConfig.get();
        syncing = true;

        // done here: the language is loaded by the time the tab is opened
        TurboOptions.translateNames(OPTIONS);

        try
        {
            ENABLED.setBooleanValue(c.enabled);
            BUILD_THREADS.setIntegerValue(c.buildThreads);
            SKIP_EMPTY_SECTIONS.setBooleanValue(c.skipEmptySections);
            ASYNC_NEAR_REBUILDS.setBooleanValue(c.asyncNearRebuilds);
            GHOST_MOVE.setBooleanValue(c.ghostMove);
            GHOST_SETTLE_MS.setIntegerValue(c.ghostSettleMs);
            CATCH_UP_AUTO.setBooleanValue(c.catchUpAuto);
            CATCH_UP_BUDGET_MS.setIntegerValue(c.catchUpBudgetMs);
            SHOW_STATS.setBooleanValue(c.showStats);
            COMPACT_STORAGE.setBooleanValue(c.compactStorage);
            BATCH_DRAW.setBooleanValue(c.batchDraw);
        }
        finally
        {
            syncing = false;
        }
    }

    private static void onChanged()
    {
        if (syncing)
        {
            return;
        }

        TurboConfig c = TurboConfig.get();
        boolean threadsChanged = c.enabled != ENABLED.getBooleanValue() || c.buildThreads != BUILD_THREADS.getIntegerValue();
        boolean buildChanged = c.skipEmptySections != SKIP_EMPTY_SECTIONS.getBooleanValue();

        c.setEnabled(ENABLED.getBooleanValue());
        c.buildThreads = BUILD_THREADS.getIntegerValue();
        c.skipEmptySections = SKIP_EMPTY_SECTIONS.getBooleanValue();
        c.asyncNearRebuilds = ASYNC_NEAR_REBUILDS.getBooleanValue();
        c.ghostMove = GHOST_MOVE.getBooleanValue();
        c.ghostSettleMs = GHOST_SETTLE_MS.getIntegerValue();
        c.catchUpAuto = CATCH_UP_AUTO.getBooleanValue();
        c.catchUpBudgetMs = CATCH_UP_BUDGET_MS.getIntegerValue();
        c.showStats = SHOW_STATS.getBooleanValue();
        c.compactStorage = COMPACT_STORAGE.getBooleanValue();
        c.batchDraw = BATCH_DRAW.getBooleanValue();
        c.save();

        if (c.ghostMoveEnabled() == false)
        {
            GhostMove.end();
        }

        reloadRenderers |= threadsChanged;
        refreshRenders |= buildChanged;
    }

    /** The chat warning shown once when a patch did not fit and Turbo switched itself off; null when none is due. */
    static String takeIncompatibilityWarning(boolean playerPresent)
    {
        if (warnedIncompatible == false && TurboConfig.incompatibility() != null && playerPresent)
        {
            warnedIncompatible = true;
            return "[Litematica Turbo] Off: " + TurboConfig.incompatibility() + ". Litematica works as normal. Check for a Turbo update.";
        }

        return null;
    }

    /**
     * Applied on the next client tick (SettingsTicker), outside rendering: a new thread count needs a new build
     * dispatcher, which Litematica creates when its renderer is given the world again.
     */
    static void applyPendingChanges()
    {
        if (reloadRenderers == false && refreshRenders == false)
        {
            return;
        }

        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();

        if (world != null)
        {
            if (reloadRenderers)
            {
                // Litematica keeps one world renderer
                LitematicaRenderer.getInstance().getWorldRenderer().setWorldAndLoadRenderers(null);
                LitematicaRenderer.getInstance().getWorldRenderer().setWorldAndLoadRenderers(world);
            }

            SchematicWorldRefresher.INSTANCE.updateAll();
        }

        reloadRenderers = false;
        refreshRenders = false;
    }
}
