import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/uwudimension"
DATA = ROOT / "src/main/resources/data/uwudimension"

PLANTS = {
    "heartberry_crop": {
        "seed": "heartberry_seeds", "harvest": "heartberry", "count": [2, 4],
        "leaf": "#4E9B60", "leaf_light": "#79C77A", "stem": "#456D45", "fruit_dark": "#C94B77",
        "fruit": "#F58EB0", "fruit_light": "#FFD1DF",
    },
    "sakura_rice_crop": {
        "seed": "sakura_rice_seeds", "harvest": "sakura_rice", "count": [2, 4],
        "leaf": "#579565", "leaf_light": "#8CC878", "stem": "#54754A", "fruit_dark": "#D1B878",
        "fruit": "#F3E5B6", "fruit_light": "#FFF6D8",
    },
    "starfruit_crop": {
        "seed": "starfruit_seeds", "harvest": "starfruit", "count": [2, 3],
        "leaf": "#39865B", "leaf_light": "#7BBE69", "stem": "#426C45", "fruit_dark": "#D58B35",
        "fruit": "#F7C84E", "fruit_light": "#FFF2A1",
    },
    "frostpetal_crop": {
        "seed": "frostpetal_seeds", "harvest": "frostpetal", "count": [2, 4],
        "leaf": "#579D99", "leaf_light": "#9ED9CF", "stem": "#4D7773", "fruit_dark": "#8AB9D2",
        "fruit": "#D5F4F3", "fruit_light": "#FFFFFF",
    },
    "sweetbean_crop": {
        "seed": "sweetbean_seeds", "harvest": "sweetbean_pods", "count": [2, 3],
        "leaf": "#438553", "leaf_light": "#83BD6C", "stem": "#456B43", "fruit_dark": "#A97A54",
        "fruit": "#D9AA7A", "fruit_light": "#F2D2A3",
    },
    "glowginger_crop": {
        "seed": "glowginger_seeds", "harvest": "glowginger", "count": [2, 3],
        "leaf": "#39775C", "leaf_light": "#79B872", "stem": "#496246", "fruit_dark": "#C57952",
        "fruit": "#F0B66A", "fruit_light": "#FFE1A0",
    },
    "vanilla_bean_crop": {
        "seed": "vanilla_bean_seeds", "harvest": "vanilla_bean", "count": [2, 3],
        "leaf": "#3A7950", "leaf_light": "#83B969", "stem": "#456745", "fruit_dark": "#8F6652",
        "fruit": "#D0A274", "fruit_light": "#F4D7A7",
    },
}

ITEM_NAMES = {
    "heartberry": "Heartberries",
    "sakura_rice": "Sakura Rice",
    "starfruit": "Starfruit",
    "frostpetal": "Frostpetals",
    "sweetbean_pods": "Sweetbean Pods",
    "glowginger": "Glowginger",
    "vanilla_bean": "Vanilla Bean",
    "moonberry": "Moonberries",
    "sakura_flour": "Sakura Rice Flour",
    "heartberry_jam": "Heartberry Jam",
    "shaved_ice": "Shaved Ice",
    "pink_shaved_ice": "Pink Shaved Ice",
    "chocolate_syrup": "Chocolate Syrup",
    "sweetbean_paste": "Sweetbean Paste",
    "maple_syrup": "Maple Syrup",
    "petal_sugar": "Petal Sugar",
    "vanilla_extract": "Vanilla Extract",
}

def make_image():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def hex_color(color):
    return tuple(bytes.fromhex(color.lstrip("#"))) + (255,)


def crop_texture(kind, stage, spec):
    image = make_image()
    draw = ImageDraw.Draw(image)
    leaf, light, stem = map(hex_color, (spec["leaf"], spec["leaf_light"], spec["stem"]))
    fruit, fruit_dark, fruit_light = map(hex_color, (spec["fruit"], spec["fruit_dark"], spec["fruit_light"]))
    if stage == 0:
        draw.rectangle((7, 13, 8, 14), fill=stem)
        draw.rectangle((6, 13, 7, 13), fill=fruit if kind == "frostpetal_crop" else leaf)
        return image

    height = 4 + stage
    top = 15 - height
    if kind == "sakura_rice_crop":
        for x, offset in ((4, 1), (7, 0), (10, 2), (12, 1)):
            draw.line((x, 15, x, top + offset), fill=stem, width=1)
            draw.line((x, max(8, top + 3), x - 2, max(9, top + 5)), fill=leaf, width=1)
            draw.line((x, max(7, top + 5), x + 2, max(8, top + 7)), fill=light, width=1)
            if stage >= 5:
                draw.line((x, top + offset, x + 2, top + offset - 2), fill=fruit_dark, width=1)
                for grain_y in range(top + offset - 2, min(14, top + offset + 2)):
                    draw.point((x + 2, grain_y), fill=fruit_light if grain_y % 2 else fruit)
        return image

    if kind == "glowginger_crop":
        draw.line((8, 15, 8, top + 2), fill=stem, width=1)
        draw.polygon([(8, 6), (4, 2), (5, 0), (8, 3), (10, 0), (12, 1), (9, 6)], fill=leaf)
        if stage >= 3:
            draw.polygon([(8, 13), (5, 14), (4, 12), (6, 11), (7, 12), (10, 11), (12, 13), (10, 15)], fill=fruit_dark)
            draw.rectangle((6, 12, 9, 14), fill=fruit)
            draw.point((7, 12), fill=fruit_light)
            if stage >= 6:
                draw.point((11, 13), fill=fruit)
        return image

    draw.line((8, 15, 8, top + 2), fill=stem, width=1)
    if stage >= 2:
        draw.polygon([(8, 12), (4, 10), (3, 8), (6, 9), (8, 11)], fill=leaf)
        draw.point((4, 9), fill=light)
    if stage >= 3:
        draw.polygon([(8, 10), (11, 7), (14, 7), (12, 9), (9, 11)], fill=light)
        draw.point((12, 8), fill=leaf)
    if stage >= 4:
        draw.polygon([(8, 8), (5, 5), (3, 5), (5, 7), (8, 9)], fill=leaf)
        draw.point((5, 6), fill=light)
    if stage >= 5:
        draw.polygon([(8, 6), (10, 3), (13, 3), (11, 5), (9, 7)], fill=light)
    if kind == "vanilla_bean_crop":
        if stage >= 4:
            draw.line((9, 7, 12, 10), fill=fruit_dark, width=1)
        if stage >= 6:
            draw.line((10, 5, 13, 8), fill=fruit, width=1)
        if stage >= 7:
            draw.point((12, 10), fill=fruit_light)
    elif kind == "heartberry_crop":
        if stage >= 5:
            draw.rectangle((4, 11, 5, 12), fill=fruit_dark)
            draw.point((4, 11), fill=fruit)
        if stage >= 6:
            draw.rectangle((10, 9, 11, 10), fill=fruit)
            draw.point((10, 9), fill=fruit_light)
        if stage >= 7:
            draw.rectangle((6, 6, 7, 7), fill=fruit_dark)
            draw.point((6, 6), fill=fruit_light)
    elif kind == "starfruit_crop":
        if stage >= 5:
            draw.polygon([(4, 8), (5, 6), (6, 8), (8, 8), (6, 9), (6, 11), (5, 10), (3, 10)], fill=fruit_dark)
            draw.point((5, 8), fill=fruit_light)
        if stage >= 7:
            draw.polygon([(10, 5), (11, 3), (12, 5), (14, 5), (12, 6), (12, 8), (11, 7), (9, 7)], fill=fruit)
            draw.point((11, 5), fill=fruit_light)
    elif kind == "frostpetal_crop":
        if stage >= 4:
            draw.rectangle((6, 5, 9, 7), fill=fruit)
            draw.rectangle((7, 4, 8, 8), fill=fruit_light)
            draw.point((7, 6), fill=fruit_dark)
        if stage >= 7:
            draw.rectangle((11, 8, 13, 10), fill=fruit)
            draw.rectangle((12, 7, 12, 11), fill=fruit_light)
            draw.point((12, 9), fill=fruit_dark)
    elif kind == "sweetbean_crop":
        if stage >= 4:
            draw.polygon([(4, 8), (7, 7), (8, 9), (7, 12), (5, 11)], fill=fruit_dark)
            draw.point((6, 9), fill=fruit_light)
        if stage >= 6:
            draw.polygon([(10, 6), (13, 5), (14, 7), (13, 10), (11, 9)], fill=fruit)
            draw.point((12, 7), fill=fruit_light)
        if stage >= 7:
            draw.point((7, 10), fill=fruit_light)
    return image


def item_texture(name):
    image = make_image()
    draw = ImageDraw.Draw(image)
    white = hex_color("#FFF4E8")
    pink = hex_color("#F58EB0")
    dark_pink = hex_color("#C94B77")
    green = hex_color("#4E9B60")
    gold = hex_color("#F4C95D")
    dark = hex_color("#704F5F")
    brown = hex_color("#895841")
    blue = hex_color("#91D2E3")
    amber = hex_color("#D58B3D")

    if name in ("heartberry", "heartberry_seeds"):
        if name == "heartberry":
            for x, y in ((4, 6), (9, 4), (7, 10)):
                draw.polygon([(x, y + 2), (x - 2, y), (x, y - 1), (x + 1, y), (x + 3, y), (x + 2, y + 2), (x + 1, y + 4)], fill=dark_pink)
                draw.point((x, y), fill=pink)
            draw.line((6, 5, 7, 3), fill=green)
        else:
            draw.ellipse((5, 8, 10, 12), fill=brown)
            draw.point((6, 9), fill=amber)
    elif name in ("sakura_rice", "sakura_rice_seeds", "sakura_flour"):
        if name == "sakura_flour":
            draw.polygon([(3, 8), (12, 5), (14, 11), (5, 14)], fill=white)
            draw.polygon([(3, 8), (8, 10), (5, 14)], fill=hex_color("#F3DCE0"))
            draw.point((10, 8), fill=pink)
        else:
            for x, y in ((4, 6), (8, 4), (10, 8), (5, 10), (12, 12), (7, 13)):
                draw.line((x, y, x + 2, y - 1), fill=hex_color("#E9DDB7"), width=1)
                draw.point((x + 1, y - 1), fill=white)
    elif name in ("starfruit", "starfruit_seeds"):
        if name == "starfruit":
            draw.polygon([(8, 2), (10, 6), (14, 5), (11, 9), (13, 13), (8, 11), (4, 14), (5, 9), (2, 6), (6, 6)], fill=gold)
            draw.point((7, 6), fill=white)
            draw.line((9, 4, 12, 3), fill=green)
        else:
            draw.ellipse((5, 8, 10, 12), fill=amber)
            draw.point((7, 9), fill=gold)
    elif name in ("frostpetal", "frostpetal_seeds"):
        if name == "frostpetal":
            draw.rectangle((6, 3, 9, 12), fill=blue)
            draw.rectangle((3, 6, 12, 9), fill=blue)
            draw.point((4, 5), fill=white)
            draw.point((10, 5), fill=white)
            draw.rectangle((7, 7, 8, 8), fill=gold)
        else:
            draw.ellipse((5, 8, 10, 12), fill=blue)
            draw.point((7, 9), fill=white)
    elif name in ("sweetbean_pods", "sweetbean_seeds"):
        if name == "sweetbean_pods":
            draw.polygon([(3, 6), (5, 4), (12, 6), (13, 8), (11, 11), (4, 9)], fill=green)
            draw.line((5, 6, 11, 8), fill=hex_color("#C5D988"), width=1)
            for x in (6, 8, 10): draw.point((x, 7), fill=white)
        else:
            draw.ellipse((5, 8, 10, 12), fill=hex_color("#8D6C59"))
            draw.point((7, 9), fill=hex_color("#D8B98D"))
    elif name in ("glowginger", "glowginger_seeds"):
        if name == "glowginger":
            draw.polygon([(3, 9), (5, 6), (8, 8), (11, 5), (14, 7), (12, 10), (13, 13), (9, 12), (7, 14), (5, 11)], fill=amber)
            draw.point((6, 9), fill=gold)
            draw.point((11, 8), fill=hex_color("#FFE8AB"))
        else:
            draw.ellipse((5, 8, 10, 12), fill=hex_color("#906F48"))
            draw.point((7, 9), fill=gold)
    elif name in ("vanilla_bean", "vanilla_bean_seeds", "vanilla_extract"):
        if name == "vanilla_bean":
            draw.line((5, 13, 10, 3), fill=brown, width=2)
            draw.line((8, 11, 12, 5), fill=amber, width=1)
            draw.point((6, 11), fill=hex_color("#E7C48D"))
        elif name == "vanilla_extract":
            draw.rectangle((6, 5, 10, 13), fill=amber)
            draw.rectangle((7, 3, 9, 5), fill=dark)
            draw.rectangle((7, 8, 9, 10), fill=white)
        else:
            draw.ellipse((5, 8, 10, 12), fill=brown)
    elif name == "moonberry":
        violet = hex_color("#684F9D")
        lavender = hex_color("#A48AD2")
        for x, y in ((4, 8), (8, 5), (11, 8), (7, 11)):
            draw.ellipse((x - 2, y - 2, x + 1, y + 1), fill=violet)
            draw.point((x - 1, y - 1), fill=lavender)
        draw.line((7, 4, 8, 2, 10, 2), fill=green)
    elif name == "heartberry_jam":
        draw.rectangle((4, 6, 11, 13), fill=dark_pink)
        draw.rectangle((5, 7, 10, 12), fill=pink)
        draw.rectangle((5, 4, 10, 5), fill=white)
        draw.point((8, 9), fill=white)
    elif name in ("shaved_ice", "pink_shaved_ice"):
        color = pink if name == "pink_shaved_ice" else blue
        draw.polygon([(3, 11), (5, 6), (8, 4), (11, 5), (13, 10), (11, 13), (5, 13)], fill=color)
        draw.polygon([(5, 7), (8, 5), (10, 6), (8, 9)], fill=white)
        draw.point((11, 10), fill=dark_pink if name == "pink_shaved_ice" else hex_color("#6FB6CF"))
    elif name == "chocolate_syrup":
        draw.rectangle((5, 5, 11, 13), fill=brown)
        draw.rectangle((6, 3, 10, 5), fill=dark)
        draw.rectangle((6, 8, 10, 10), fill=hex_color("#D39363"))
    elif name == "sweetbean_paste":
        draw.ellipse((3, 6, 12, 13), fill=hex_color("#CA8E78"))
        draw.ellipse((5, 7, 10, 11), fill=hex_color("#E8BBA0"))
        draw.point((7, 8), fill=white)
    elif name == "maple_syrup":
        draw.rectangle((5, 5, 11, 13), fill=amber)
        draw.rectangle((6, 3, 10, 5), fill=dark)
        draw.point((7, 7), fill=gold)
        draw.line((6, 10, 10, 10), fill=hex_color("#F5D78A"))
    elif name == "petal_sugar":
        draw.polygon([(3, 12), (5, 8), (8, 6), (11, 8), (13, 12), (10, 14), (5, 14)], fill=pink)
        for x, y in ((5, 10), (8, 8), (10, 11), (7, 12)):
            draw.point((x, y), fill=white)
    else:
        draw.rectangle((4, 7, 11, 13), fill=white)
        draw.rectangle((5, 4, 10, 6), fill=hex_color("#E7C48D"))
        draw.point((8, 8), fill=pink)
    return image


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def main():
    for crop_id, spec in PLANTS.items():
        block_models = {}
        for stage in range(8):
            tex = f"{crop_id}_stage{stage}"
            block_models[f"age={stage}"] = {"model": f"uwudimension:block/{tex}"}
            (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
            crop_texture(crop_id, stage, spec).save(ASSETS / f"textures/block/{tex}.png")
            write_json(ASSETS / f"models/block/{tex}.json", {
                "parent": "minecraft:block/crop",
                "textures": {"crop": f"uwudimension:block/{tex}"},
            })
        write_json(ASSETS / f"blockstates/{crop_id}.json", {"variants": block_models})

        count = {"type": "minecraft:uniform", "min": spec["count"][0], "max": spec["count"][1]}
        write_json(DATA / f"loot_table/blocks/{crop_id}.json", {
            "type": "minecraft:block",
            "pools": [
                {"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": f"uwudimension:{spec['seed']}"}]},
                {
                    "rolls": 1.0,
                    "conditions": [{
                        "condition": "minecraft:block_state_property",
                        "block": f"uwudimension:{crop_id}",
                        "properties": {"age": "7"},
                    }],
                    "entries": [{
                        "type": "minecraft:item",
                        "name": f"uwudimension:{spec['harvest']}",
                        "functions": [{"function": "minecraft:set_count", "count": count}],
                    }],
                },
            ],
        })

    item_ids = set(ITEM_NAMES)
    seed_ids = {spec["seed"] for spec in PLANTS.values()}
    item_ids.update(seed_ids)
    for item_id in sorted(item_ids):
        (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
        item_texture(item_id).save(ASSETS / f"textures/item/{item_id}.png")
        write_json(ASSETS / f"models/item/{item_id}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"uwudimension:item/{item_id}"},
        })

    block_names = {
        "heartberry_crop": "Heartberry Vine",
        "sakura_rice_crop": "Sakura Rice Sprouts",
        "starfruit_crop": "Starfruit Stalk",
        "frostpetal_crop": "Frostpetal Patch",
        "sweetbean_crop": "Sweetbean Shoots",
        "glowginger_crop": "Glowginger Root",
        "vanilla_bean_crop": "Vanilla Blossom Vine",
    }
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for crop_id, name in block_names.items():
        lang[f"block.uwudimension.{crop_id}"] = name
    for crop_id, spec in PLANTS.items():
        lang[f"item.uwudimension.{spec['seed']}"] = " ".join(w.capitalize() for w in spec["seed"].split("_"))
    for item_id, name in ITEM_NAMES.items():
        lang[f"item.uwudimension.{item_id}"] = name
    lang_path.write_text(json.dumps(lang, indent=2) + "\n", encoding="utf-8")

    cols, scale, cell_width, cell_height = 4, 3, 104, 72
    rows = (len(item_ids) + cols - 1) // cols
    preview = Image.new("RGBA", (cols * cell_width, rows * cell_height), hex_color("#2B2233"))
    draw = ImageDraw.Draw(preview)
    ordered = sorted(item_ids)
    for index, item_id in enumerate(ordered):
        x = (index % cols) * cell_width + 28
        y = (index // cols) * cell_height + 1
        icon = Image.open(ASSETS / f"textures/item/{item_id}.png")
        preview.alpha_composite(icon.resize((16 * scale, 16 * scale), Image.Resampling.NEAREST), (x, y))
        label = item_id.replace("_", " ")[:15]
        draw.text(((index % cols) * cell_width + 3, (index // cols) * cell_height + 54), label, fill=hex_color("#F6E8F4"))
    preview_path = ROOT / "docs/art/uwu_dimension_ingredients_preview.png"
    preview_path.parent.mkdir(parents=True, exist_ok=True)
    preview.save(preview_path)

    crop_preview = Image.new("RGBA", (4 * 104, 2 * 104), hex_color("#2B2233"))
    crop_draw = ImageDraw.Draw(crop_preview)
    for index, (crop_id, spec) in enumerate(PLANTS.items()):
        x = (index % 4) * 104 + 12
        y = (index // 4) * 104 + 1
        icon = crop_texture(crop_id, 7, spec)
        crop_preview.alpha_composite(icon.resize((80, 80), Image.Resampling.NEAREST), (x, y))
        label = crop_id.removesuffix("_crop").replace("_", " ")[:15]
        crop_draw.text(((index % 4) * 104 + 3, (index // 4) * 104 + 84), label, fill=hex_color("#F6E8F4"))
    crop_preview_path = ROOT / "docs/art/uwu_dimension_crop_preview.png"
    crop_preview.save(crop_preview_path)
    print(f"Generated {len(PLANTS)} dimension crops, {len(item_ids)} ingredient/seed textures, block models, loot tables, and translations.")


if __name__ == "__main__":
    main()
