import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/uwudimension"
DATA = ROOT / "src/main/resources/data/uwudimension"

TREES = {
    "heartbloom": {
        "fruit": "heartberry", "fruit_name": "Heartberries", "leaves": "#F3AFCB",
        "leaf_dark": "#C979A2", "leaf_light": "#FFDCEA", "wood": "#A46779",
        "wood_light": "#D799A7", "wood_dark": "#714557", "fruit_color": "#EF638D",
        "fruit_light": "#FFE2EC", "sapling": "Heartbloom Sapling", "log": "Heartbloom Log",
        "leaf_name": "Heartbloom Fruiting Leaves", "biomes": ["ribbon_bluffs", "lantern_fens"],
    },
    "starpetal": {
        "fruit": "starfruit", "fruit_name": "Starfruit", "leaves": "#F5B8BD",
        "leaf_dark": "#D3869D", "leaf_light": "#FFE5C5", "wood": "#A86268",
        "wood_light": "#DB988B", "wood_dark": "#71434A", "fruit_color": "#F4C94F",
        "fruit_light": "#FFF4AB", "sapling": "Starpetal Sapling", "log": "Starpetal Log",
        "leaf_name": "Starpetal Fruiting Leaves", "biomes": ["velvet_highlands", "ribbon_bluffs"],
    },
    "moonberry": {
        "fruit": "moonberry", "fruit_name": "Moonberries", "leaves": "#C8B9E8",
        "leaf_dark": "#9280C5", "leaf_light": "#EEE8FF", "wood": "#796796",
        "wood_light": "#B7A0D1", "wood_dark": "#514568", "fruit_color": "#59427F",
        "fruit_light": "#A993D4", "sapling": "Moonberry Sapling", "log": "Moonberry Log",
        "leaf_name": "Moonberry Fruiting Leaves", "biomes": ["lantern_fens", "velvet_highlands"],
    },
}


def color(value):
    return tuple(bytes.fromhex(value.lstrip("#"))) + (255,)


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def leaves_texture(spec, fruiting=False):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    dark, base, light = (color(spec[k]) for k in ("leaf_dark", "leaves", "leaf_light"))
    clusters = [
        (4, 2, 9, 6), (1, 5, 6, 10), (6, 5, 12, 11), (3, 9, 8, 14),
        (9, 8, 14, 13), (7, 1, 12, 5),
    ]
    for index, box in enumerate(clusters):
        draw.ellipse(box, fill=base if index % 2 == 0 else dark)
        x1, y1, x2, y2 = box
        draw.rectangle((x1 + 1, y1 + 1, x2 - 1, y2 - 1), fill=base)
    for x, y in ((3, 6), (5, 3), (8, 7), (11, 4), (4, 11), (10, 10), (7, 12), (12, 8)):
        draw.point((x, y), fill=light)
    if fruiting:
        fruits = ((3, 7), (10, 4), (8, 11))
        for x, y in fruits:
            if spec["fruit"] == "heartberry":
                draw.polygon([(x, y + 2), (x - 2, y), (x - 1, y - 2), (x, y - 1),
                              (x + 1, y - 2), (x + 2, y), (x + 1, y + 2)], fill=color(spec["fruit_color"]))
                draw.point((x, y - 1), fill=color(spec["fruit_light"]))
            elif spec["fruit"] == "starfruit":
                draw.polygon([(x, y - 3), (x + 1, y - 1), (x + 3, y - 1), (x + 1, y + 1),
                              (x + 2, y + 3), (x, y + 2), (x - 2, y + 3), (x - 1, y + 1),
                              (x - 3, y - 1), (x - 1, y - 1)], fill=color(spec["fruit_color"]))
                draw.point((x, y), fill=color(spec["fruit_light"]))
            else:
                draw.ellipse((x - 2, y - 2, x + 1, y + 1), fill=color(spec["fruit_color"]))
                draw.point((x - 1, y - 1), fill=color(spec["fruit_light"]))
    return image


def log_side(spec):
    image = Image.new("RGBA", (16, 16), color(spec["wood"]))
    draw = ImageDraw.Draw(image)
    dark, light = color(spec["wood_dark"]), color(spec["wood_light"])
    for x, shade in ((2, dark), (4, light), (7, dark), (9, light), (13, dark)):
        draw.line((x, 0, x, 15), fill=shade)
    draw.line((11, 2, 12, 2, 12, 4, 10, 4, 10, 6), fill=dark)
    draw.line((4, 10, 5, 10, 5, 12, 3, 12), fill=light)
    draw.point((2, 5), fill=light)
    draw.point((14, 11), fill=light)
    return image


def log_top(spec):
    image = Image.new("RGBA", (16, 16), color(spec["wood"]))
    draw = ImageDraw.Draw(image)
    dark, light = color(spec["wood_dark"]), color(spec["wood_light"])
    draw.ellipse((1, 1, 14, 14), outline=dark, width=2)
    draw.ellipse((3, 3, 12, 12), outline=light, width=1)
    draw.ellipse((5, 5, 10, 10), outline=dark, width=1)
    draw.point((8, 8), fill=light)
    return image


def sapling_texture(spec):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    base, dark, light = (color(spec[k]) for k in ("leaves", "leaf_dark", "leaf_light"))
    wood = color(spec["wood"])
    draw.line((8, 15, 8, 9), fill=wood, width=2)
    draw.polygon([(8, 11), (3, 8), (2, 5), (6, 6), (8, 8)], fill=dark)
    draw.polygon([(8, 10), (10, 5), (14, 4), (12, 8)], fill=base)
    draw.polygon([(8, 8), (5, 4), (6, 1), (9, 4), (12, 2), (13, 4)], fill=base)
    draw.point((6, 5), fill=light)
    draw.point((11, 6), fill=light)
    draw.point((8, 3), fill=light)
    draw.point((4, 7), fill=light)
    if spec["fruit"] == "heartberry":
        draw.polygon([(4, 8), (3, 7), (4, 6), (5, 7), (6, 6), (7, 7), (6, 9), (5, 10)], fill=color(spec["fruit_color"]))
    elif spec["fruit"] == "starfruit":
        draw.polygon([(11, 9), (12, 7), (13, 9), (15, 9), (13, 10), (14, 12), (12, 11), (10, 12), (11, 10), (9, 9)], fill=color(spec["fruit_color"]))
    else:
        draw.ellipse((10, 9, 13, 12), fill=color(spec["fruit_color"]))
        draw.point((11, 10), fill=color(spec["fruit_light"]))
    return image


def tree_preview(name, textures):
    tree = Image.new("RGBA", (64, 80), (0, 0, 0, 0))
    log = Image.open(textures / f"{name}_log.png")
    fruiting = Image.open(textures / f"{name}_leaves_fruiting.png")
    leaves = Image.open(textures / f"{name}_leaves.png")
    # A chunky four-by-four canopy with fruit-bearing clusters mixed through it.
    canopy = {
        (1, 0), (2, 0),
        (0, 1), (1, 1), (2, 1), (3, 1),
        (0, 2), (1, 2), (2, 2), (3, 2),
        (1, 3), (2, 3),
    }
    fruit_positions = {(2, 0), (0, 1), (3, 2)}
    for x, y in ((1, 4), (1, 5)):
        tree.alpha_composite(log, (x * 16, y * 16))
    for x, y in canopy:
        texture = fruiting if (x, y) in fruit_positions else leaves
        tree.alpha_composite(texture, (x * 16, y * 16))
    return tree


def tree_config(name, spec):
    def leaf(fruiting):
        return {
            "Name": f"uwudimension:{name}_leaves",
            "Properties": {"distance": "7", "fruiting": str(fruiting).lower(),
                           "persistent": "false", "waterlogged": "false"},
        }

    return {
        "type": "minecraft:tree",
        "config": {
            "decorators": [],
            "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:dirt"}},
            "foliage_placer": {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3},
            "foliage_provider": {
                "type": "minecraft:weighted_state_provider",
                "entries": [
                    {"data": leaf(False), "weight": 4},
                    {"data": leaf(True), "weight": 1},
                ],
            },
            "force_dirt": False,
            "ignore_vines": True,
            "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2},
            "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 5, "height_rand_a": 2, "height_rand_b": 1},
            "trunk_provider": {
                "type": "minecraft:simple_state_provider",
                "state": {"Name": f"uwudimension:{name}_log", "Properties": {"axis": "y"}},
            },
        },
    }


def leaves_loot(name, spec):
    leaves_id = f"uwudimension:{name}_leaves"
    sapling_id = f"uwudimension:{name}_sapling"
    return {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1.0,
                "entries": [{
                    "type": "minecraft:alternatives",
                    "children": [
                        {
                            "type": "minecraft:item", "name": leaves_id,
                            "conditions": [{
                                "condition": "minecraft:any_of",
                                "terms": [
                                    {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
                                    {"condition": "minecraft:match_tool", "predicate": {"predicates": {
                                        "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]
                                    }}},
                                ],
                            }],
                        },
                        {
                            "type": "minecraft:item", "name": sapling_id,
                            "conditions": [
                                {"condition": "minecraft:survives_explosion"},
                                {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                 "chances": [0.05, 0.0625, 0.083333336, 0.1]},
                            ],
                        },
                    ],
                }],
            },
            {
                "rolls": 1.0,
                "conditions": [{"condition": "minecraft:block_state_property", "block": leaves_id,
                                "properties": {"fruiting": "true"}},
                               {"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "minecraft:item", "name": f"uwudimension:{spec['fruit']}"}],
            },
        ],
    }


def self_drop(block_id):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"uwudimension:{block_id}"}],
        }],
        "random_sequence": f"uwudimension:blocks/{block_id}",
    }


def update_vanilla_tags():
    log_ids = [f"uwudimension:{name}_log" for name in TREES]
    sapling_ids = [f"uwudimension:{name}_sapling" for name in TREES]
    for tag, values in {
        "logs": log_ids,
        "logs_that_burn": log_ids,
        "leaves": [f"uwudimension:{name}_leaves" for name in TREES],
        "saplings": sapling_ids,
        "mineable/axe": [f"uwudimension:{name}_log" for name in TREES] + sapling_ids,
        "mineable/hoe": [f"uwudimension:{name}_leaves" for name in TREES],
    }.items():
        path = ROOT / "src/main/resources/data/minecraft/tags/block" / f"{tag}.json"
        current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
        existing = current.setdefault("values", [])
        for block_id in values:
            if block_id not in existing:
                existing.append(block_id)
        current["replace"] = False
        save_json(path, current)
    for tag, values in {
        "logs": log_ids,
        "logs_that_burn": log_ids,
        "leaves": [f"uwudimension:{name}_leaves" for name in TREES],
        "saplings": sapling_ids,
    }.items():
        path = ROOT / "src/main/resources/data/minecraft/tags/item" / f"{tag}.json"
        current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
        existing = current.setdefault("values", [])
        for item_id in values:
            if item_id not in existing:
                existing.append(item_id)
        current["replace"] = False
        save_json(path, current)


def main():
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    model_textures = ASSETS / "textures/block"
    model_textures.mkdir(parents=True, exist_ok=True)
    sapling_textures = ASSETS / "textures/item"

    for name, spec in TREES.items():
        log_side(spec).save(model_textures / f"{name}_log.png")
        log_top(spec).save(model_textures / f"{name}_log_top.png")
        leaves_texture(spec).save(model_textures / f"{name}_leaves.png")
        leaves_texture(spec, True).save(model_textures / f"{name}_leaves_fruiting.png")
        sapling_texture(spec).save(sapling_textures / f"{name}_sapling.png")

        save_json(ASSETS / f"blockstates/{name}_log.json", {"variants": {
            "axis=y": {"model": f"uwudimension:block/{name}_log"},
            "axis=z": {"model": f"uwudimension:block/{name}_log", "x": 90},
            "axis=x": {"model": f"uwudimension:block/{name}_log", "x": 90, "y": 90},
        }})
        save_json(ASSETS / f"models/block/{name}_log.json", {
            "parent": "minecraft:block/cube_column",
            "textures": {"end": f"uwudimension:block/{name}_log_top", "side": f"uwudimension:block/{name}_log"},
        })
        save_json(ASSETS / f"models/item/{name}_log.json", {"parent": f"uwudimension:block/{name}_log"})

        save_json(ASSETS / f"blockstates/{name}_leaves.json", {"multipart": [
            {"when": {"fruiting": "false"}, "apply": {"model": f"uwudimension:block/{name}_leaves"}},
            {"when": {"fruiting": "true"}, "apply": {"model": f"uwudimension:block/{name}_leaves_fruiting"}},
        ]})
        for suffix in ("", "_fruiting"):
            save_json(ASSETS / f"models/block/{name}_leaves{suffix}.json", {
                "parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                "textures": {"all": f"uwudimension:block/{name}_leaves{suffix}"},
            })
        save_json(ASSETS / f"models/item/{name}_leaves.json", {"parent": f"uwudimension:block/{name}_leaves"})

        save_json(ASSETS / f"blockstates/{name}_sapling.json", {"variants": {
            "": {"model": f"uwudimension:block/{name}_sapling"},
        }})
        save_json(ASSETS / f"models/block/{name}_sapling.json", {
            "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
            "textures": {"cross": f"uwudimension:item/{name}_sapling"},
        })
        save_json(ASSETS / f"models/item/{name}_sapling.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"uwudimension:item/{name}_sapling"},
        })

        save_json(DATA / f"worldgen/configured_feature/{name}_tree.json", tree_config(name, spec))
        save_json(DATA / f"worldgen/placed_feature/{name}_tree.json", {
            "feature": f"uwudimension:{name}_tree",
            "placement": [
                {"type": "minecraft:rarity_filter", "chance": 4},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"},
            ],
        })
        save_json(DATA / f"loot_table/blocks/{name}_log.json", self_drop(f"{name}_log"))
        save_json(DATA / f"loot_table/blocks/{name}_sapling.json", self_drop(f"{name}_sapling"))
        save_json(DATA / f"loot_table/blocks/{name}_leaves.json", leaves_loot(name, spec))

        lang[f"block.uwudimension.{name}_log"] = spec["log"]
        lang[f"block.uwudimension.{name}_leaves"] = spec["leaf_name"]
        lang[f"block.uwudimension.{name}_sapling"] = spec["sapling"]

        for biome in spec["biomes"]:
            biome_path = DATA / f"worldgen/biome/{biome}.json"
            data = json.loads(biome_path.read_text(encoding="utf-8"))
            tree_step = data["features"][9]
            feature_id = f"uwudimension:{name}_tree"
            if feature_id not in tree_step:
                tree_step.append(feature_id)
            save_json(biome_path, data)

    lang_path.write_text(json.dumps(lang, indent=2) + "\n", encoding="utf-8")
    update_vanilla_tags()

    cell_w, cell_h, scale = 168, 216, 2
    preview = Image.new("RGBA", (cell_w * 3, cell_h), color("#2B2233"))
    draw = ImageDraw.Draw(preview)
    for index, (name, spec) in enumerate(TREES.items()):
        x0 = index * cell_w
        tree = tree_preview(name, model_textures)
        preview.alpha_composite(tree.resize((64 * scale, 80 * scale), Image.Resampling.NEAREST), (x0 + 20, 0))
        draw.text((x0 + 4, 163), f"{name.title()} · {spec['fruit_name']}", fill=color("#F6E8F4"))
        for icon_index, icon_path in enumerate((f"{name}_sapling.png", f"{name}_log.png", f"{name}_leaves_fruiting.png")):
            icon_folder = sapling_textures if icon_index == 0 else model_textures
            icon = Image.open(icon_folder / icon_path)
            preview.alpha_composite(icon.resize((32, 32), Image.Resampling.NEAREST), (x0 + 10 + icon_index * 52, 181))
    preview_path = ROOT / "docs/art/uwu_dimension_trees_preview.png"
    preview_path.parent.mkdir(parents=True, exist_ok=True)
    preview.save(preview_path)
    print(f"Generated {len(TREES)} original orchard trees with models, textures, saplings, fruit harvesting, and worldgen.")


if __name__ == "__main__":
    main()
