from hashlib import sha256
import json
from pathlib import Path
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
    "RevivalBloom", "RevivalBloom$Cue", "RevivalBloomProtection", "client/RevivalLotusBurstClient"
)}
ART = {
    EFFECT + "mesh.json", EFFECT + "motion.json",
    TEXTURE + ".png", TEXTURE + ".png.mcmeta",
    TEXTURE + "_glow.png", TEXTURE + "_glow.png.mcmeta",
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
        verify_timeline(archive)
    print(f"LOTUS RC2 PARITY PASS {count} unchanged entries")
    print(f"LOTUS JAR SHA256 {sha256(jar.read_bytes()).hexdigest()} {jar.name}")


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
