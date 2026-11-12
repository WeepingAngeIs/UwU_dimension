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
    growth = stage / 7

    def blade(points, base=leaf, highlight=None):
        draw.polygon(points, fill=base)
        if highlight:
            draw.line(highlight[0], fill=light, width=1)

    def heart(x, y):
        draw.polygon([(x, y + 2), (x - 2, y), (x - 1, y - 2), (x, y - 1),
                      (x + 1, y - 2), (x + 2, y), (x + 1, y + 2)], fill=fruit_dark)
        draw.point((x, y - 1), fill=fruit_light)

    def star(x, y):
        draw.polygon([(x, y - 3), (x + 1, y - 1), (x + 3, y - 1), (x + 1, y + 1),
                      (x + 2, y + 3), (x, y + 2), (x - 2, y + 3), (x - 1, y + 1),
                      (x - 3, y - 1), (x - 1, y - 1)], fill=fruit_dark)
        draw.point((x, y), fill=fruit_light)

    # Each crop has its own silhouette and harvest details instead of a shared stalk shape.
    if kind == "heartberry_crop":
        crown = 13 - int(growth * 7)
        draw.line((8, 15, 8, crown + 1), fill=stem)
        if stage == 0:
            blade([(8, 14), (5, 13), (4, 11), (7, 12), (9, 14)], leaf, [(5, 12, 8, 13)])
            blade([(8, 14), (10, 12), (12, 11), (11, 13), (9, 14)], light, [(10, 13, 12, 12)])
        if stage >= 1:
            blade([(8, 14), (5, 13), (3, 10), (6, 10), (9, 12)], leaf, [(5, 11, 8, 13)])
            blade([(8, 13), (10, 10), (13, 9), (12, 12), (9, 14)], light, [(10, 12, 12, 10)])
        if stage >= 3:
            blade([(8, 11), (5, 9), (4, 6), (7, 7), (9, 10)], light, [(5, 7, 8, 10)])
            blade([(8, 10), (10, 7), (13, 6), (12, 9), (9, 11)], leaf, [(10, 8, 12, 7)])
        if stage >= 5:
            heart(4, 11)
        if stage >= 6:
            heart(12, 9)
        if stage >= 7:
            heart(8, 6)
        draw.point((8, 15), fill=stem)
    elif kind == "sakura_rice_crop":
        height = 3 + stage * 1.55
        tops = [(4, 1), (7, 0), (10, 2), (12, 1)]
        for index, (x, offset) in enumerate(tops):
            top = max(2, int(15 - height + offset))
            draw.line((x, 15, x, top), fill=stem if index % 2 else leaf)
            if stage == 0 and index == 1:
                blade([(7, 14), (5, 13), (5, 11), (7, 12), (8, 14)], leaf, [(6, 12, 8, 13)])
                blade([(8, 14), (9, 12), (11, 11), (10, 13)], light, [(9, 12, 10, 13)])
            if stage >= 1:
                blade([(x, 14), (x - 2, 12), (x - 3, 9 - min(stage // 3, 2)),
                       (x - 1, 10), (x + 1, 13)], leaf, [(x - 2, 11, x, 13)])
            if stage >= 2:
                blade([(x, 13), (x + 2, 11), (x + 3, 8 - min(stage // 4, 2)),
                       (x + 3, 10), (x + 1, 14)], light, [(x + 2, 10, x + 1, 12)])
            if stage >= 5 and index in (0, 1, 3):
                head_y = max(3, top + 1)
                draw.line((x, top + 1, x + 2, head_y + 2), fill=fruit_dark)
                for grain_x, grain_y in ((x + 1, head_y), (x + 2, head_y + 1),
                                         (x + 3, head_y + 2), (x + 2, head_y + 3)):
                    if 0 <= grain_x < 16 and 0 <= grain_y < 16:
                        draw.point((grain_x, grain_y), fill=fruit_light if grain_y % 2 else fruit)
        draw.line((5, 15, 11, 15), fill=stem)
    elif kind == "starfruit_crop":
        top = 13 - int(growth * 9)
        draw.line((8, 15, 8, top + 2), fill=stem, width=1)
        if stage == 0:
            blade([(8, 14), (5, 13), (4, 11), (7, 12), (9, 14)], leaf, [(5, 12, 8, 13)])
            blade([(8, 14), (10, 12), (12, 11), (11, 13)], light, [(10, 12, 11, 13)])
        if stage >= 1:
            blade([(8, 14), (5, 12), (3, 8), (6, 9), (9, 13)], leaf, [(5, 10, 8, 13)])
        if stage >= 2:
            blade([(8, 13), (10, 10), (14, 8), (13, 11), (9, 14)], light, [(10, 11, 13, 9)])
        if stage >= 3:
            blade([(8, 10), (5, 8), (4, 5), (7, 6), (9, 10)], light, [(5, 6, 8, 9)])
        if stage >= 4:
            blade([(8, 9), (10, 6), (13, 4), (12, 8), (9, 10)], leaf, [(10, 7, 12, 5)])
        if stage >= 5:
            star(4, 8)
        if stage >= 7:
            star(12, 5)
        draw.point((8, 15), fill=stem)
    elif kind == "frostpetal_crop":
        # A low icy rosette stays visibly different from the tall grains and vines.
        if stage < 2:
            blade([(8, 14), (6, 13), (5, 11), (8, 12), (9, 14)], leaf, [(6, 12, 8, 13)])
            blade([(8, 14), (10, 12), (12, 11), (11, 13)], light, [(10, 12, 11, 13)])
        else:
            blade([(8, 14), (5, 13), (2, 10), (6, 10), (8, 12)], leaf, [(4, 11, 7, 13)])
            blade([(8, 13), (10, 10), (14, 9), (12, 12), (9, 14)], light, [(10, 12, 13, 10)])
        if stage >= 2:
            blade([(7, 12), (5, 9), (5, 6), (8, 8), (9, 12)], light, [(6, 8, 8, 11)])
            blade([(8, 12), (10, 8), (12, 6), (12, 10), (9, 13)], leaf, [(10, 9, 11, 11)])
        if stage >= 4:
            draw.line((8, 10, 8, 6), fill=stem)
            draw.rectangle((6, 5, 10, 7), fill=fruit)
            draw.rectangle((7, 4, 9, 8), fill=fruit_light)
            draw.point((8, 6), fill=fruit_dark)
        if stage >= 7:
            draw.line((12, 12, 12, 9), fill=stem)
            draw.rectangle((10, 8, 14, 10), fill=fruit)
            draw.rectangle((11, 7, 13, 11), fill=fruit_light)
            draw.point((12, 9), fill=fruit_dark)
    elif kind == "sweetbean_crop":
        draw.line((8, 15, 8, 14 - int(growth * 5)), fill=stem)
        if stage == 0:
            blade([(8, 14), (5, 13), (4, 11), (7, 12), (9, 14)], leaf, [(5, 12, 8, 13)])
            blade([(8, 14), (10, 12), (12, 11), (11, 13)], light, [(10, 12, 11, 13)])
        if stage >= 1:
            blade([(8, 14), (4, 12), (3, 9), (6, 9), (9, 12)], leaf, [(4, 10, 8, 13)])
            blade([(8, 13), (10, 10), (14, 9), (12, 12), (9, 14)], light, [(10, 11, 13, 10)])
        if stage >= 3:
            blade([(8, 10), (5, 8), (4, 5), (7, 6), (9, 10)], light, [(5, 6, 8, 9)])
            blade([(8, 10), (10, 7), (13, 5), (12, 8), (9, 11)], leaf, [(10, 8, 12, 6)])
        if stage >= 4:
            draw.polygon([(3, 8), (5, 7), (7, 9), (7, 12), (5, 13), (4, 10)], fill=fruit_dark)
            draw.line((4, 9, 5, 12), fill=fruit_light)
        if stage >= 6:
            draw.polygon([(10, 6), (12, 5), (14, 6), (14, 9), (12, 11), (10, 9)], fill=fruit)
            draw.line((11, 7, 12, 10), fill=fruit_light)
        if stage >= 7:
            draw.point((7, 11), fill=fruit_light)
    elif kind == "glowginger_crop":
        # Broad ginger blades fan upward; a warm flower marks a mature, harvestable plant.
        if stage == 0:
            blade([(8, 15), (6, 13), (6, 11), (8, 12), (9, 15)], leaf, [(7, 12, 8, 14)])
        else:
            blade([(8, 15), (5, 10), (4, 4), (7, 7), (9, 14)], leaf, [(6, 8, 8, 13)])
        if stage >= 1:
            blade([(8, 15), (8, 8), (8, 2), (10, 6), (9, 14)], light, [(8, 5, 9, 11)])
        if stage >= 2:
            blade([(8, 15), (11, 10), (13, 4), (13, 9), (10, 15)], leaf, [(11, 9, 12, 12)])
        if stage >= 4:
            blade([(8, 13), (3, 9), (2, 6), (6, 8), (9, 12)], light, [(4, 8, 8, 12)])
        if stage >= 6:
            draw.line((8, 5, 8, 2), fill=stem)
            draw.rectangle((6, 1, 10, 3), fill=fruit_dark)
            draw.point((7, 1), fill=fruit_light)
            draw.point((9, 2), fill=fruit)
        if stage >= 7:
            draw.point((8, 15), fill=fruit_dark)
            draw.point((7, 14), fill=fruit)
            draw.point((9, 14), fill=fruit_light)
    elif kind == "vanilla_bean_crop":
        tip_x, tip_y = 5 + int(growth * 7), 15 - int(growth * 11)
        draw.line((5, 15, tip_x, tip_y), fill=stem)
        if stage == 0:
            blade([(5, 14), (3, 13), (3, 11), (5, 12), (6, 14)], leaf, [(4, 12, 5, 13)])
        if stage >= 1:
            blade([(7, 12), (4, 10), (3, 7), (6, 8), (8, 11)], light, [(4, 8, 7, 11)])
        if stage >= 2:
            blade([(8, 9), (10, 7), (13, 7), (12, 9), (9, 11)], leaf, [(10, 8, 12, 8)])
        if stage >= 3:
            blade([(10, 6), (8, 4), (8, 2), (11, 3), (12, 5)], light, [(9, 3, 11, 5)])
        if stage >= 5:
            draw.point((12, 6), fill=fruit_light)
            draw.point((13, 5), fill=fruit)
            draw.point((12, 4), fill=fruit_light)
        if stage >= 6:
            draw.line((9, 9, 10, 12, 9, 14), fill=fruit_dark)
            draw.line((10, 10, 11, 12), fill=fruit)
        if stage >= 7:
            draw.point((9, 13), fill=fruit_light)
            draw.point((11, 12), fill=fruit_light)
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
                "render_type": "minecraft:cutout",
                "ambientocclusion": False,
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

    row_height = 62
    crop_preview = Image.new("RGBA", (270, 24 + len(PLANTS) * row_height), hex_color("#2B2233"))
    crop_draw = ImageDraw.Draw(crop_preview)
    for x, heading in ((72, "Sprout"), (143, "Growing"), (213, "Ripe")):
        crop_draw.text((x, 4), heading, fill=hex_color("#F6E8F4"))
    for index, (crop_id, spec) in enumerate(PLANTS.items()):
        y = 24 + index * row_height
        label = crop_id.removesuffix("_crop").replace("_", " ")[:10]
        crop_draw.text((3, y + 21), label, fill=hex_color("#F6E8F4"))
        for column, stage in enumerate((0, 3, 7)):
            icon = crop_texture(crop_id, stage, spec)
            crop_preview.alpha_composite(icon.resize((48, 48), Image.Resampling.NEAREST), (68 + column * 67, y))
    crop_preview_path = ROOT / "docs/art/uwu_dimension_crop_preview.png"
    crop_preview.save(crop_preview_path)
    print(f"Generated {len(PLANTS)} dimension crops, {len(item_ids)} ingredient/seed textures, block models, loot tables, and translations.")


if __name__ == "__main__":
    main()
