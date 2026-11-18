"""Align the isolated Figura review rig to canonical Blockbench arm axes and mark each wrist."""
import base64
import json
from pathlib import Path
import struct
import sys
import zlib

model_file = Path(sys.argv[1])
avatar = json.loads(model_file.read_text())
assert avatar["resolution"] == {"width": 16, "height": 16}
assert {part["name"] for part in avatar["elements"]} == {"left_sleeve", "right_sleeve"}
texture = avatar["textures"][0]
assert len(avatar["textures"]) == 1 and texture["width"] == texture["height"] == 16

# Each sleeve samples one half of the atlas. Cream sleeves have a cyan left
# cuff and a copper right cuff at the wrist, with a deeper seam for contrast.
base = (255, 224, 239)
raw = bytearray()
for y in range(16):
    raw.append(0)
    for x in range(16):
        left = x >= 8
        color = base
        if 10 <= y <= 14:
            color = (38, 210, 224) if left else (242, 146, 71)
        if y == 13:
            color = (31, 117, 149) if left else (158, 81, 54)
        raw.extend(color)

def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 2, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))
texture["source"] = "data:image/png;base64," + base64.b64encode(png).decode("ascii")
for part in avatar["elements"]:
    start = 8 if part["name"] == "left_sleeve" else 0
    for direction, face in part["faces"].items():
        assert face["uv"] == [0, 0, 16, 16]
        # Keep the cap cream rather than wrapping the side's stripe over it.
        face["uv"] = [start, 0, start + 8, 1 if direction in ("up", "down") else 16]
# Figura/Blockbench uses positive local X on the character's anatomical right.
# The older generic compatibility fixture used the opposite X for all its
# sleeve geometry and item pivots. Correct both as a pair in the review copy.
for part in avatar["elements"]:
    old_min, old_max = part["from"][0], part["to"][0]
    part["from"][0], part["to"][0] = -old_max, -old_min
    if "origin" in part:
        part["origin"][0] *= -1
for arm in avatar["outliner"]:
    arm["origin"][0] *= -1
    pivot = next(n for n in arm["children"] if isinstance(n, dict) and n["name"].endswith("ItemPivot"))
    pivot["origin"][0] *= -1
    pivot["origin"][2] = -1
    assert (pivot["origin"][0] > 0) == (arm["name"] == "RightArm")
lua_file = model_file.with_name("script.lua")
with lua_file.open("a", encoding="utf-8") as out:
    out.write("\nvanilla_model.LEFT_SLEEVE:setVisible(false)\nvanilla_model.RIGHT_SLEEVE:setVisible(false)\n")
model_file.write_text(json.dumps(avatar, separators=(",", ":")), encoding="utf-8")

