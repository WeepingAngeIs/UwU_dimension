import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/uwudimension"
DATA = ROOT / "src/main/resources/data/uwudimension"

COLORS = {
    "edge": (202, 86, 139, 255),
    "edge_light": (232, 126, 174, 255),
    "pink": (247, 161, 198, 255),
    "light": (255, 193, 219, 255),
    "pale": (255, 222, 237, 255),
    "core": (255, 244, 250, 255),
    "white": (255, 255, 255, 255),
    "silver_dark": (91, 105, 123, 255),
    "silver": (188, 201, 217, 255),
    "silver_light": (230, 237, 247, 255),
    "silver_white": (255, 255, 255, 255),
}


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def heart_pixels():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = {
        2: [(3, 6), (9, 12)],
        3: [(2, 6), (9, 13)],
        4: [(1, 14)],
        5: [(1, 14)],
        6: [(2, 13)],
        7: [(3, 12)],
        8: [(4, 11)],
        9: [(5, 10)],
        10: [(6, 9)],
        11: [(7, 8)],
    }
    shape = {
        (x, y)
        for y, spans in rows.items()
        for left, right in spans
        for x in range(left, right + 1)
    }
    boundary = {
        (x, y) for x, y in shape
        if any((x + dx, y + dy) not in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    }
    gradient = (COLORS["edge"], COLORS["edge_light"], COLORS["pink"], COLORS["light"], COLORS["pale"], COLORS["core"])
    for x, y in shape:
        depth = min(abs(x - bx) + abs(y - by) for bx, by in boundary)
        image.putpixel((x, y), gradient[min(depth, len(gradient) - 1)])
    # Bright facets make the heart read as a glowing glass core in-game.
    for x, y in ((4, 4), (5, 4), (4, 5), (6, 3), (7, 3), (8, 3), (8, 6), (7, 7)):
        image.putpixel((x, y), COLORS["white"] if y in (4, 5) and x in (4, 5) else COLORS["core"])
    return image


def metal_texture():
    image = Image.new("RGBA", (16, 16), COLORS["silver"])
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 1), fill=COLORS["silver_dark"])
    draw.rectangle((0, 2, 15, 3), fill=COLORS["silver_white"])
    draw.rectangle((0, 4, 15, 5), fill=COLORS["silver_light"])
    draw.rectangle((0, 6, 15, 9), fill=COLORS["silver"])
    draw.rectangle((0, 10, 15, 11), fill=COLORS["silver_light"])
    draw.rectangle((0, 12, 15, 13), fill=COLORS["silver_white"])
    draw.rectangle((0, 14, 15, 15), fill=COLORS["silver_dark"])
    for x in (2, 7, 12):
        image.putpixel((x, 3), COLORS["silver"])
        image.putpixel((x, 12), COLORS["silver"])
    return image


def flower_particle():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    centers = ((8, 2), (4, 6), (12, 6), (5, 11), (11, 11))
    for cx, cy in centers:
        for y in range(cy - 2, cy + 3):
            for x in range(cx - 2, cx + 3):
                if 0 <= x < 16 and 0 <= y < 16 and max(abs(x - cx), abs(y - cy)) <= 2:
                    edge = max(abs(x - cx), abs(y - cy))
                    image.putpixel((x, y), COLORS["edge_light"] if edge == 2 else COLORS["pale"])
        image.putpixel((cx, cy), COLORS["light"])
    for x, y in ((7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8)):
        image.putpixel((x, y), COLORS["white"] if (x + y) % 2 == 0 else COLORS["core"])
    for x, y in ((5, 13), (6, 12), (10, 12), (11, 13)):
        image.putpixel((x, y), (96, 173, 112, 255))
    return image


def row_runs(image):
    for y in range(16):
        x = 0
        while x < 16:
            color = image.getpixel((x, y))
            if color[3] == 0:
                x += 1
                continue
            end = x + 1
            while end < 16 and image.getpixel((end, y)) == color:
                end += 1
            yield x, end, y, color
            x = end


def silver_bezel_pixels(heart):
    shape = {(x, y) for y in range(16) for x in range(16) if heart.getpixel((x, y))[3]}
    inside = set(shape)
    for _ in range(3):
        inside = {
            (x, y) for x, y in inside
            if all((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        }
    outline = {
        (x, y) for x, y in shape
        if any((x + dx, y + dy) not in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    }
    inner_outline = {
        (x, y) for x, y in inside
        if any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    }
    outline |= inner_outline
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    metal = metal_texture()
    for x, y in outline:
        image.putpixel((x, y), metal.getpixel((x, y)))
    return image


def heart_ring_pixels(heart):
    shape = {(x, y) for y in range(16) for x in range(16) if heart.getpixel((x, y))[3]}
    inside = set(shape)
    for _ in range(3):
        inside = {
            (x, y) for x, y in inside
            if all((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        }
    metal = {(x, y) for x, y in shape
             if any((x + dx, y + dy) not in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    metal |= {(x, y) for x, y in inside
              if any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    ring = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in shape - inside:
        if (x, y) not in metal:
            ring.putpixel((x, y), heart.getpixel((x, y)))
    return ring


def face(texture, uv=None, shade=None):
    value = {"texture": f"#{texture}"}
    if uv is not None:
        value["uv"] = uv
    if shade is not None:
        value["shade"] = shade
    return value


def heart_model(hanging):
    silhouette = heart_pixels()
    heart = heart_ring_pixels(silhouette)
    bezel = silver_bezel_pixels(silhouette)
    elements = []
    # Trace both edges of the open heart with slim silver pixelwork. The pink
    # glass sits between the rails, leaving the center genuinely open.
    for left, right, row, color in row_runs(bezel):
        y = 14 - row
        elements.append({
            "from": [left, y, 4],
            "to": [right, y + 1, 12],
            "shade": False,
            "faces": {
                "north": face("metal", [left, row, right, row + 1], False),
                "south": face("metal", [left, row, right, row + 1], False),
                "east": face("metal"),
                "west": face("metal"),
                "up": face("metal"),
                "down": face("metal"),
            },
        })
    for left, right, row, color in row_runs(heart):
        y = 14 - row
        elements.append({
            "from": [left, y, 5],
            "to": [right, y + 1, 11],
            "shade": False,
            "faces": {
                "north": face("heart", [left, row, right, row + 1], False),
                "south": face("heart", [left, row, right, row + 1], False),
                "east": face("rim", [0, 0, 8, 1]),
                "west": face("rim", [0, 0, 8, 1]),
                "up": face("rim", [0, 0, right - left, 8]),
                "down": face("rim", [0, 0, right - left, 8]),
            },
        })
    if hanging:
        elements.extend([
            {"from": [7, 12, 7], "to": [9, 14, 9], "shade": False,
             "faces": {name: face("metal") for name in ("north", "south", "east", "west", "up", "down")}},
            {"from": [7.5, 14, 7.5], "to": [8.5, 16, 8.5], "shade": False,
             "faces": {name: face("metal") for name in ("north", "south", "east", "west", "up", "down")}},
        ])
    else:
        # A tight silver bail joins the heart's cleft to the hanging point.
        elements.extend([
            {"from": [7, 12, 7], "to": [9, 14, 9], "shade": False,
             "faces": {name: face("metal") for name in ("north", "south", "east", "west", "up", "down")}},
            {"from": [6.5, 14, 7], "to": [9.5, 15, 9], "shade": False,
             "faces": {name: face("metal") for name in ("north", "south", "east", "west", "up", "down")}},
        ])

    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "uwudimension:block/heart_neon_heart",
            "heart": "uwudimension:block/heart_neon_heart",
            "rim": "uwudimension:block/heart_neon_rim",
            "metal": "uwudimension:block/heart_neon_metal",
        },
        "elements": elements,
    }


def preview(heart, flower):
    canvas = Image.new("RGBA", (512, 256), (25, 21, 33, 255))
    draw = ImageDraw.Draw(canvas)
    # A checkerboard keeps transparency visible without smoothing pixel edges.
    tile = 16
    for y in range(0, 256, tile):
        for x in range(0, 512, tile):
            if ((x // tile) + (y // tile)) % 2 == 0:
                draw.rectangle((x, y, x + tile - 1, y + tile - 1), fill=(43, 36, 52, 255))
    lantern = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    metal = metal_texture()
    bezel = silver_bezel_pixels(heart)
    lantern.alpha_composite(bezel)
    for y in (0, 1):
        lantern.putpixel((7, y), metal.getpixel((7, y)))
        lantern.putpixel((8, y), metal.getpixel((8, y)))
    lantern.alpha_composite(heart_ring_pixels(heart))
    enlarged_heart = lantern.resize((192, 192), Image.Resampling.NEAREST)
    enlarged_flower = flower.resize((128, 128), Image.Resampling.NEAREST)
    enlarged_heart.putalpha(enlarged_heart.getchannel("A"))
    enlarged_flower.putalpha(enlarged_flower.getchannel("A"))
    canvas.alpha_composite(enlarged_heart, (20, 28))
    canvas.alpha_composite(enlarged_flower, (330, 64))
    draw.text((22, 230), "NEON HEART LANTERN - silver frame + glowing pink core", fill=(248, 226, 241, 255))
    canvas.save(ROOT / "docs/art/heart-neon-lantern-preview.png")


def main():
    block_textures = ASSETS / "textures/block"
    particle_textures = ASSETS / "textures/particle"
    heart = heart_pixels()
    heart_ring = heart_ring_pixels(heart)
    flower = flower_particle()
    heart_ring.save(block_textures / "heart_neon_heart.png")
    heart_ring.save(particle_textures / "neon_heart.png")
    flower.save(particle_textures / "neon_flower.png")
    metal_texture().save(block_textures / "heart_neon_metal.png")
    Image.new("RGBA", (16, 16), COLORS["edge"]).save(block_textures / "heart_neon_rim.png")

    save_json(ASSETS / "blockstates/heart_neon_lantern.json", {
        "variants": {
            "hanging=false,waterlogged=false": {"model": "uwudimension:block/heart_neon_lantern"},
            "hanging=false,waterlogged=true": {"model": "uwudimension:block/heart_neon_lantern"},
            "hanging=true,waterlogged=false": {"model": "uwudimension:block/heart_neon_lantern_hanging"},
            "hanging=true,waterlogged=true": {"model": "uwudimension:block/heart_neon_lantern_hanging"},
        },
    })
    save_json(ASSETS / "blockstates/heart_flash_light.json", {
        "variants": {f"age={age}": {"model": "minecraft:block/air"} for age in range(6)},
    })
    save_json(ASSETS / "models/block/heart_neon_lantern.json", heart_model(False))
    save_json(ASSETS / "models/block/heart_neon_lantern_hanging.json", heart_model(True))
    save_json(ASSETS / "models/item/heart_neon_lantern.json", {
        "parent": "uwudimension:block/heart_neon_lantern",
    })
    save_json(ASSETS / "particles/neon_heart.json", {"textures": ["uwudimension:neon_heart"]})
    save_json(ASSETS / "particles/neon_flower.json", {"textures": ["uwudimension:neon_flower"]})
    save_json(DATA / "loot_table/blocks/heart_neon_lantern.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "uwudimension:heart_neon_lantern"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })
    save_json(DATA / "recipe/heart_neon_lantern.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [
            {"item": "minecraft:lantern"},
            {"item": "minecraft:pink_dye"},
            {"item": "minecraft:glowstone_dust"},
        ],
        "result": {"id": "uwudimension:heart_neon_lantern", "count": 1},
    })
    preview(heart, flower)


if __name__ == "__main__":
    main()
