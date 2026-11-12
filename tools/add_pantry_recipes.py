import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECIPES = {
    "sweet_berry_cheesecake_slice": ["wheat", "milk_bucket", "sweet_berries", "sugar", "egg"],
    "apple_pie_slice": ["wheat", "apple", "sugar", "egg"],
    "uwu_pancake_stack": ["wheat", "egg", "milk_bucket", "sugar", "pink_petals"],
    "pink_melon_popsicle": ["melon_slice", "snowball", "sugar", "sweet_berries"],
    "pink_fruit_salad": ["apple", "melon_slice", "sweet_berries", "glow_berries", "bowl"],
    "sweet_berry_cookie": ["wheat", "sugar", "sweet_berries"],
    "blush_cabbage_roll": ["beetroot", "carrot", "cooked_porkchop", "kelp", "bowl"],
    "strawberry_milk": ["milk_bucket", "sweet_berries", "sugar", "glass_bottle"],
    "chorus_fruit_pie_slice": ["wheat", "chorus_fruit", "egg", "sugar"],
    "chorus_blossom_tea": ["chorus_flower", "sugar", "glass_bottle", "pink_petals"],
    "dragon_breath_soda": ["chorus_fruit", "glow_berries", "sugar", "glass_bottle", "fermented_spider_eye"],
    "shulker_soup": ["bowl", "chorus_fruit", "brown_mushroom", "beetroot", "purple_dye"],
    "strider_moss_stew": ["bowl", "cooked_porkchop", "warped_fungus", "kelp", "brown_mushroom"],
    "nether_fungus_skewer": ["stick", "cooked_porkchop", "crimson_fungus", "warped_fungus"],
    "stuffed_hoglin_roast": ["cooked_porkchop", "cooked_porkchop", "potato", "carrot", "wheat", "pink_petals"],
    "magma_gelatin": ["magma_cream", "sugar", "sweet_berries", "honey_bottle"],
    "blossom_ice_cream_bucket": ["milk_bucket", "snowball", "sugar", "sweet_berries", "pink_petals"],
    "strawberry_ice_cream_cake": ["wheat", "egg", "milk_bucket", "snowball", "sweet_berries", "sugar"],
    "flower_stuffed_chicken": ["cooked_chicken", "potato", "carrot", "beetroot", "pink_petals", "wheat"],
    "heartberry_burger": ["bread", "cooked_beef", "beetroot", "sweet_berries", "milk_bucket"],
    "blossom_hot_pocket": ["wheat", "cooked_chicken", "potato", "egg", "sweet_berries"],
    "uwu_ramen_bowl": ["bowl", "wheat", "cooked_porkchop", "egg", "kelp", "brown_mushroom"],
    "cherry_picnic_bento": ["cooked_salmon", "dried_kelp", "sweet_berries", "baked_potato", "carrot", "pink_petals"],
    "berry_cinnamon_roll": ["wheat", "sugar", "milk_bucket", "cocoa_beans", "sweet_berries"],
    "starberry_waffle_stack": ["wheat", "egg", "sugar", "milk_bucket", "sweet_berries", "pink_petals"],
    "creamy_mushroom_stew": ["bowl", "brown_mushroom", "red_mushroom", "milk_bucket", "potato"],
    "loaded_heart_potato": ["baked_potato", "carrot", "beetroot", "milk_bucket", "pink_petals"],
    "rose_berry_pie_slice": ["wheat", "sweet_berries", "egg", "honey_bottle", "sugar", "pink_petals"],
    "pastel_sakura_dumplings": ["wheat", "cooked_porkchop", "beetroot", "kelp", "pink_petals"],
    "honey_glazed_ham": ["cooked_porkchop", "honey_bottle", "carrot", "sweet_berries", "pink_petals"],
    "moonberry_pudding": ["milk_bucket", "snowball", "sweet_berries", "sugar", "bowl"],
    "chicken_flower_pot_pie": ["cooked_chicken", "wheat", "egg", "potato", "carrot", "brown_mushroom"],
    "sakura_katsu_curry": ["cooked_porkchop", "baked_potato", "carrot", "brown_mushroom", "beetroot", "bowl"],
    "heart_omurice": ["egg", "baked_potato", "cooked_chicken", "beetroot", "bowl", "sweet_berries"],
    "cherry_onigiri_bento": ["dried_kelp", "cooked_cod", "cooked_salmon", "baked_potato", "carrot", "sweet_berries"],
    "golden_taiyaki": ["wheat", "sweet_berries", "sugar", "egg"],
    "cozy_udon_hotpot": ["bowl", "wheat", "cooked_porkchop", "egg", "kelp", "brown_mushroom"],
    "sakura_melonpan": ["wheat", "sugar", "egg", "melon_slice", "pink_petals"],
    "strawberry_shokupan": ["bread", "wheat", "milk_bucket", "sweet_berries", "sugar"],
    "sweet_bean_dorayaki": ["wheat", "egg", "sugar", "sweet_berries", "honey_bottle"],
    "sakura_mochi_ice_cream": ["snowball", "sugar", "sweet_berries", "wheat", "pink_petals"],
    "maple_ice_cream_sundae": ["milk_bucket", "snowball", "sugar", "honey_bottle", "bowl", "pink_petals"],
    "maple_poutine": ["baked_potato", "cooked_beef", "brown_mushroom", "milk_bucket", "carrot"],
    "tourtiere_hand_pie": ["cooked_porkchop", "cooked_beef", "wheat", "potato", "carrot", "egg"],
    "nanaimo_heart_bar": ["cocoa_beans", "sugar", "honey_bottle", "wheat", "milk_bucket"],
    "maple_butter_tart": ["wheat", "sugar", "egg", "honey_bottle", "sweet_berries"],
    "saskatoon_berry_pie_slice": ["wheat", "sweet_berries", "sugar", "egg", "pink_petals"],
    "maple_glazed_salmon_bowl": ["cooked_salmon", "honey_bottle", "bowl", "carrot", "beetroot", "kelp"],
}


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + chr(10), encoding="utf-8")


def main() -> None:
    recipes = ROOT / "src/main/resources/data/uwudimension/recipe"
    advancements = ROOT / "src/main/resources/data/uwudimension/advancement/recipes/misc"
    for food, ingredients in RECIPES.items():
        recipe_id = f"uwudimension:{food}"
        recipe = {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": f"minecraft:{ingredient}"} for ingredient in ingredients],
            "result": {"id": recipe_id, "count": 1},
        }
        write_json(recipes / f"{food}.json", recipe)
        advancement = {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_ingredient": {
                    "trigger": "minecraft:inventory_changed",
                    "conditions": {"items": [{"items": f"minecraft:{ingredients[0]}"}]},
                },
                "has_the_recipe": {
                    "trigger": "minecraft:recipe_unlocked",
                    "conditions": {"recipe": recipe_id},
                },
            },
            "requirements": [["has_ingredient", "has_the_recipe"]],
            "rewards": {"recipes": [recipe_id]},
        }
        write_json(advancements / f"{food}.json", advancement)


if __name__ == "__main__":
    main()
