from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile
import json
import sys

# Digest of all unmodified resources and classes from the verified RC.2 grips.1 production JAR.
baseline_digest = "5e7fa550dd6ee1f4d5e636ff9537c4fd9061c1beee93ae4b38f48f9b92631b02"
allowed_changes = {
    "META-INF/neoforge.mods.toml",
    "dev/uwu/dimension/mixin/DawnpetalHeldModelMixin.class",
    "dev/uwu/dimension/mixin/DawnpetalHeldModelMixin$1.class",
    "dev/uwu/dimension/mixin/DawnpetalHeldModelMixin$2.class",
    "dev/uwu/dimension/client/RevivalHandPose.class",
    "dev/uwu/dimension/client/RevivalHandPose$1.class",
    "dev/uwu/dimension/client/RevivalHandPose$BetterCombatAttack.class",
    "dev/uwu/dimension/mixin/RevivalRestingHandMixin.class",
}
new_entries = {"dev/uwu/dimension/client/RevivalHandPose$AnimatedHands.class"}

def unchanged_digest(archive):
    digest = sha256()
    files = sorted(f.filename for f in archive.infolist() if not f.is_dir())
    if len(files) != len(set(files)):
        raise ValueError("Duplicate JAR entries")
    for name in files:
        if name not in allowed_changes | new_entries:
            digest.update(name.encode("utf-8") + b"\0" + sha256(archive.read(name)).digest())
    return digest.hexdigest(), len(set(files) - allowed_changes - new_entries)

if __name__ == "__main__":
    jar = Path(sys.argv[1])
    with ZipFile(jar) as z:
        assert z.testzip() is None
        assert (allowed_changes | new_entries) <= set(z.namelist())
        digest, count = unchanged_digest(z)
        assert digest == baseline_digest, f"An unrelated binary or resource differs from RC.2: {digest}"
        mixins = json.loads(z.read("uwudimension.mixins.json"))
        name = "RevivalRestingHandMixin"
        assert mixins["client"].count(name) == 1 and name not in mixins["mixins"]
        assert b'version="1.5.0-beta.13-combined-rc.2-grips.2"' in z.read("META-INF/neoforge.mods.toml")
        # The hosted client uses the main Gradle source set. Verify it has exactly the JAR's class bytes.
        classes = Path("build/classes/java/main")
        compiled = {p.relative_to(classes).as_posix(): p for p in classes.rglob("*.class")}
        assert set(compiled) == {name for name in z.namelist() if name.endswith(".class")}
        assert all(p.read_bytes() == z.read(name) for name, p in compiled.items())
    print(f"RC2 CONTENT PARITY PASS {count} untouched entries, including forest, reef and animation assets")
    print(f"HELD JAR SHA256 {sha256(jar.read_bytes()).hexdigest()} {jar.name}")
