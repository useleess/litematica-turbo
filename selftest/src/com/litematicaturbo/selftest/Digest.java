package com.litematicaturbo.selftest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A stable, readable description of what a chunk build produced: one text line per fact, read from Litematica's
 * build result by reflection (so one reader fits every version). Two builds of the same chunk gave the same
 * geometry when their lines are equal. What is read: the built chunk data (which block layers and overlay types
 * have geometry, the block entities with their positions, the translucency sort data - one position per quad -,
 * the sizes of the built vertex / index buffers) and the chunk's vertex buffers (index count, draw mode). Times,
 * GPU handles and locks are left out; buffer contents are never read (a closed native buffer must not be touched),
 * only their sizes.
 */
final class Digest
{
    /** Objects of these kinds are looked into; everything else is described by its class name only. */
    private static final String[] DEEP = {
        "fi.dy.masa.litematica.render", "BufferBuilder", "BuiltBuffer", "MeshData", "ByteBufferBuilder", "SortState", "DrawState",
        "RenderedBuffer", "SortingData", "SortingState", "VertexBuffer", "GpuBuffer", "ChunkMesh", "BufferCache", "DrawParameters",
        "TransparentSort"};
    private static final String[] NEVER = {
        "WorldRenderer", "Dispatcher", "Worker", "ChunkRendererSchematic", "ChunkCache", "World", "Level", "Minecraft", "Client", "Task",
        "Uploader"};
    private static final Pattern SKIP_FIELD = Pattern.compile("(?i:.*(time|thread|handle|fence|label|pointer|address|closed|vertexarray).*)|.*Lock.*|lock.*|(?i:id)|.*Id"); // (not "lock" inside "block...": those are the block layers)
    private static final Pattern HASH = Pattern.compile("@[0-9a-fA-F]+|\\$\\$Lambda[\\w$/.]*");
    private static final int MAX_LINES = 20000;
    private static final int MAX_DEPTH = 7;

    private Digest()
    {
    }

    /** Adds the lines of one object to out, each starting with path. */
    static void lines(String path, Object o, List<String> out)
    {
        walk(path, o, 0, new IdentityHashMap<Object, Boolean>(), out);
    }

    private static boolean leafLike(Object o)
    {
        return o == null || o instanceof Number || o instanceof Boolean || o instanceof Character || o instanceof CharSequence ||
               o instanceof Enum || isKind(o, "Vec3i") || isKind(o, "BlockEntity") || deep(o) == false;
    }

    private static boolean deep(Object o)
    {
        String name = o.getClass().getName();

        for (String n : NEVER)
        {
            if (o.getClass().getSimpleName().contains(n))
            {
                return false;
            }
        }

        for (String d : DEEP)
        {
            if (name.contains(d))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isKind(Object o, String simpleName)
    {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass())
        {
            if (c.getSimpleName().equals(simpleName))
            {
                return true;
            }
        }

        return false;
    }

    /** One-line text of an object that is not walked. */
    private static String leaf(Object o)
    {
        if (o == null)
        {
            return "null";
        }

        if (o instanceof Enum)
        {
            return ((Enum<?>) o).name();
        }

        if (o instanceof Number || o instanceof Boolean || o instanceof Character || o instanceof CharSequence)
        {
            return String.valueOf(o);
        }

        try
        {
            if (isKind(o, "Vec3i"))
            {
                return Ctx.call(o, "getX") + "," + Ctx.call(o, "getY") + "," + Ctx.call(o, "getZ");
            }

            if (isKind(o, "BlockEntity"))
            {
                Object pos = Game.callAny(o, new String[] {"getPos", "getBlockPos"});
                return "BE:" + o.getClass().getSimpleName() + "@" + leaf(pos);
            }
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return "BE:" + o.getClass().getSimpleName() + "@?";
        }

        if (deep(o) == false && o.getClass().getName().startsWith("net.minecraft") == false)
        {
            return HASH.matcher(String.valueOf(o)).replaceAll("");
        }

        String text = HASH.matcher(String.valueOf(o)).replaceAll("");
        return HASH.matcher(o.getClass().getSimpleName()).replaceAll("") + (text.length() > 160 ? text.substring(0, 160) : text);
    }

    private static void walk(String path, Object o, int depth, IdentityHashMap<Object, Boolean> path_, List<String> out)
    {
        if (out.size() >= MAX_LINES)
        {
            return;
        }

        if (o == null || leafLike(o) && (o instanceof Collection || o instanceof Map || o.getClass().isArray()) == false)
        {
            out.add(path + "=" + leaf(o));
            return;
        }

        Class<?> c = o.getClass();

        if (c.isArray())
        {
            if (o instanceof Object[])
            {
                Object[] a = (Object[]) o;
                out.add(path + ".length=" + a.length);

                for (int i = 0; i < a.length; i++)
                {
                    walk(path + "[" + i + "]", a[i], depth + 1, path_, out);
                }

                return;
            }

            int hash;
            int length;

            if (o instanceof float[]) { hash = Arrays.hashCode((float[]) o); length = ((float[]) o).length; }
            else if (o instanceof int[]) { hash = Arrays.hashCode((int[]) o); length = ((int[]) o).length; }
            else if (o instanceof long[]) { hash = Arrays.hashCode((long[]) o); length = ((long[]) o).length; }
            else if (o instanceof byte[]) { hash = Arrays.hashCode((byte[]) o); length = ((byte[]) o).length; }
            else if (o instanceof short[]) { hash = Arrays.hashCode((short[]) o); length = ((short[]) o).length; }
            else if (o instanceof double[]) { hash = Arrays.hashCode((double[]) o); length = ((double[]) o).length; }
            else if (o instanceof boolean[]) { hash = Arrays.hashCode((boolean[]) o); length = ((boolean[]) o).length; }
            else { hash = 0; length = ((char[]) o).length; }

            out.add(path + "=array(" + length + "):" + hash);
            return;
        }

        if (o instanceof Buffer)
        {
            out.add(path + "=" + c.getSimpleName() + "(remaining " + ((Buffer) o).remaining() + ")");
            return;
        }

        if (depth >= MAX_DEPTH || path_.containsKey(o))
        {
            out.add(path + "=<" + c.getSimpleName() + " too deep>");
            return;
        }

        path_.put(o, Boolean.TRUE);

        try
        {
            if (o instanceof Map)
            {
                List<String> keys = new ArrayList<>();
                Map<String, Object> byKey = new java.util.TreeMap<>();

                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet())
                {
                    byKey.put(leaf(e.getKey()), e.getValue());
                }

                keys.addAll(byKey.keySet());
                out.add(path + ".size=" + keys.size());

                for (String key : keys)
                {
                    walk(path + "[" + key + "]", byKey.get(key), depth + 1, path_, out);
                }
            }
            else if (o instanceof Collection)
            {
                Collection<?> col = (Collection<?>) o;
                out.add(path + ".size=" + col.size());
                boolean unordered = o instanceof Set;
                List<String> items = new ArrayList<>();
                int i = 0;

                for (Object e : col)
                {
                    List<String> sub = new ArrayList<>();
                    walk(unordered ? "e" : path + "[" + (i++) + "]", e, depth + 1, path_, sub);
                    items.add(String.join("|", sub));
                }

                if (unordered)
                {
                    Collections.sort(items);

                    for (String item : items)
                    {
                        out.add(path + "{}=" + item);
                    }
                }
                else
                {
                    out.addAll(items);
                }
            }
            else
            {
                List<Field> fields = new ArrayList<>();

                for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
                {
                    for (Field f : k.getDeclaredFields())
                    {
                        if (Modifier.isStatic(f.getModifiers()) == false && SKIP_FIELD.matcher(f.getName()).matches() == false)
                        {
                            fields.add(f);
                        }
                    }
                }

                Collections.sort(fields, (a, b) -> a.getName().compareTo(b.getName()));

                for (Field f : fields)
                {
                    try
                    {
                        f.setAccessible(true);
                        walk(path + "." + f.getName(), f.get(o), depth + 1, path_, out);
                    }
                    catch (ReflectiveOperationException | RuntimeException e)
                    {
                        out.add(path + "." + f.getName() + "=<unreadable>");
                    }
                }
            }
        }
        finally
        {
            path_.remove(o);
        }
    }
}
