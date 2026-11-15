#!/usr/bin/env python3
"""Reconstruct the exact 32x32 RGBA pixels into a new output folder (Pillow required)."""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument("--output", required=True, type=Path, help="new or existing output folder for PNGs")
args = p.parse_args()
data = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
atlas = Image.open(HERE / data["atlas"]).convert("RGBA")
args.output.mkdir(parents=True, exist_ok=True)
for entry in data["entries"]:
    x, y = entry["atlas_x"], entry["atlas_y"]
    sprite = atlas.crop((x, y, x + data["tile_size"], y + data["tile_size"]))
    if hashlib.sha256(sprite.tobytes()).hexdigest() != entry["pixel_sha256"]:
        raise ValueError("Atlas mismatch: " + entry["item"])
    sprite.save(args.output / (entry["item"] + ".png"))
print("Rebuilt and verified", len(data["entries"]), "sprites in", args.output)
