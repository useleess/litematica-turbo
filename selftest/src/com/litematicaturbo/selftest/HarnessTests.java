package com.litematicaturbo.selftest;

/**
 * harness/popup/: the way into the test world never stops at a pop-up. The game's own prompts for an old-format world
 * ("back up and load?", "experimental settings", ...) are answered by the harness, and anything it does not know fails
 * the run after five minutes with the name of the screen (Game.join), never a silent hang.
 */
final class HarnessTests
{
    private HarnessTests()
    {
    }

    static void register(Ctx ctx, SelfTest.Tests tests)
    {
        tests.add("harness/popup/world-opens-without-stopping", () -> {
            Fixture f = WorldTests.fixture(ctx);
            SelfTest.check(ctx.onMain(() -> f.game.player() != null && f.game.clientWorld() != null), "the test world is not open");
        });

        tests.add("harness/popup/no-screen-in-the-way", () -> {
            WorldTests.fixture(ctx);
            Object screen = ctx.onMain(() -> ctx.screen());
            SelfTest.check(screen == null, "a screen is still open in the test world: " + (screen == null ? "" : screen.getClass().getName()));
        });
    }
}
