package com.litematicaturbo.selftest;

import java.util.Set;

/** Shared pieces of the in-world groups: the usual captures and the comparison of two of them. */
final class WorldTests
{
    private WorldTests()
    {
    }

    static Fixture fixture(Ctx ctx) throws Exception
    {
        return Fixture.get(ctx);
    }

    /** Litematica alone: Turbo's master switch off. */
    static Fixture.Capture stock(Fixture f) throws Exception
    {
        return f.capture("stock1", false, 0, true, true);
    }

    static Fixture.Capture stockAgain(Fixture f) throws Exception
    {
        return f.capture("stock2", false, 0, true, true);
    }

    /** Turbo with this many build threads, empty-space skipping and near rebuilds on. */
    static Fixture.Capture threads(Fixture f, int n) throws Exception
    {
        return f.capture("t" + n, true, n, true, true);
    }

    static Fixture.Capture automatic(Fixture f) throws Exception
    {
        return f.capture("tauto", true, 0, true, true);
    }

    static Fixture.Capture repeat4(Fixture f) throws Exception
    {
        return f.capture("t4b", true, 4, true, true);
    }

    static Fixture.Capture noSkip(Fixture f) throws Exception
    {
        return f.capture("noskip", true, 3, false, true);
    }

    static Fixture.Capture noAsync(Fixture f) throws Exception
    {
        return f.capture("noasync", true, 3, true, false);
    }

    /** Turbo's usual setting (3 threads, everything on). */
    static Fixture.Capture turbo(Fixture f) throws Exception
    {
        return threads(f, 3);
    }

    static Set<Long> zones(Fixture f, String... names)
    {
        return f.chunksOf(names);
    }

    /** Fails unless every chunk of the set was built in both captures with exactly the same description. */
    static void same(Fixture.Capture expected, Fixture.Capture actual, Set<Long> chunks, String what)
    {
        int compared = 0;

        for (Long k : chunks)
        {
            Fixture.Sig a = expected.sigs.get(k);
            Fixture.Sig b = actual.sigs.get(k);
            String at = "chunk " + Fixture.keyX(k) + "," + Fixture.keyZ(k);

            SelfTest.check(a != null, what + ": " + at + " missing in '" + expected.label + "'");
            SelfTest.check(b != null, what + ": " + at + " missing in '" + actual.label + "'");

            if (a.lines.equals(b.lines) == false)
            {
                throw new SelfTest.Fail(what + ": " + at + " differs between '" + expected.label + "' and '" + actual.label + "': " + a.diff(b));
            }

            ++compared;
        }

        SelfTest.check(compared == chunks.size() && compared > 0, what + ": nothing compared");
    }

    /** The set of chunks has geometry of its own: not every chunk looks like the empty chunk. */
    static void hasGeometry(Fixture f, Fixture.Capture c, Set<Long> chunks, String what)
    {
        Fixture.Sig empty = c.sigs.get(f.chunksOf("S").toArray(new Long[0])[3]);
        int withGeometry = 0;

        for (Long k : chunks)
        {
            Fixture.Sig s = c.sigs.get(k);

            if (s != null && s.lines.equals(empty.lines) == false)
            {
                ++withGeometry;
            }
        }

        if (withGeometry == 0)
        {
            Long first = chunks.iterator().next();
            Fixture.Sig one = c.sigs.get(first);
            throw new SelfTest.Fail(what + ": no chunk has any geometry (the test would pass on anything); chunk " + Fixture.keyX(first) + "," + Fixture.keyZ(first) + " = "
                                    + (one == null ? "no description" : one.lines.size() + " lines " + one.lines.subList(0, Math.min(20, one.lines.size()))));
        }
    }
}
