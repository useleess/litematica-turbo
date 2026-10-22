package com.litematicaturbo.mc1194;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Fail-safe. The mixin config is not required and injectors default to require = 0, so a patch that
 * does not fit the installed Litematica never crashes the game. Instead this plugin checks every
 * applied mixin: each injector handler must be called from the patched class at least as often as
 * its "expect" says (1 when unset). {@link #verify} then loads every target class; any mixin that
 * failed or did not fully apply switches Turbo off (stock Litematica behaviour).
 */
public final class TurboMixinGuard implements IMixinConfigPlugin
{
    private static final String INJECTOR_PACKAGE = "Lorg/spongepowered/asm/mixin/injection/";

    /** Mixin class -> its target class (from the config). */
    private static final Map<String, String> TARGETS = new ConcurrentHashMap<>();
    /** Mixin class -> problem; empty string while it applied cleanly. */
    private static final Map<String, String> RESULTS = new ConcurrentHashMap<>();
    /** Problems of settings-menu patches: these only hide the Turbo tab, Turbo keeps running. */
    private static final List<String> OPTIONAL_FAILURES = new ArrayList<>();

    @Override
    public void onLoad(String mixinPackage)
    {
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        // Mixin may hand over internal names (a/b/C) depending on the loader version
        TARGETS.put(mixinClassName, targetClassName.replace('/', '.'));
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
        StringBuilder problems = new StringBuilder();
        ClassNode mixin = mixinInfo.getClassNode(0);

        for (MethodNode handler : mixin.methods)
        {
            int required = requiredCount(handler);

            if (required > 0)
            {
                int calls = countCalls(targetClass, handler.name);

                if (calls < required)
                {
                    problems.append(String.format(" %s (%d/%d)", handler.name, calls, required));
                }
            }
        }

        RESULTS.put(mixinClassName, problems.toString());
    }

    /** How many injection points the handler needs; 0 when it is not an injector. */
    private static int requiredCount(MethodNode method)
    {
        List<AnnotationNode> annotations = new ArrayList<>();

        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);

        for (AnnotationNode annotation : annotations)
        {
            if (annotation.desc.startsWith(INJECTOR_PACKAGE) && annotation.desc.indexOf('/', INJECTOR_PACKAGE.length()) < 0 &&
                annotation.desc.endsWith("Invoker;") == false)
            {
                // Mixin itself only enforces "require" (crash); handlers state their count as "expect"
                Object expect = annotationValue(annotation, "expect");
                return expect instanceof Integer ? (Integer) expect : 1;
            }
        }

        return 0;
    }

    private static Object annotationValue(AnnotationNode annotation, String key)
    {
        if (annotation.values != null)
        {
            for (int i = 0; i + 1 < annotation.values.size(); i += 2)
            {
                if (key.equals(annotation.values.get(i)))
                {
                    return annotation.values.get(i + 1);
                }
            }
        }

        return null;
    }

    /** Calls from the patched class to the merged handler (Mixin renames it to ...$name). */
    private static int countCalls(ClassNode target, String handlerName)
    {
        String suffix = "$" + handlerName;
        int count = 0;

        for (MethodNode method : target.methods)
        {
            for (AbstractInsnNode insn : method.instructions.toArray())
            {
                if (insn instanceof MethodInsnNode)
                {
                    MethodInsnNode call = (MethodInsnNode) insn;

                    if (call.owner.equals(target.name) && (call.name.endsWith(suffix) || call.name.equals(handlerName)))
                    {
                        ++count;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Loads every patched class (which applies the mixins) and returns what did not apply.
     * Empty when everything is fine. Settings-menu problems go to {@link #optionalFailures} instead.
     */
    public static List<String> verify(ClassLoader loader)
    {
        List<String> failures = new ArrayList<>();
        OPTIONAL_FAILURES.clear();

        for (Map.Entry<String, String> entry : TARGETS.entrySet())
        {
            String target = entry.getValue();
            List<String> list = isOptional(entry.getKey()) ? OPTIONAL_FAILURES : failures;

            try
            {
                Class.forName(target, false, loader);
            }
            catch (Throwable t)
            {
                list.add(target + ": could not load: " + t);
                continue;
            }

            String result = RESULTS.get(entry.getKey());

            if (result == null)
            {
                list.add(shortName(entry.getKey()) + ": not applied to " + target);
            }
            else if (result.isEmpty() == false)
            {
                list.add(shortName(entry.getKey()) + ": injection points not found:" + result);
            }
        }

        if (TARGETS.isEmpty())
        {
            failures.add("mixin config plugin saw no mixins");
        }

        return failures;
    }

    public static List<String> optionalFailures()
    {
        return OPTIONAL_FAILURES;
    }

    /** False when a settings-menu patch did not fit: the Turbo tab is then left out. */
    public static boolean settingsTabOk()
    {
        return OPTIONAL_FAILURES.isEmpty();
    }

    private static boolean isOptional(String mixinClassName)
    {
        return shortName(mixinClassName).startsWith("GuiConfigs");
    }

    public static int mixinCount()
    {
        return TARGETS.size();
    }

    private static String shortName(String className)
    {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
