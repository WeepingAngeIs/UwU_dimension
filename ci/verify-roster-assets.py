"""Check the delivered JAR's item models, textures and save-sensitive Glitch model indices."""
import json
import sys
import zipfile
from pathlib import Path

NATIVE = ["cupid_bow", "ripple_bow", "ribbonhare_scepter", "blossom_greatsword",
          "bellheart_axe", "lockkeeper_axe", "vesper_scythe", "axolotl_whip", "frost_whip",
          "rift_whip", "rose_dagger", "satin_whip", "lantern_spear", "tidelace_trident", "blue_hour_wand"]
old = json.loads(Path("ci/probe/resources/legacy-counter-roster.json").read_text(encoding="utf-8-sig"))
with zipfile.ZipFile(sys.argv[1]) as jar:
    assert jar.testzip() is None, "Damaged JAR entry"
    names = set(jar.namelist())
    roster = json.loads(jar.read("assets/uwudimension/counter_roster.json"))
    assert len(roster) == len(old) == 133
    for before, after in zip(old, roster):
        for key in ("id", "target", "school", "shape", "damage", "runic"):
            assert before[key] == after[key], (before["id"], key)
    ids = NATIVE + [entry["id"] for entry in old]
    glitch = json.loads(jar.read("assets/uwudimension/models/item/glitch_bow.json"))
    overrides = {entry["predicate"]["uwudimension:form"]: entry["model"]
                 for entry in glitch["overrides"] if "uwudimension:form" in entry["predicate"]}
    assert len(overrides) == len(ids), "Missing or duplicate Glitch model indices"
    for i, weapon in enumerate(ids, 1):
        assert overrides.get(i) == "uwudimension:item/" + weapon, (i, weapon, overrides.get(i))

    visited = set()
    def inspect_model(reference):
        namespace, path = reference.split(":", 1) if ":" in reference else ("minecraft", reference)
        if namespace != "uwudimension" or path in visited:
            return
        visited.add(path)
        model_path = f"assets/{namespace}/models/{path}.json"
        assert model_path in names, f"Missing model {model_path}"
        model = json.loads(jar.read(model_path))
        if model.get("parent"):
            inspect_model(model["parent"])
        for texture in model.get("textures", {}).values():
            if texture.startswith("uwudimension:"):
                texture_path = "assets/uwudimension/textures/" + texture.split(":", 1)[1] + ".png"
                assert texture_path in names, f"Missing texture {texture_path} from {model_path}"
        for override in model.get("overrides", []):
            inspect_model(override["model"])

    tools = [material + "_" + kind for material in ("silver", "roseglass", "vesper_opal")
             for kind in ("pickaxe", "axe", "shovel", "hoe")]
    for weapon in ids + ["glitch_bow", "heart_launcher", "bell_hammer"] + tools:
        inspect_model("uwudimension:item/" + weapon)
    print(f"ROSTER ASSET PASS {len(ids) + 3 + len(tools)} item models; {len(visited)} model dependencies and {len(ids)} stable Glitch overrides")
