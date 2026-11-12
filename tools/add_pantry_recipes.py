import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECIPES = {
    "sweet_berry_cheesecake_slice": ["uwudimension:sakura_flour", "milk_bucket", "uwudimension:heartberry", "sugar", "egg"],
    "apple_pie_slice": ["uwudimension:sakura_flour", "apple", "sugar", "egg"],
    "uwu_pancake_stack": ["uwudimension:sakura_flour", "egg", "milk_bucket", "sugar", "uwudimension:starfruit"],
    "pink_melon_popsicle": ["uwudimension:starfruit", "uwudimension:pink_shaved_ice", "sugar", "uwudimension:heartberry"],
    "pink_fruit_salad": ["apple", "uwudimension:starfruit", "uwudimension:heartberry", "uwudimension:moonberry", "bowl"],
    "sweet_berry_cookie": ["uwudimension:sakura_flour", "sugar", "uwudimension:heartberry"],
    "blush_cabbage_roll": ["beetroot", "carrot", "cooked_porkchop", "uwudimension:sakura_rice", "bowl"],
    "strawberry_milk": ["milk_bucket", "uwudimension:heartberry", "sugar", "glass_bottle"],
    "chorus_fruit_pie_slice": ["uwudimension:sakura_flour", "chorus_fruit", "egg", "sugar"],
    "chorus_blossom_tea": ["uwudimension:frostpetal", "sugar", "glass_bottle", "pink_petals"],
    "dragon_breath_soda": ["chorus_fruit", "glow_berries", "sugar", "glass_bottle", "uwudimension:glowginger"],
    "shulker_soup": ["bowl", "chorus_fruit", "brown_mushroom", "beetroot", "uwudimension:sakura_rice"],
    "strider_moss_stew": ["bowl", "cooked_porkchop", "warped_fungus", "kelp", "uwudimension:glowginger"],
    "nether_fungus_skewer": ["stick", "cooked_porkchop", "crimson_fungus", "uwudimension:glowginger"],
    "stuffed_hoglin_roast": ["cooked_porkchop", "cooked_porkchop", "potato", "carrot", "uwudimension:sakura_rice", "pink_petals"],
    "magma_gelatin": ["magma_cream", "sugar", "uwudimension:heartberry", "honey_bottle"],
    "blossom_ice_cream_bucket": ["milk_bucket", "uwudimension:pink_shaved_ice", "sugar", "uwudimension:heartberry_jam", "uwudimension:vanilla_extract"],
    "strawberry_ice_cream_cake": ["uwudimension:sakura_flour", "egg", "milk_bucket", "uwudimension:pink_shaved_ice", "uwudimension:heartberry", "sugar"],
    "flower_stuffed_chicken": ["cooked_chicken", "potato", "carrot", "beetroot", "pink_petals", "uwudimension:sakura_rice"],
    "heartberry_burger": ["bread", "cooked_beef", "beetroot", "uwudimension:heartberry_jam", "uwudimension:starfruit"],
    "blossom_hot_pocket": ["uwudimension:sakura_flour", "cooked_chicken", "potato", "egg", "uwudimension:heartberry_jam"],
    "uwu_ramen_bowl": ["bowl", "uwudimension:sakura_flour", "cooked_porkchop", "egg", "kelp", "uwudimension:sakura_rice", "uwudimension:glowginger"],
    "cherry_picnic_bento": ["cooked_salmon", "dried_kelp", "uwudimension:heartberry", "baked_potato", "carrot", "uwudimension:sakura_rice"],
    "berry_cinnamon_roll": ["uwudimension:sakura_flour", "sugar", "milk_bucket", "uwudimension:chocolate_syrup", "uwudimension:heartberry_jam"],
    "starberry_waffle_stack": ["uwudimension:sakura_flour", "egg", "sugar", "milk_bucket", "uwudimension:heartberry", "uwudimension:petal_sugar"],
    "creamy_mushroom_stew": ["bowl", "brown_mushroom", "red_mushroom", "milk_bucket", "potato"],
    "loaded_heart_potato": ["baked_potato", "carrot", "beetroot", "milk_bucket", "pink_petals"],
    "rose_berry_pie_slice": ["uwudimension:sakura_flour", "uwudimension:heartberry", "egg", "honey_bottle", "sugar", "pink_petals"],
    "pastel_sakura_dumplings": ["uwudimension:sakura_flour", "cooked_porkchop", "beetroot", "kelp", "pink_petals"],
    "honey_glazed_ham": ["cooked_porkchop", "honey_bottle", "carrot", "uwudimension:heartberry", "pink_petals"],
    "moonberry_pudding": ["milk_bucket", "uwudimension:shaved_ice", "uwudimension:moonberry", "sugar", "bowl"],
    "chicken_flower_pot_pie": ["cooked_chicken", "uwudimension:sakura_flour", "egg", "potato", "carrot", "brown_mushroom"],
    "sakura_katsu_curry": ["cooked_porkchop", "uwudimension:sakura_rice", "carrot", "brown_mushroom", "beetroot", "uwudimension:glowginger"],
    "heart_omurice": ["egg", "uwudimension:sakura_rice", "cooked_chicken", "beetroot", "bowl", "uwudimension:heartberry_jam"],
    "cherry_onigiri_bento": ["dried_kelp", "cooked_cod", "cooked_salmon", "baked_potato", "carrot", "uwudimension:sakura_rice", "uwudimension:heartberry"],
    "golden_taiyaki": ["uwudimension:sakura_flour", "uwudimension:sweetbean_paste", "sugar", "egg"],
    "cozy_udon_hotpot": ["bowl", "uwudimension:sakura_flour", "cooked_porkchop", "egg", "kelp", "brown_mushroom", "uwudimension:glowginger"],
    "sakura_melonpan": ["uwudimension:sakura_flour", "sugar", "egg", "uwudimension:starfruit", "uwudimension:petal_sugar"],
    "strawberry_shokupan": ["bread", "uwudimension:sakura_flour", "milk_bucket", "uwudimension:heartberry_jam", "sugar"],
    "sweet_bean_dorayaki": ["uwudimension:sakura_flour", "egg", "sugar", "uwudimension:sweetbean_paste", "honey_bottle"],
    "sakura_mochi_ice_cream": ["uwudimension:pink_shaved_ice", "sugar", "uwudimension:heartberry", "uwudimension:sakura_rice", "uwudimension:vanilla_extract"],
    "maple_ice_cream_sundae": ["milk_bucket", "uwudimension:shaved_ice", "sugar", "uwudimension:maple_syrup", "bowl", "uwudimension:vanilla_extract"],
    "maple_poutine": ["baked_potato", "cooked_beef", "brown_mushroom", "uwudimension:maple_syrup", "uwudimension:sakura_flour"],
    "tourtiere_hand_pie": ["cooked_porkchop", "cooked_beef", "uwudimension:sakura_flour", "potato", "carrot", "egg"],
    "nanaimo_heart_bar": ["cocoa_beans", "sugar", "honey_bottle", "uwudimension:sakura_flour", "uwudimension:chocolate_syrup", "uwudimension:vanilla_extract"],
    "maple_butter_tart": ["uwudimension:sakura_flour", "sugar", "egg", "uwudimension:maple_syrup", "uwudimension:heartberry"],
    "saskatoon_berry_pie_slice": ["uwudimension:sakura_flour", "uwudimension:heartberry", "sugar", "egg", "pink_petals"],
    "maple_glazed_salmon_bowl": ["cooked_salmon", "uwudimension:maple_syrup", "bowl", "carrot", "beetroot", "uwudimension:sakura_rice", "uwudimension:glowginger"],
}

INGREDIENT_RECIPES = {
    "sakura_flour": ["uwudimension:sakura_rice", "uwudimension:sakura_rice"],
    "heartberry_jam": ["uwudimension:heartberry", "uwudimension:heartberry", "minecraft:sugar"],
    "shaved_ice": ["minecraft:snowball", "minecraft:snowball"],
    "pink_shaved_ice": ["uwudimension:shaved_ice", "minecraft:pink_dye"],
    "chocolate_syrup": ["minecraft:cocoa_beans", "minecraft:sugar", "minecraft:milk_bucket"],
    "sweetbean_paste": ["uwudimension:sweetbean_pods", "minecraft:sugar"],
    "maple_syrup": ["minecraft:honey_bottle", "minecraft:sugar"],
    "petal_sugar": ["minecraft:sugar", "minecraft:pink_petals"],
    "vanilla_extract": ["uwudimension:vanilla_bean", "minecraft:sugar"],
}


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + chr(10), encoding="utf-8")


def item_id(ingredient: str) -> str:
    return ingredient if ":" in ingredient else f"minecraft:{ingredient}"


def main() -> None:
    recipes = ROOT / "src/main/resources/data/uwudimension/recipe"
    advancements = ROOT / "src/main/resources/data/uwudimension/advancement/recipes/misc"
    for output, ingredients in {**RECIPES, **INGREDIENT_RECIPES}.items():
        recipe_id = f"uwudimension:{output}"
        recipe = {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": item_id(ingredient)} for ingredient in ingredients],
            "result": {"id": recipe_id, "count": 1},
        }
        write_json(recipes / f"{output}.json", recipe)
        advancement = {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_ingredient": {
                    "trigger": "minecraft:inventory_changed",
                    "conditions": {"items": [{"items": item_id(ingredients[0])}]},
                },
                "has_the_recipe": {
                    "trigger": "minecraft:recipe_unlocked",
                    "conditions": {"recipe": recipe_id},
                },
            },
            "requirements": [["has_ingredient", "has_the_recipe"]],
            "rewards": {"recipes": [recipe_id]},
        }
        write_json(advancements / f"{output}.json", advancement)


if __name__ == "__main__":
    main()
