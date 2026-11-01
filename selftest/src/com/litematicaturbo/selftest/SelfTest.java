package com.litematicaturbo.selftest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The test runner and its protocol (stdout, one line each, read by ingame-test.sh):
 * <pre>
 * SCOPE-TEST total N
 * SCOPE-TEST pass id | SCOPE-TEST fail id :: reason | SCOPE-TEST skip id :: reason
 * SCOPE TESTS: P passed, F failed, S skipped
 * </pre>
 * Ids are group/name (the groups are the prefixes in test-plan.json). A test that shows a known, not yet fixed
 * mod bug gets an id starting with knownbug/: only a filter starting with knownbug/ runs those.
 */
public final class SelfTest
{
    public static final String KNOWN_BUG = "knownbug/";

    /** A test body: returns normally = pass; {@link Fail} = fail; {@link Skip} = skip. */
    interface Body
    {
        void run() throws Exception;
    }

    static final class Fail extends RuntimeException
    {
        Fail(String reason)
        {
            super(reason);
        }
    }

    static final class Skip extends RuntimeException
    {
        Skip(String reason)
        {
            super(reason);
        }
    }

    /** The tests one group registers, in order. */
    static final class Tests
    {
        final Map<String, Body> list = new LinkedHashMap<>();

        void add(String id, Body body)
        {
            if (this.list.put(id, body) != null)
            {
                throw new IllegalStateException("duplicate test id " + id);
            }
        }
    }

    private SelfTest()
    {
    }

    static void check(boolean ok, String reason)
    {
        if (ok == false)
        {
            throw new Fail(reason);
        }
    }

    static void equal(Object expected, Object actual, String what)
    {
        if (expected == null ? actual != null : expected.equals(actual) == false)
        {
            throw new Fail(what + ": expected " + expected + ", got " + actual);
        }
    }

    static Skip skip(String reason)
    {
        return new Skip(reason);
    }

    static int run(Ctx ctx, String filter) throws Exception
    {
        Tests tests = new Tests();
        AuditTests.register(ctx, tests);
        SettingsTests.register(ctx, tests);
        StatsTests.register(ctx, tests);
        // the in-world groups last: they leave the title screen for a test world
        HarnessTests.register(ctx, tests);
        BuildTests.register(ctx, tests);
        NearTests.register(ctx, tests);
        EmptyTests.register(ctx, tests);
        DrawTests.register(ctx, tests);
        FarTests.register(ctx, tests);
        MoveTests.register(ctx, tests);
        PlaceTests.register(ctx, tests);
        MemoryTests.register(ctx, tests);
        CompatTests.register(ctx, tests);
        SafetyTests.register(ctx, tests);

        List<String> prefixes = new ArrayList<>();

        for (String p : filter.split(","))
        {
            if (p.trim().isEmpty() == false)
            {
                prefixes.add(p.trim());
            }
        }

        boolean knownBugs = prefixes.isEmpty() == false && prefixes.get(0).startsWith(KNOWN_BUG);
        Map<String, Body> chosen = new LinkedHashMap<>();

        for (Map.Entry<String, Body> e : tests.list.entrySet())
        {
            String id = e.getKey();
            boolean matches = prefixes.isEmpty() || prefixes.stream().anyMatch(id::startsWith);

            if (matches && (id.startsWith(KNOWN_BUG) == false || knownBugs))
            {
                chosen.put(id, e.getValue());
            }
        }

        Agent.OUT.println("SCOPE-TEST total " + chosen.size());
        int passed = 0, failed = 0, skipped = 0;

        for (Map.Entry<String, Body> e : chosen.entrySet())
        {
            String id = e.getKey();

            try
            {
                e.getValue().run();
                Agent.OUT.println("SCOPE-TEST pass " + id);
                ++passed;
            }
            catch (Skip s)
            {
                Agent.OUT.println("SCOPE-TEST skip " + id + " :: " + oneLine(s.getMessage()));
                ++skipped;
            }
            catch (Fail f)
            {
                Agent.OUT.println("SCOPE-TEST fail " + id + " :: " + oneLine(f.getMessage()));
                ++failed;
            }
            catch (Throwable t)
            {
                Agent.OUT.println("SCOPE-TEST fail " + id + " :: " + oneLine(String.valueOf(t)));
                t.printStackTrace(Agent.OUT);
                ++failed;
            }
        }

        Agent.OUT.println("SCOPE TESTS: " + passed + " passed, " + failed + " failed, " + skipped + " skipped");
        return failed == 0 ? 0 : 1;
    }

    private static String oneLine(String s)
    {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ');
    }
}
