"""Mark wrist sides on a copied Figura review fixture, retaining geometry and item pivots."""
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
model_file.write_text(json.dumps(avatar, separators=(",", ":")), encoding="utf-8")

