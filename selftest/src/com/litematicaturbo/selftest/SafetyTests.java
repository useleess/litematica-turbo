package com.litematicaturbo.selftest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * safety/: the fail-safe. When a patch does not fit the installed Litematica, Turbo switches itself off for the
 * session, tells the player in the chat, keeps the saved setting as the player left it, and Litematica works as it does
 * without Turbo. The guard that finds the broken patches is checked with made-up broken ones (an unloadable target, a
 * patch that never applied, an injection point that was not found), because the real patches all fit here.
 */
final class SafetyTests
{
    private SafetyTests()
    {
    }

    /** Runs body with the session marked incompatible, and puts the flag, the switch and the file back after. */
    private static void incompatible(Ctx ctx, SettingsTests.Live live, String reason, SelfTest.Body body) throws Exception
    {
        live.restoring(() -> {
            Object config = live.config();
            boolean wasEnabled = (Boolean) Ctx.getField(config, "enabled");
            Object was = Ctx.getStatic(live.configClass, "incompatible");

            try
            {
                ctx.onMain(() -> Ctx.call(live.configClass, "markIncompatible", reason));
                body.run();
            }
            finally
            {
                ctx.onMain(() -> {
                    Ctx.setStatic(live.configClass, "incompatible", was);
                    Ctx.setField(config, "enabled", wasEnabled);
                    Ctx.setField(config, "enabledInFile", true);
                    return null;
                });
            }
        });
    }

    /** The text of every chat line the player has now. */
    private static String chatText(Ctx ctx) throws Exception
    {
        return ctx.onMain(() -> {
            Object hud = Game.fieldOfType(ctx.client, "InGameHud", "Gui");
            Object chat = Game.callAny(hud, new String[] {"getChatHud", "getChat"});
            StringBuilder all = new StringBuilder();

            for (Class<?> c = chat.getClass(); c != null; c = c.getSuperclass())
            {
                for (Field f : c.getDeclaredFields())
                {
                    if (Collection.class.isAssignableFrom(f.getType()) == false)
                    {
                        continue;
                    }

                    f.setAccessible(true);

                    for (Object line : new ArrayList<Object>((Collection<?>) f.get(chat)))
                    {
                        if (line == null)
                        {
                            continue;
                        }

                        for (Field lf : line.getClass().getDeclaredFields())
                        {
                            String type = lf.getType().getSimpleName();

                            if (type.equals("Text") || type.equals("Component") || type.equals("MutableText") || type.equals("MutableComponent"))
                            {
                                lf.setAccessible(true);
                                all.append(String.valueOf(lf.get(line))).append('\n');
                            }
                        }
                    }
                }
            }

            return all.toString();
        });
    }

    static void register(Ctx ctx, SelfTest.Tests tests) throws Exception
    {
        SettingsTests.Live live = new SettingsTests.Live(ctx);
        Class<?> guard = ctx.turbo("TurboMixinGuard");
        Class<?> tab = ctx.turbo("TurboSettingsTab");

        tests.add("safety/switches-turbo-off", () -> incompatible(ctx, live, "test reason", () -> {
            Object config = live.config();
            SelfTest.equal(false, Ctx.getField(config, "enabled"), "the master switch once a patch does not fit");
            SelfTest.equal("test reason", Ctx.call(live.configClass, "incompatibility"), "the reason kept for the session");
            SelfTest.equal(false, Ctx.call(config, "threadingEnabled"), "Turbo threading");
            SelfTest.equal(false, Ctx.call(config, "skipEmptySectionsEnabled"), "empty-space skipping");
            SelfTest.equal(false, Ctx.call(config, "asyncNearRebuildsEnabled"), "background near rebuilds");
            SelfTest.equal(false, Ctx.call(config, "ghostMoveEnabled"), "smooth placement moving");
        }));

        tests.add("safety/cannot-be-switched-back-on", () -> incompatible(ctx, live, "test reason", () -> {
            Object config = live.config();
            ctx.onMain(() -> Ctx.call(config, "setEnabled", true));
            SelfTest.equal(false, Ctx.getField(config, "enabled"), "the master switch after the player turned it on");
            ctx.onMain(() -> Ctx.call(live.option(SettingsTests.OPTIONS[0]), "setBooleanValue", true));
            ctx.ticks(3);
            SelfTest.equal(false, Ctx.getField(config, "enabled"), "the master switch after the tab's switch was turned on");
        }));

        tests.add("safety/saved-setting-kept", () -> incompatible(ctx, live, "test reason", () -> {
            // the file keeps what the player chose, so an update that fits switches Turbo on again by itself
            Object config = live.config();
            ctx.onMain(() -> Ctx.call(config, "setEnabled", true));
            ctx.onMain(() -> Ctx.call(config, "save"));
            SelfTest.check(live.fileText().contains("enabled=true"), "the file lost the player's 'enabled=true':\n" + live.fileText());
            ctx.onMain(() -> Ctx.call(config, "setEnabled", false));
            ctx.onMain(() -> Ctx.call(config, "save"));
            SelfTest.check(live.fileText().contains("enabled=false"), "the file did not take the player's 'enabled=false':\n" + live.fileText());
        }));

        tests.add("safety/chat-message", () -> incompatible(ctx, live, "7 of 9 patches do not fit this Litematica version", () -> {
            ctx.onMain(() -> {
                Ctx.setStatic(tab, "warnedIncompatible", false);
                return null;
            });
            ctx.ticks(4);
            String chat = chatText(ctx);
            SelfTest.check(chat.contains("Litematica Turbo") && chat.contains("Off"), "no 'Litematica Turbo ... Off' line in the chat:\n" + chat);
            SelfTest.check(chat.contains("7 of 9 patches do not fit this Litematica version"), "the chat line does not say why:\n" + chat);
            SelfTest.check(chat.contains("Litematica works as normal"), "the chat line does not say that Litematica works as normal:\n" + chat);
        }));

        tests.add("safety/message-only-once", () -> incompatible(ctx, live, "once-only reason xyz", () -> {
            ctx.onMain(() -> {
                Ctx.setStatic(tab, "warnedIncompatible", false);
                return null;
            });
            ctx.ticks(4);
            String first = chatText(ctx);
            ctx.ticks(10);
            String second = chatText(ctx);
            SelfTest.equal(count(first, "once-only reason xyz"), 1, "chat lines naming the reason");
            SelfTest.equal(count(second, "once-only reason xyz"), 1, "chat lines naming the reason after more game ticks");
        }));

        tests.add("safety/no-message-when-fine", () -> live.restoring(() -> {
            SelfTest.equal(null, Ctx.call(live.configClass, "incompatibility"), "incompatibility of a healthy session");
            ctx.onMain(() -> {
                Ctx.setStatic(tab, "warnedIncompatible", false);
                return null;
            });
            String before = chatText(ctx);
            ctx.ticks(6);
            SelfTest.equal(count(before, "Litematica Turbo] Off"), count(chatText(ctx), "Litematica Turbo] Off"), "'Turbo is off' lines in the chat of a healthy session");
        }));

        tests.add("safety/litematica-builds-as-stock", () -> {
            Fixture f = WorldTests.fixture(ctx);
            Fixture.Capture stock = WorldTests.stock(f);
            incompatible(ctx, live, "test reason", () -> {
                Fixture.Capture off = f.captureAsIs("incompatible");
                WorldTests.hasGeometry(f, off, f.allChunks(), "the whole test world");
                WorldTests.same(stock, off, f.allChunks(), "plain Litematica vs a session Turbo switched off");
                SelfTest.equal(0L, off.skippedSections, "sections skipped by a switched-off Turbo");
            });
        });

        tests.add("safety/game-keeps-running", () -> {
            Fixture f = WorldTests.fixture(ctx);
            incompatible(ctx, live, "test reason", () -> {
                ctx.ticks(30);
                f.awaitIdle("a switched-off session keeps its view", f.allChunks(), null);
                SelfTest.check(ctx.onMain(() -> ctx.client != null), "the client is gone");
            });
        });

        // ---- the guard, with made-up broken patches ----

        tests.add("safety/guard-finds-unloadable-target", () -> withFakes(guard, () -> {
            targets(guard).put("fake.pkg.BrokenMixin", "no.such.litematica.Class");
            List<String> failures = verify(ctx, guard);
            SelfTest.check(failures.size() == 1 && failures.get(0).contains("no.such.litematica.Class") && failures.get(0).contains("could not load"),
                           "the guard said: " + failures);
        }));

        tests.add("safety/guard-finds-patch-that-never-applied", () -> withFakes(guard, () -> {
            targets(guard).put("fake.pkg.NeverAppliedMixin", "java.lang.Object");
            List<String> failures = verify(ctx, guard);
            SelfTest.check(failures.size() == 1 && failures.get(0).contains("NeverAppliedMixin") && failures.get(0).contains("not applied"),
                           "the guard said: " + failures);
        }));

        tests.add("safety/guard-finds-missing-injection-point", () -> withFakes(guard, () -> {
            targets(guard).put("fake.pkg.HalfAppliedMixin", "java.lang.Object");
            results(guard).put("fake.pkg.HalfAppliedMixin", " turbo$handler (0/1)");
            List<String> failures = verify(ctx, guard);
            SelfTest.check(failures.size() == 1 && failures.get(0).contains("HalfAppliedMixin") && failures.get(0).contains("turbo$handler (0/1)"),
                           "the guard said: " + failures);
        }));

        tests.add("safety/guard-accepts-clean-patch", () -> withFakes(guard, () -> {
            targets(guard).put("fake.pkg.CleanMixin", "java.lang.Object");
            results(guard).put("fake.pkg.CleanMixin", "");
            SelfTest.equal(0, verify(ctx, guard).size(), "problems the guard found with one clean patch added");
        }));

        tests.add("safety/guard-settings-patch-only-hides-tab", () -> withFakes(guard, () -> {
            targets(guard).put("fake.pkg.GuiConfigsBrokenMixin", "no.such.litematica.GuiClass");
            List<String> failures = verify(ctx, guard);
            SelfTest.equal(0, failures.size(), "problems that switch Turbo off for a broken settings-screen patch");
            SelfTest.equal(false, Ctx.call(guard, "settingsTabOk"), "settingsTabOk with a broken settings-screen patch");
            SelfTest.equal(1, ((List<?>) Ctx.call(guard, "optionalFailures")).size(), "optional problems");
        }));

        tests.add("safety/guard-counts-injection-points", () -> withFakes(guard, () -> {
            // a handler asking for 2 injection points where the patched class calls it once is reported, and one asking for 1 is not
            ClassLoader loader = ctx.loader;
            Object target = newNode(loader, "fake/Target");
            addCaller(loader, target, "fake/Target", "turbo$needsTwo");
            addCaller(loader, target, "fake/Target", "turbo$needsOne");
            Object mixin = newNode(loader, "fake/Mixin");
            addHandler(loader, mixin, "turbo$needsTwo", 2);
            addHandler(loader, mixin, "turbo$needsOne", null);
            addPlainMethod(loader, mixin, "notAnInjector");
            Object info = Proxy.newProxyInstance(loader, new Class<?>[] {loader.loadClass("org.spongepowered.asm.mixin.extensibility.IMixinInfo")},
                                                 (InvocationHandler) (proxy, method, args) -> method.getName().equals("getClassNode") ? mixin : null);
            Ctx.call(newPlugin(guard), "postApply", "java.lang.Object", target, "fake.pkg.CountMixin", info);
            String result = String.valueOf(results(guard).get("fake.pkg.CountMixin"));
            SelfTest.check(result.contains("turbo$needsTwo (1/2)"), "the guard noted: '" + result + "'");
            SelfTest.check(result.contains("needsOne") == false && result.contains("notAnInjector") == false, "the guard noted too much: '" + result + "'");
        }));
    }

    private static int count(String text, String part)
    {
        int n = 0;

        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1))
        {
            ++n;
        }

        return n;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> targets(Class<?> guard) throws ReflectiveOperationException
    {
        return (Map<String, String>) Ctx.getStatic(guard, "TARGETS");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> results(Class<?> guard) throws ReflectiveOperationException
    {
        return (Map<String, String>) Ctx.getStatic(guard, "RESULTS");
    }

    @SuppressWarnings("unchecked")
    private static List<String> verify(Ctx ctx, Class<?> guard) throws ReflectiveOperationException
    {
        return (List<String>) Ctx.call(guard, "verify", ctx.loader);
    }

    /** Runs body, then takes every made-up entry out of the guard again and lets it look once more (it clears its optional list then). */
    private static void withFakes(Class<?> guard, SelfTest.Body body) throws Exception
    {
        Map<String, String> t = targets(guard);
        Map<String, String> r = results(guard);

        try
        {
            body.run();
        }
        finally
        {
            t.keySet().removeIf(k -> k.startsWith("fake."));
            r.keySet().removeIf(k -> k.startsWith("fake."));
            Ctx.call(guard, "verify", guard.getClassLoader());
        }
    }

    private static Object newPlugin(Class<?> guard) throws ReflectiveOperationException
    {
        return guard.getDeclaredConstructor().newInstance();
    }

    private static Object newNode(ClassLoader loader, String name) throws ReflectiveOperationException
    {
        Object node = loader.loadClass("org.objectweb.asm.tree.ClassNode").getDeclaredConstructor().newInstance();
        Ctx.setField(node, "name", name);
        return node;
    }

    private static Object newMethod(ClassLoader loader, String name) throws ReflectiveOperationException
    {
        Class<?> methodNode = loader.loadClass("org.objectweb.asm.tree.MethodNode");
        Object m = methodNode.getDeclaredConstructor().newInstance();
        Ctx.setField(m, "name", name);
        Ctx.setField(m, "desc", "()V");
        Ctx.setField(m, "instructions", loader.loadClass("org.objectweb.asm.tree.InsnList").getDeclaredConstructor().newInstance());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static void addCaller(ClassLoader loader, Object target, String owner, String handler) throws ReflectiveOperationException
    {
        Object method = newMethod(loader, "caller");
        Object insn = loader.loadClass("org.objectweb.asm.tree.MethodInsnNode")
            .getDeclaredConstructor(int.class, String.class, String.class, String.class, boolean.class)
            .newInstance(182, owner, handler, "()V", false);
        Ctx.call(Ctx.getField(method, "instructions"), "add", insn);
        ((List<Object>) Ctx.getField(target, "methods")).add(method);
    }

    @SuppressWarnings("unchecked")
    private static void addHandler(ClassLoader loader, Object mixin, String name, Integer expect) throws ReflectiveOperationException
    {
        Object method = newMethod(loader, name);
        Object annotation = loader.loadClass("org.objectweb.asm.tree.AnnotationNode").getDeclaredConstructor(String.class)
            .newInstance("Lorg/spongepowered/asm/mixin/injection/Inject;");

        if (expect != null)
        {
            List<Object> values = new ArrayList<>();
            values.add("expect");
            values.add(expect);
            Ctx.setField(annotation, "values", values);
        }

        List<Object> visible = new ArrayList<>();
        visible.add(annotation);
        Ctx.setField(method, "visibleAnnotations", visible);
        ((List<Object>) Ctx.getField(mixin, "methods")).add(method);
    }

    @SuppressWarnings("unchecked")
    private static void addPlainMethod(ClassLoader loader, Object mixin, String name) throws ReflectiveOperationException
    {
        ((List<Object>) Ctx.getField(mixin, "methods")).add(newMethod(loader, name));
    }
}
