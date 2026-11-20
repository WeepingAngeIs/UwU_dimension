from hashlib import sha256
import json
from pathlib import Path
import re
import sys
from zipfile import ZipFile


BASELINE = "2c8cbc7fc47a0e65ec7721a9c74c3d10248daa417708eb707c614d55653fa52e"
JAVA = "dev/uwu/dimension/"
EFFECT = "assets/uwudimension/effects/revival_lotus_burst/"
TEXTURE = "assets/uwudimension/textures/effect/revival_lotus_burst"
CHANGED = {
    "META-INF/neoforge.mods.toml", JAVA + "RevivalBlossoms.class",
    JAVA + "RevivalBlossoms$Ledger.class", JAVA + "RevivalBlossoms$FlowerAxe.class",
    JAVA + "RevivalBlossoms$FlowerDirk.class",
}
REQUIRED_CLASSES = {JAVA + name + ".class" for name in (
    "RevivalBloom", "RevivalBloom$Cue", "RevivalBloomProtection",
    "RevivalLotusTerrainLight", "RevivalLotusTerrainLight$SourceBlock", "RevivalLotusTerrainLight$Source",
    "client/RevivalLotusBurstClient"
)}
ART = {
    EFFECT + "mesh.json", EFFECT + "motion.json", EFFECT + "lighting.json",
    TEXTURE + ".png", TEXTURE + ".png.mcmeta",
    TEXTURE + "_glow.png", TEXTURE + "_glow.png.mcmeta",
    "assets/uwudimension/textures/effect/revival_lotus_halo.png",
    "assets/uwudimension/textures/effect/revival_lotus_halo.png.mcmeta",
}


def verify_timeline(archive):
    motion = json.loads(archive.read(EFFECT + "motion.json"))
    mesh = json.loads(archive.read(EFFECT + "mesh.json"))
    frames = motion["frames"]
    assert motion["duration_ticks"] == 34 and motion["sample_step_ticks"] == .5
    assert [frame["tick"] for frame in frames] == [tick / 2 for tick in range(69)]
    assert frames[-1]["glow"] == 0 and all(pose[-2:] == [0, 0] for pose in frames[-1]["poses"])
    for frame in frames:
        assert len(frame["poses"]) == len(motion["parts"])
        assert all(len(pose) == 14 and 0 <= pose[-2] <= 1 for pose in frame["poses"])
    for part in motion["parts"]:
        assert part["mesh"] in mesh["meshes"]
    for name in ART:
        assert archive.read(name) == Path("src/main/resources", name).read_bytes()
    print(f"LOTUS VISUAL TIMELINE PASS {len(motion['parts'])} parts, {len(frames)} poses over 34 ticks")
    lighting = json.loads(archive.read(EFFECT + "lighting.json"))
    assert lighting["duration_ticks"] == motion["duration_ticks"]
    samples = [frame["block_light_level"] for frame in lighting["frames"] if frame["tick"] % 1 == 0]
    source = Path("src/main/java", JAVA, "RevivalLotusTerrainLight.java").read_text()
    native = list(map(int, re.search(r"LEVELS = \{([^}]+)\}", source)[1].split(",")))
    assert native + [0] * (len(samples) - len(native)) == samples
    print(f"LOTUS LIGHT TIMELINE PASS peak {max(samples)}, zero from tick {len(native)}")


def verify(jar):
    with ZipFile(jar) as archive:
        assert archive.testzip() is None
        names = sorted(entry.filename for entry in archive.infolist() if not entry.is_dir())
        assert len(set(names)) == len(names)
        assert CHANGED | REQUIRED_CLASSES | ART <= set(names)
        digest = sha256()
        count = 0
        for name in names:
            if name in CHANGED | REQUIRED_CLASSES | ART or name.startswith(JAVA + "client/RevivalLotusBurstClient$"):
                continue
            digest.update(name.encode() + b"\0" + sha256(archive.read(name)).digest())
            count += 1
        assert digest.hexdigest() == BASELINE, f"Unexpected JAR change: {digest.hexdigest()}"
        assert b'version="1.5.0-beta.13-combined-rc.2-lotus.1"' in archive.read("META-INF/neoforge.mods.toml")
        compiled = Path("build/classes/java/main")
        classes = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob("*.class")}
        assert set(classes) == {name for name in names if name.endswith(".class")}
        assert all(archive.read(name) == path.read_bytes() for name, path in classes.items())
        verify_timeline(archive)
    print(f"LOTUS RC2 PARITY PASS {count} unchanged entries")
    print(f"LOTUS JAR SHA256 {sha256(jar.read_bytes()).hexdigest()} {jar.name}")


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
