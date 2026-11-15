"""Bake our Blockbench per-face UVs into Minecraft's entity cube texture layout."""
from pathlib import Path
import json
from PIL import Image

repo = Path(__file__).resolve().parents[1]
src = repo / "docs/models"
dst = repo / "src/main/resources/assets/uwudimension"
model = json.loads((src / "female_uwu_guardian.bbmodel").read_text(encoding="utf-8-sig"))
skin = Image.open(src / "female_uwu_guardian.png").convert("RGBA")
elements = {e["uuid"]: e for e in model["elements"]}
# Java uses downward-positive model Y. The DOWN face points toward the top of the head.
tiles = {"west": (0, 1), "north": (1, 1), "east": (2, 1), "south": (3, 1),
         "up": (1, 0), "down": (2, 0)}
sheets = {}
bones = []
bone_names = {"body": "basket", "left_arm": "left", "right_arm": "right",
              "left_leg": "left_root", "right_leg": "right_root"}

def add_cubes(bone, group, pivot):
    for child in group["children"]:
        if isinstance(child, dict):
            add_cubes(bone, child, pivot)
            continue
        cube = elements[child]
        assert not any(cube["rotation"]), "Rotate new parts in the baker before export"
        a, b = cube["from"], cube["to"]
        uv = tuple(tuple(cube["faces"][face]["uv"]) for face in tiles)
        index = sheets.setdefault(uv, len(sheets))
        xyz = [a[0]-pivot[0], pivot[1]-b[1], a[2]-pivot[2]]
        size = [hi-lo for lo, hi in zip(a, b)]
        bone["cubes"].append({"name": cube["name"], "position": [round(x, 4) for x in xyz],
                              "size": [round(x, 4) for x in size], "tile": index})

for group in model["outliner"][0]["children"]:
    x, y, z = group["origin"]
    bone = {"name": bone_names.get(group["name"], group["name"]),
            "pivot": [x, 24-y, z], "cubes": []}
    add_cubes(bone, group, [x, y, z])
    bones.append(bone)

atlas = Image.new("RGBA", (256, 32*((len(sheets)+3)//4)))
for faces, index in sheets.items():
    for (face, (tx, ty)), bounds in zip(tiles.items(), faces):
        u, v, u2, v2 = (int(i) for i in bounds)
        patch = skin.crop((min(u,u2), min(v,v2), max(u,u2), max(v,v2)))
        if u > u2: patch = patch.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        if v > v2: patch = patch.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        if face == "down": patch = patch.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        patch = patch.resize((16,16), Image.Resampling.NEAREST)
        atlas.paste(patch, ((index%4)*64+tx*16,(index//4)*32+ty*16))
atlas.save(dst / "textures/entity/female_uwu_guardian.png")
(dst / "geometry").mkdir(parents=True, exist_ok=True)
(dst / "geometry/guardian_bones.json").write_text(json.dumps(
    {"width": atlas.width, "height": atlas.height, "bones": bones}, indent=2) + "\n", encoding="utf-8")
