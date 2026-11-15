"""Check release resources directly in the distributable jar before it is uploaded."""
import json
import struct
import sys
import tomllib
from zipfile import ZipFile


def check_jar(jar):
    paths = set(jar.namelist())
    data = "data/uwudimension/"
    art = "assets/uwudimension/"

    def read(path):
        return json.loads(jar.read(path))

    def image(path, size=None):
        png = jar.read(art + "textures/" + path)
        assert png[:8] == b"\x89PNG\r\n\x1a\n", path
        if size:
            assert struct.unpack(">II", png[16:24]) == size, path

    assert jar.testzip() is None
    for file in sorted(paths):
        if file.endswith(".json"):
            read(file)
    assert {p for p in paths if "/dimension/" in p and p.startswith("data/") and p.endswith(".json")} == {
        data + "dimension/sakura_reach.json"}
    assert not any(p.startswith("dev/uwu/test/") for p in paths)
    assert not any(p.startswith("data/minecraft/worldgen/biome/") for p in paths)
    meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
    assert meta["mods"][0]["version"] == "1.5.0-beta.1"
    locale = read(art + "lang/en_us.json")
    for family in ("petalweave", "nightbriar"):
        for part in ("helmet", "chestplate", "leggings", "boots"):
            name = family + "_" + part
            assert locale["item.uwudimension." + name]
            assert read(data + "recipe/" + name + ".json")["result"]["id"] == "uwudimension:" + name
            image("item/" + name + ".png", (16, 16))
        for layer in (1, 2):
            image(f"models/armor/{family}_layer_{layer}.png", (64, 32))
    for family in ("petal_house", "dusk_house"):
        banner = read(data + "recipe/" + family + "_standard.json")["result"]["components"]
        assert len(banner["minecraft:banner_patterns"]) == 3
        assert json.loads(banner["minecraft:item_name"])["translate"] in locale
    for glyph in read(art + "font/field_sketches.json")["providers"]:
        image(glyph["file"].split(":", 1)[1])
    print(f"SAKURA RELEASE ASSETS PASS {len(paths)} entries")


if __name__ == "__main__":
    with ZipFile(sys.argv[1]) as package:
        check_jar(package)
