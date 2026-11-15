"""Follow each Moonveil material from its blockstate and item model to shipped textures and data."""
import json
import struct
import sys
from zipfile import ZipFile

MOD = "uwudimension"
PILLARS = ("moonveil_log", "stripped_moonveil_log", "moonveil_wood", "stripped_moonveil_wood")
SHAPES = ("planks", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate")
FURNITURE = ("table", "stool", "bookshelf")
ITEMS = (*PILLARS, "moonveil_leaves", *("moonveil_" + name for name in (*SHAPES, *FURNITURE)))


def check(archive):
    paths = set(archive.namelist())

    def read(name):
        assert name in paths, f"Missing {name}"
        return json.loads(archive.read(name))

    def owned_model(ref, visited):
        if ref in visited or not ref.startswith(MOD + ":"):
            return
        visited.add(ref)
        model = read("assets/uwudimension/models/" + ref.split(":")[1] + ".json")
        for ref in model.get("textures", {}).values():
            if ref.startswith(MOD + ":"):
                name = "assets/uwudimension/textures/" + ref.split(":")[1] + ".png"
                assert name in paths, name
                data = archive.read(name)
                assert data[:8] == b"\x89PNG\r\n\x1a\n" and min(struct.unpack(">II", data[16:24])) >= 16, name
        if "parent" in model:
            owned_model(model["parent"], visited)

    def references(value):
        if isinstance(value, dict):
            if "model" in value:
                yield value["model"]
            for child in value.values():
                yield from references(child)
        elif isinstance(value, list):
            for child in value:
                yield from references(child)

    language = read("assets/uwudimension/lang/en_us.json")
    models = set()
    for name in ITEMS:
        assert language.get(f"block.{MOD}.{name}"), name
        state = read(f"assets/{MOD}/blockstates/{name}.json")
        refs = set(references(state))
        assert refs and any(r.startswith(MOD + ":") for r in refs), name
        for ref in refs:
            owned_model(ref, models)
        owned_model(f"{MOD}:item/{name}", models)
        loot = read(f"data/{MOD}/loot_table/blocks/{name}.json")
        assert loot["pools"], name
    for output in ("moonveil_wood", "stripped_moonveil_wood", *("moonveil_" + s for s in (*SHAPES, *FURNITURE))):
        recipe = read(f"data/{MOD}/recipe/moonveil/{output}.json")
        result = recipe["result"]
        assert result["id"] == f"{MOD}:{output}" and result.get("count",1) > 0, output
    print(f"MOONVEIL ASSETS PASS {len(ITEMS)} blockstates, item models, loot tables, names; {len(models)} distinct native models")


if __name__ == "__main__":
    with ZipFile(sys.argv[1]) as archive:
        check(archive)
