import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TABLES = ("rose_bathhouse", "tide_archive", "mirror_court", "inverted_shrine")
FOOD = (
    "sweet_berry_cheesecake_slice",
    "apple_pie_slice",
    "uwu_pancake_stack",
    "pink_melon_popsicle",
    "pink_fruit_salad",
    "sweet_berry_cookie",
    "blush_cabbage_roll",
    "strawberry_milk",
    "chorus_fruit_pie_slice",
    "chorus_blossom_tea",
    "dragon_breath_soda",
    "shulker_soup",
    "strider_moss_stew",
    "nether_fungus_skewer",
    "stuffed_hoglin_roast",
    "magma_gelatin",
)
OLD_FOOD = (
    "peach_blossom_mochi",
    "strawberry_cream_cake",
    "heart_cookie",
    "custard_bun",
    "pink_starfruit",
    "moonberry",
    "sunberry",
    "lavender_dewberry",
    "aqua_cherries",
    "petal_jam",
    "fairy_dew",
    "blossom_lantern_charm",
    "petal_parasol",
    "shrine_bell_charm",
    "seashell_picnic_basket",
)


def is_old_pantry_entry(entry: dict) -> bool:
    return entry.get("name") in {f"uwudimension:{item}" for item in (*OLD_FOOD, *FOOD)}


def main() -> None:
    folder = ROOT / "src" / "main" / "resources" / "data" / "uwudimension" / "loot_table" / "chests"
    for table_name in TABLES:
        path = folder / f"{table_name}.json"
        table = json.loads(path.read_text(encoding="utf-8"))
        pools = []
        for pool in table.get("pools", []):
            pool["entries"] = [entry for entry in pool.get("entries", []) if not is_old_pantry_entry(entry)]
            if pool["entries"]:
                pools.append(pool)

        pools.append(
            {
                "rolls": 1,
                "conditions": [
                    {"condition": "minecraft:random_chance", "chance": 0.35}
                ],
                "entries": [
                    {"type": "minecraft:item", "name": f"uwudimension:{item}", "weight": 1}
                    for item in FOOD
                ],
            }
        )
        table["pools"] = pools
        path.write_text(json.dumps(table, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
