package com.litematicaturbo.shared;

import java.util.List;
import java.util.Map;

/** The maps of re-sort copies Litematica keeps from 1.21 on (see SortCopyData). */
final class SortCopyMaps
{
    private SortCopyMaps()
    {
    }

    static void addValues(Map<?, ?> copies, List<Object> out)
    {
        for (Object copy : copies.values())
        {
            if (copy != null)
            {
                out.add(copy);
            }
        }
    }

    /** Empties the map; true when it held a copy. */
    static boolean clear(Map<?, ?> copies)
    {
        boolean held = copies.values().stream().anyMatch(copy -> copy != null);
        copies.clear();
        return held;
    }
}
