import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TABLES = (
    "rose_bathhouse",
    "tide_archive",
    "mirror_court",
    "inverted_shrine",
)
FOOD = (
    "peach_blossom_mochi",
    "strawberry_cream_cake",
    "heart_cookie",
    "strawberry_milk",
    "custard_bun",
    "pink_starfruit",
    "moonberry",
    "sunberry",
    "lavender_dewberry",
    "aqua_cherries",
    "petal_jam",
    "fairy_dew",
)
KEEPSAKES = (
    "blossom_lantern_charm",
    "petal_parasol",
    "shrine_bell_charm",
    "seashell_picnic_basket",
)


def main() -> None:
    folder = ROOT / "src" / "main" / "resources" / "data" / "uwudimension" / "loot_table" / "chests"
    for table_name in TABLES:
        path = folder / f"{table_name}.json"
        table = json.loads(path.read_text(encoding="utf-8"))
        existing = {
            entry.get("name")
            for pool in table.get("pools", [])
            for entry in pool.get("entries", [])
        }
        items = tuple((name, 4) for name in FOOD) + tuple((name, 3) for name in KEEPSAKES)
        entries = [
            {"type": "minecraft:item", "name": f"uwudimension:{name}", "weight": weight}
            for name, weight in items
            if f"uwudimension:{name}" not in existing
        ]
        if entries:
            table.setdefault("pools", []).append(
                {
                    "rolls": 1,
                    "conditions": [
                        {"condition": "minecraft:random_chance", "chance": 0.35}
                    ],
                    "entries": entries,
                }
            )
            path.write_text(json.dumps(table, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
