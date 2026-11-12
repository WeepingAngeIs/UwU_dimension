from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ATLAS = ROOT / "docs" / "art" / "uwu_pantry_sprite_atlas.png"
OUTPUT = ROOT / "src" / "main" / "resources" / "assets" / "uwudimension" / "textures" / "item"
MODELS = ROOT / "src" / "main" / "resources" / "assets" / "uwudimension" / "models" / "item"

SPRITES = (
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
    "blossom_lantern_charm",
    "petal_parasol",
    "shrine_bell_charm",
    "seashell_picnic_basket",
)


def main() -> None:
    atlas = Image.open(ATLAS).convert("RGBA")
    width, height = atlas.size
    x_edges = [round(index * width / 4) for index in range(5)]
    y_edges = [round(index * height / 4) for index in range(5)]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)

    for index, name in enumerate(SPRITES):
        row, column = divmod(index, 4)
        sprite = atlas.crop((x_edges[column], y_edges[row], x_edges[column + 1], y_edges[row + 1]))
        alpha = sprite.getchannel("A")
        rgb = sprite.convert("RGB").quantize(
            colors=32,
            method=Image.Quantize.MEDIANCUT,
            dither=Image.Dither.NONE,
        ).convert("RGB")
        pixel_art = rgb.resize((32, 32), Image.Resampling.NEAREST).convert("RGBA")
        reduced_alpha = alpha.resize((32, 32), Image.Resampling.NEAREST).point(
            lambda value: 255 if value >= 144 else 0
        )
        pixel_art.putalpha(reduced_alpha)
        pixel_art.save(OUTPUT / f"{name}.png", optimize=True)
        model = (
            '{\n'
            '  "parent": "minecraft:item/generated",\n'
            '  "textures": {\n'
            f'    "layer0": "uwudimension:item/{name}"\n'
            '  }\n'
            '}\n'
        )
        (MODELS / f"{name}.json").write_text(model, encoding="utf-8")


if __name__ == "__main__":
    main()
