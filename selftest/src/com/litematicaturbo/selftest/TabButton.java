package com.litematicaturbo.selftest;

import java.util.ArrayList;
import java.util.List;

/**
 * settings/tab/button: Litematica's real config screen, opened in the game. It gets a "Turbo" tab button after
 * Litematica's own tabs; clicking it shows Turbo's options (and greys the button out), clicking one of
 * Litematica's tabs goes back to Litematica's own options.
 */
final class TabButton
{
    private TabButton()
    {
    }

    static void run(Ctx ctx, SettingsTests.Live live) throws Exception
    {
        Class<?> dataManager = ctx.cls("fi.dy.masa.litematica.data.DataManager");
        Object previousTab = Ctx.call(dataManager, "getConfigGuiTab");
        Object previousScreen = ctx.onMain(ctx::screen);
        Object generic = ctx.cls("fi.dy.masa.litematica.gui.GuiConfigs$ConfigGuiTab").getField("GENERIC").get(null);

        try
        {
            Object gui = ctx.onMain(() -> {
                Ctx.call(dataManager, "setConfigGuiTab", generic);
                Ctx.setStatic(live.tab, "selected", false);
                Object g = ctx.cls("fi.dy.masa.litematica.gui.GuiConfigs").getDeclaredConstructor().newInstance();
                ctx.setScreen(g);
                return g;
            });
            ctx.ticks(2);
            String label = (String) Ctx.call(ctx.cls("fi.dy.masa.malilib.util.StringUtils"), "translate", "litematica_turbo.gui.button.config_gui.turbo", new Object[0]);
            SelfTest.equal("Turbo", label, "the Turbo tab's label (lang file)");
            String genericLabel = (String) Ctx.call(generic, "getDisplayName");

            ctx.onMain(() -> {
                SelfTest.check(ctx.screen() == gui, "Litematica's config screen did not open: " + ctx.screen());
                List<Object> turbo = buttons(gui, label);
                SelfTest.equal(1, turbo.size(), "Turbo tab buttons on Litematica's config screen " + labels(gui));
                SelfTest.check(buttons(gui, genericLabel).size() == 1, "Litematica's own Generic tab button is missing " + labels(gui));
                SelfTest.equal(true, Ctx.getField(turbo.get(0), "enabled"), "Turbo tab button clickable while not selected");
                click(turbo.get(0));
                return null;
            });
            ctx.ticks(2);

            ctx.onMain(() -> {
                SelfTest.equal(true, live.flag("selected"), "Turbo tab selected after the click");
                List<String> expected = new ArrayList<>();

                for (Object option : (List<?>) Ctx.getStatic(live.tab, "OPTIONS"))
                {
                    expected.add((String) Ctx.call(option, "getName"));
                }

                SelfTest.equal(expected, SettingsTests.configNames(gui), "options shown after clicking Turbo");
                List<Object> turbo = buttons(gui, label);
                SelfTest.equal(1, turbo.size(), "Turbo tab buttons after the click " + labels(gui));
                SelfTest.equal(false, Ctx.getField(turbo.get(0), "enabled"), "selected Turbo tab button greyed out");
                click(buttons(gui, genericLabel).get(0));
                return null;
            });
            ctx.ticks(2);

            ctx.onMain(() -> {
                SelfTest.equal(false, live.flag("selected"), "Turbo tab left after clicking Litematica's Generic tab");
                Object shown = ctx.screen();
                Object configs = shown != null && shown.getClass().getName().equals("fi.dy.masa.litematica.gui.GuiConfigs") ? shown : gui;

                for (String name : SettingsTests.configNames(configs))
                {
                    SelfTest.check(name.startsWith("turbo") == false, "Generic tab still shows Turbo's " + name);
                }

                return null;
            });
        }
        finally
        {
            ctx.onMain(() -> {
                Ctx.setStatic(live.tab, "selected", false);
                Ctx.call(dataManager, "setConfigGuiTab", previousTab);
                ctx.setScreen(previousScreen);
                return null;
            });
            ctx.ticks(2);
        }
    }

    /** The malilib buttons of a screen that show this text. */
    static List<Object> buttons(Object gui, String text) throws ReflectiveOperationException
    {
        List<Object> found = new ArrayList<>();

        for (Object button : (List<?>) Ctx.getField(gui, "buttons"))
        {
            if (text.equals(Ctx.getField(button, "displayString")))
            {
                found.add(button);
            }
        }

        return found;
    }

    static List<Object> labels(Object gui) throws ReflectiveOperationException
    {
        List<Object> labels = new ArrayList<>();

        for (Object button : (List<?>) Ctx.getField(gui, "buttons"))
        {
            labels.add(Ctx.getField(button, "displayString"));
        }

        return labels;
    }

    /** What a left click on the button does: its action listener (the same one malilib's mouse handling calls). */
    static void click(Object button) throws ReflectiveOperationException
    {
        Object listener = Ctx.getField(button, "actionListener");
        SelfTest.check(listener != null, "button has no action");
        Ctx.call(listener, "actionPerformedWithButton", button, 0);
    }
}
