from hashlib import sha256
import json
from pathlib import Path
import sys
from zipfile import ZipFile


JAVA = "dev/uwu/dimension/"
SCOPED_CLASSES = {
    "DawnpetalAbility", "DawnpetalAbilities", "DawnpetalAbilityClient", "RelicFlight",
    "mixin/DawnpetalAbilityKeyMixin", "SharedAbilityHotbar", "mixin/SharedAbilityInputMixin",
    "mixin/SharedAbilityGuiMixin", "mixin/SharedAbilitySpellHotbarMixin",
}
SCOPED_RESOURCES = {
    "META-INF/neoforge.mods.toml", "assets/uwudimension/lang/en_us.json", "uwudimension.mixins.json",
}
BASELINE = "d584fa2e006c1c4add181ce8551b95647b52707510c2e86f3e52e12bff27b2a4"


def scoped(name):
    if name in SCOPED_RESOURCES:
        return True
    if name.startswith(JAVA) and name.endswith(".class"):
        return name[len(JAVA):-6].split("$")[0] in SCOPED_CLASSES
    return False


jar = Path(sys.argv[1])
with ZipFile(jar) as archive:
    assert archive.testzip() is None
    names = sorted(entry.filename for entry in archive.infolist() if not entry.is_dir())
    assert len(set(names)) == len(names)
    assert b'version="1.5.0-beta.13-combined-rc.2-hotbar.1"' in archive.read("META-INF/neoforge.mods.toml")
    assert JAVA + "SharedAbilityHotbar.class" in names
    assert JAVA + "DawnpetalAbilityClient.class" not in names
    mixins = json.loads(archive.read("uwudimension.mixins.json"))
    for mixin in ("SharedAbilityInputMixin", "SharedAbilityGuiMixin", "SharedAbilitySpellHotbarMixin"):
        assert mixin in mixins["client"] and JAVA + f"mixin/{mixin}.class" in names
    assert "DawnpetalAbilityKeyMixin" not in mixins["client"]
    digest = sha256()
    count = 0
    for name in names:
        if not scoped(name):
            digest.update(name.encode() + b"\0" + sha256(archive.read(name)).digest())
            count += 1
    assert digest.hexdigest() == BASELINE, f"Unexpected JAR change: {digest.hexdigest()}"
    compiled = Path("build/classes/java/main")
    classes = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob("*.class")}
    assert set(classes) == {name for name in names if name.endswith(".class")}
    assert all(archive.read(name) == path.read_bytes() for name, path in classes.items())
print(f"HOTBAR RC2 PARITY PASS {count} unchanged entries")
print(f"HOTBAR JAR SHA256 {sha256(jar.read_bytes()).hexdigest()} {jar.name}")
