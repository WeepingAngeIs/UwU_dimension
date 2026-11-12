from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "docs" / "art"
ATLAS = ART / "uwu_pantry_sprite_atlas.png"
OUTPUT = ROOT / "src" / "main" / "resources" / "assets" / "uwudimension" / "textures" / "item"
MODELS = ROOT / "src" / "main" / "resources" / "assets" / "uwudimension" / "models" / "item"
PREVIEW = ART / "uwu_pantry_textures_preview.png"

SPRITES = (
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


def main() -> None:
    atlas = Image.open(ATLAS).convert("RGBA")
    width, height = atlas.size
    x_edges = [round(index * width / 4) for index in range(5)]
    y_edges = [round(index * height / 4) for index in range(5)]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)

    preview = Image.new("RGB", (512, 512), (39, 31, 48))
    for index, name in enumerate(SPRITES):
        row, column = divmod(index, 4)
        cell = atlas.crop((x_edges[column], y_edges[row], x_edges[column + 1], y_edges[row + 1]))
        alpha = cell.getchannel("A").resize((16, 16), Image.Resampling.NEAREST)
        rgb = cell.convert("RGB").resize((16, 16), Image.Resampling.NEAREST)
        reduced = rgb.quantize(colors=12, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        texture = reduced.convert("RGB").convert("RGBA")
        texture.putalpha(alpha.point(lambda value: 255 if value >= 144 else 0))
        texture.save(OUTPUT / f"{name}.png", optimize=True)
        model = (
            '{\n'
            '  "parent": "minecraft:item/generated",\n'
            '  "textures": {\n'
            f'    "layer0": "uwudimension:item/{name}"\n'
            '  }\n'
            '}\n'
        )
        (MODELS / f"{name}.json").write_text(model, encoding="utf-8")
        preview_icon = texture.resize((128, 128), Image.Resampling.NEAREST)
        preview.paste(preview_icon, (column * 128, row * 128), preview_icon)

    preview.save(PREVIEW, optimize=True)


if __name__ == "__main__":
    main()
