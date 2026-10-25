package com.litematicaturbo.selftest;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * audit/: every patch applies on this version (the in-game form of audit.sh). For each mixin listed in
 * litematica_turbo.mixins.json: Turbo's guard saw it, its target (a Litematica / malilib class) loads, every
 * injection point was found, and the loaded target class really carries the patch (each of the mixin's own
 * turbo$ methods merged in, or the accessor interface implemented). Plain Litematica classes have none of it.
 */
final class AuditTests
{
    private static final String CONFIG = "litematica_turbo.mixins.json";

    private AuditTests()
    {
    }

    static void register(Ctx ctx, SelfTest.Tests tests) throws Exception
    {
        String json = resource(ctx.loader, CONFIG);
        List<String> listed = new ArrayList<>();
        String mixinPackage = null;

        if (json != null)
        {
            Matcher pkg = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            mixinPackage = pkg.find() ? pkg.group(1) : null;
            Matcher lists = Pattern.compile("\"(client|mixins|common)\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);

            while (lists.find())
            {
                Matcher names = Pattern.compile("\"([^\"]+)\"").matcher(lists.group(2));

                while (names.find())
                {
                    listed.add(names.group(1));
                }
            }
        }

        final String mixinPkg = mixinPackage;
        Class<?> guard = ctx.turbo("TurboMixinGuard");

        tests.add("audit/config-read", () -> {
            SelfTest.check(json != null, CONFIG + " is not on the game's class path");
            SelfTest.equal(ctx.pkg + ".mixin", mixinPkg, "mixin package in " + CONFIG);
            SelfTest.check(listed.size() >= 8, "only " + listed.size() + " mixins listed in " + CONFIG);
            SelfTest.check(json.contains(ctx.pkg + ".TurboMixinGuard"), "the config does not use Turbo's guard plugin");
        });

        for (String name : listed)
        {
            tests.add("audit/mixin/" + name, () -> mixin(ctx, guard, mixinPkg + "." + name));
        }

        tests.add("audit/config-complete", () -> {
            Map<?, ?> targets = (Map<?, ?>) Ctx.getStatic(guard, "TARGETS");
            List<String> missing = new ArrayList<>();

            for (String name : listed)
            {
                if (targets.containsKey(mixinPkg + "." + name) == false)
                {
                    missing.add(name);
                }
            }

            SelfTest.check(missing.isEmpty(), "listed but never offered to the guard: " + missing);
            SelfTest.check((Integer) Ctx.call(guard, "mixinCount") >= listed.size(), "mixinCount " + Ctx.call(guard, "mixinCount") + " < " + listed.size());
        });

        tests.add("audit/verify-clean", () -> {
            List<?> failures = (List<?>) Ctx.call(guard, "verify", ctx.loader);
            SelfTest.check(failures.isEmpty(), "TurboMixinGuard.verify: " + failures);
            List<?> optional = (List<?>) Ctx.call(guard, "optionalFailures");
            SelfTest.check(optional.isEmpty(), "settings tab patches: " + optional);
            SelfTest.equal(Boolean.TRUE, Ctx.call(guard, "settingsTabOk"), "settingsTabOk");
        });

        tests.add("audit/turbo-on", () -> {
            Class<?> config = ctx.cls("com.litematicaturbo.core.TurboConfig");
            SelfTest.equal(null, Ctx.call(config, "incompatibility"), "TurboConfig.incompatibility (set when a patch does not fit)");
            Object c = Ctx.call(config, "get");
            SelfTest.equal(Boolean.TRUE, Ctx.getField(c, "enabled"), "Turbo enabled after start-up");
            SelfTest.equal(Boolean.TRUE, Ctx.call(c, "threadingEnabled"), "threadingEnabled with the default settings");
        });

        tests.add("audit/live-renderer-patched", () -> {
            // the world renderer Litematica really uses is the patched class (not a copy from another loader)
            // on the render thread: the first call creates the renderer (GL set-up; 1.16.5 crashes on another thread)
            Object renderer = ctx.onMain(() -> Ctx.call(Ctx.call(ctx.cls("fi.dy.masa.litematica.render.LitematicaRenderer"), "getInstance"), "getWorldRenderer"));
            SelfTest.check(renderer != null, "LitematicaRenderer has no world renderer");
            Class<?> target = Class.forName("fi.dy.masa.litematica.render.schematic.WorldRendererSchematic", false, ctx.loader);
            SelfTest.equal(target, renderer.getClass(), "live world renderer class");
            boolean stats = false;

            for (Method m : target.getDeclaredMethods())
            {
                stats |= m.getName().endsWith("turbo$appendStats") || m.getName().equals("turbo$appendStats");
            }

            SelfTest.check(stats, "the F3 stats handler is not merged into the live WorldRendererSchematic");
        });
    }

    private static void mixin(Ctx ctx, Class<?> guard, String mixin) throws Exception
    {
        Map<?, ?> targets = (Map<?, ?>) Ctx.getStatic(guard, "TARGETS");
        Map<?, ?> results = (Map<?, ?>) Ctx.getStatic(guard, "RESULTS");
        String target = (String) targets.get(mixin);
        SelfTest.check(target != null, "the guard never saw this mixin (not applied to anything)");
        SelfTest.check(target.startsWith("fi.dy.masa."), "target is not a Litematica / malilib class: " + target);

        Class<?> targetClass;

        try
        {
            // loading applies the patches; initialising is left to the game (static set-up may need its render thread)
            targetClass = Class.forName(target, false, ctx.loader);
        }
        catch (Throwable t)
        {
            throw new SelfTest.Fail("target " + target + " does not load: " + t);
        }

        ClassInfo info = ClassInfo.read(ctx.loader, mixin);

        if (info.isInterface)
        {
            // accessor / invoker mixin: the target implements it
            boolean implemented = false;

            for (Class<?> c = targetClass; c != null && implemented == false; c = c.getSuperclass())
            {
                for (Class<?> i : c.getInterfaces())
                {
                    implemented |= i.getName().equals(mixin);
                }
            }

            SelfTest.check(implemented, target + " does not implement the accessor " + mixin);
            return;
        }

        Object result = results.get(mixin);
        SelfTest.check(result != null, "postApply never ran: not applied to " + target);
        SelfTest.check("".equals(result), "injection points not found:" + result);

        Set<String> merged = new LinkedHashSet<>();

        for (Method m : targetClass.getDeclaredMethods())
        {
            merged.add(m.getName());
        }

        for (Field f : targetClass.getDeclaredFields())
        {
            merged.add(f.getName());
        }

        List<String> missing = new ArrayList<>();

        for (String own : info.ownTurboMembers)
        {
            boolean found = false;

            for (String name : merged)
            {
                found |= name.equals(own) || name.endsWith("$" + own);
            }

            if (found == false)
            {
                missing.add(own);
            }
        }

        SelfTest.check(missing.isEmpty(), "not merged into " + target + ": " + missing);
        SelfTest.check(info.ownTurboMembers.isEmpty() == false || info.methodCount > 1, "the mixin class has nothing to apply");
    }

    static String resource(ClassLoader loader, String path) throws IOException
    {
        try (InputStream in = loader.getResourceAsStream(path))
        {
            return in == null ? null : new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    static byte[] readAll(InputStream in) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];

        for (int n; (n = in.read(buf)) > 0; )
        {
            out.write(buf, 0, n);
        }

        return out.toByteArray();
    }

    /** What a mixin class declares, read from its class file (a mixin class itself must never be loaded). */
    static final class ClassInfo
    {
        boolean isInterface;
        int methodCount;
        /** Its own methods and fields named turbo$... (handlers, @Unique members), not abstract ones. */
        final Set<String> ownTurboMembers = new LinkedHashSet<>();

        static ClassInfo read(ClassLoader loader, String className) throws IOException
        {
            byte[] bytes;

            try (InputStream in = loader.getResourceAsStream(className.replace('.', '/') + ".class"))
            {
                if (in == null)
                {
                    throw new SelfTest.Fail("class file of " + className + " not found");
                }

                bytes = readAll(in);
            }

            DataInputStream data = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
            ClassInfo info = new ClassInfo();
            data.readInt();
            data.readUnsignedShort();
            data.readUnsignedShort();
            int count = data.readUnsignedShort();
            String[] utf8 = new String[count];

            for (int i = 1; i < count; i++)
            {
                int tag = data.readUnsignedByte();

                switch (tag)
                {
                    case 1: utf8[i] = data.readUTF(); break;
                    case 3: case 4: data.readInt(); break;
                    case 5: case 6: data.readLong(); ++i; break;
                    case 7: case 8: case 16: case 19: case 20: data.readUnsignedShort(); break;
                    case 9: case 10: case 11: case 12: case 17: case 18: data.readInt(); break;
                    case 15: data.readUnsignedByte(); data.readUnsignedShort(); break;
                    default: throw new IOException("unknown constant pool tag " + tag + " in " + className);
                }
            }

            info.isInterface = (data.readUnsignedShort() & 0x0200) != 0;
            data.readUnsignedShort();
            data.readUnsignedShort();
            data.skipBytes(2 * data.readUnsignedShort());

            for (int kind = 0; kind < 2; kind++) // fields, then methods
            {
                int members = data.readUnsignedShort();

                for (int m = 0; m < members; m++)
                {
                    int access = data.readUnsignedShort();
                    String name = utf8[data.readUnsignedShort()];
                    data.readUnsignedShort();

                    if (kind == 1)
                    {
                        ++info.methodCount;
                    }

                    if (name.startsWith("turbo$") && (access & 0x0400) == 0) // not abstract
                    {
                        info.ownTurboMembers.add(name);
                    }

                    for (int a = data.readUnsignedShort(); a > 0; a--)
                    {
                        data.readUnsignedShort();
                        data.skipBytes(data.readInt());
                    }
                }
            }

            return info;
        }
    }
}
