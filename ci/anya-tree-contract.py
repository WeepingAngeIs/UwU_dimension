"""Validate the shipped tree against Anya's untouched saved-world extraction."""
import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
docs = root / "docs/anya-finished-tree"
assert hashlib.sha256((docs / "anya-finished-sakura.nbt").read_bytes()).hexdigest() == "cf3eeb543e027a6740bc15667b6965a85c5491b1374addcee908fbd9f0c39c09"
baseline = json.loads((docs / "tree-blocks-exact.json").read_text())
changes = json.loads((docs / "bark-substitutions.json").read_text())
with ZipFile(sys.argv[1]) as jar:
    actual = json.loads(jar.read("assets/uwudimension/giant_sakura_exact.json"))
    roster = json.loads(jar.read("assets/uwudimension/counter_roster.json"))
    retired = {e["id"] for e in roster if e["shape"] == "bow"}
    assert len(retired) == 15
    for name in jar.namelist():
        if not name.startswith("data/") or not name.endswith(".json"):
            continue
        if any(part in name for part in ("/recipe/", "/loot_table/")):
            tree = json.loads(jar.read(name))
            def refs(value):
                if isinstance(value, dict):
                    return [r for v in value.values() for r in refs(v)]
                if isinstance(value, list):
                    return [r for v in value for r in refs(v)]
                return [value] if isinstance(value, str) else []
            assert not retired.intersection(s.removeprefix("uwudimension:") for s in refs(tree)), name

old = {tuple(e["pos"]): e["state"] for e in baseline}
new = {(e["pos"][0], e["pos"][1] - 60, e["pos"][2]): e["state"] for e in actual}
assert len(old) == len(new) == 5026 and old.keys() == new.keys()
changed = set()
for pos, state in old.items():
    if state != new[pos]:
        assert state["Name"] == "minecraft:cherry_log"
        assert new[pos] == {"Name": "minecraft:cherry_wood", "Properties": state["Properties"]}
        changed.add(pos)
assert len(changed) == len(changes) == 46
assert {tuple(e["pos"]) for e in changes} == changed
assert tuple(max(p[i] for p in old) - min(p[i] for p in old) + 1 for i in range(3)) == (34, 38, 30)
print("ANYA TREE CONTRACT PASS 5026 exact positions/states, 46 documented bark substitutions, 15 retired bows absent from recipes/loot")
