"""Dev check: run the mixin audit against older Litematica releases inside each version's allowed range.

usage: python range-check.py [mc ...]     (default: every Fabric version)

Per version it lists the Litematica releases for that Minecraft version on Modrinth that fit the
"litematica" range in fabric.mod.json, pairs each with the MaLiLib released closest before it, and
audits from the oldest up until one passes (then also the newest, which is the build target).
Results go to range-check.txt.
"""
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
TOOLCHAIN = os.path.join(ROOT, "..", "..", "toolchain")


def modrinth(project, mc):
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    with urllib.request.urlopen(f"https://api.modrinth.com/v2/project/{project}/version?{q}") as r:
        return sorted(json.load(r), key=lambda v: v["date_published"])


def nums(version):
    m = re.search(r"(\d+)\.(\d+)\.(\d+)", version)
    return tuple(int(x) for x in m.groups()) if m else None


def in_range(version, rng):
    v = nums(version)
    if rng.strip() == "*" or v is None:
        return True
    for part in rng.split():
        op, ver = re.match(r"([<>=]*)(.*)", part).groups()
        w = nums(ver + ".0" if ver.count(".") == 1 else ver)
        if (op == ">=" and v < w) or (op == "<" and v >= w) or (op == "<=" and v > w) or (op == ">" and v <= w):
            return False
    return True


def props(mc):
    p = {}
    with open(os.path.join(ROOT, "versions", mc, "gradle.properties")) as f:
        for line in f:
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                p[k.strip()] = v.strip()
    return p


def audit(mc, lite, mali):
    env = dict(os.environ, GRADLE_USER_HOME=os.path.join(TOOLCHAIN, "gradle-home"),
               JAVA_HOME=os.path.join(TOOLCHAIN, "jdk25", "jdk-25.0.4.1+1"))
    opts = os.path.join(ROOT, "versions", mc, "run", "options.txt")
    if os.path.exists(opts):  # test launches are muted
        with open(opts) as f:
            text = re.sub(r"(?m)^soundCategory_master:.*$", "soundCategory_master:0.0", f.read())
        with open(opts, "w") as f:
            f.write(text)
    cmd = ["sh", "./gradlew", f":versions:{mc}:runClient", "-Paudit", f"-Pmc={mc}",
           f"-Plitematica_runtime={lite['id']}", f"-Pmalilib_runtime={mali['id']}", "--console=plain"]
    out = subprocess.run(cmd, cwd=ROOT, env=env, capture_output=True, text=True, errors="replace").stdout
    m = re.search(r"AUDIT (\d+) mixins, failures: (\d+)", out)
    fails = [l.split("Patch did not apply: ")[1] for l in out.splitlines() if "Patch did not apply: " in l]
    if m:
        return int(m.group(2)) == 0, f"{m.group(2)}/{m.group(1)} failed", fails
    err = [l for l in out.splitlines() if "Replace mod" in l or "requires" in l or "FAILED" in l][:3]
    return False, "NO RESULT", err


def main():
    versions = sys.argv[1:] or sorted(d for d in os.listdir(os.path.join(ROOT, "versions"))
                                      if "forge" not in d and os.path.exists(os.path.join(ROOT, "versions", d, "gradle.properties")))
    report = open(os.path.join(ROOT, "range-check.txt"), "a")
    for mc in versions:
        with open(os.path.join(ROOT, "versions", mc, "src", "main", "resources", "fabric.mod.json")) as f:
            fmj = json.load(f)
        rng = fmj["depends"]["litematica"]
        game = props(mc)["minecraft_version"]
        lites = [v for v in modrinth("litematica", game) if in_range(v["version_number"], rng)]
        malis = modrinth("malilib", game)
        print(f"{mc} ({rng}): {len(lites)} Litematica releases", flush=True)
        report.write(f"== {mc} litematica {rng}\n")
        tested = set()

        def run(v):
            tested.add(v["id"])
            before = [m for m in malis if m["date_published"][:10] <= v["date_published"][:10]] or malis[:1]
            ok, summary, detail = audit(mc, v, before[-1])
            line = f"  {v['version_number']} ({v['id']}) + malilib {before[-1]['version_number']}: {'OK' if ok else summary}"
            print(line, flush=True)
            report.write(line + "\n")
            for d in detail:
                report.write(f"      {d}\n")
            report.flush()
            return ok

        # oldest first; when it fails, binary search for the first release that passes
        if lites and not run(lites[0]):
            lo, hi = 0, len(lites) - 1
            if run(lites[hi]):
                while hi - lo > 1:
                    mid = (lo + hi) // 2
                    if run(lites[mid]):
                        hi = mid
                    else:
                        lo = mid
                report.write(f"  -> first passing: {lites[hi]['version_number']}\n")
        if lites and lites[-1]["id"] not in tested:
            run(lites[-1])


main()
