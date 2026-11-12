from collections import deque
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "docs" / "art"
ATLAS = ART / "uwu_hearty_food_sprite_atlas.png"
OUTPUT = ROOT / "src" / "main" / "resources" / "assets" / "uwudimension" / "textures" / "item"
MODELS = ROOT / "src" / "main" / "resources" / "assets" / "uwudimension" / "models" / "item"
PREVIEW = ART / "uwu_hearty_food_textures_preview.png"

SPRITES = (
    "blossom_ice_cream_bucket",
    "strawberry_ice_cream_cake",
    "flower_stuffed_chicken",
    "heartberry_burger",
    "blossom_hot_pocket",
    "uwu_ramen_bowl",
    "cherry_picnic_bento",
    "berry_cinnamon_roll",
    "starberry_waffle_stack",
    "creamy_mushroom_stew",
    "loaded_heart_potato",
    "rose_berry_pie_slice",
    "pastel_sakura_dumplings",
    "honey_glazed_ham",
    "moonberry_pudding",
    "chicken_flower_pot_pie",
)


def discard_speckles(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    mask = alpha.point(lambda value: 1 if value >= 96 else 0)
    pixels = mask.load()
    width, height = mask.size
    visited = bytearray(width * height)
    keep = bytearray(width * height)
    for y in range(height):
        for x in range(width):
            index = y * width + x
            if not pixels[x, y] or visited[index]:
                continue
            queue = deque([(x, y)])
            visited[index] = 1
            component = []
            while queue:
                cx, cy = queue.popleft()
                component.append((cx, cy))
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if 0 <= nx < width and 0 <= ny < height:
                        nindex = ny * width + nx
                        if pixels[nx, ny] and not visited[nindex]:
                            visited[nindex] = 1
                            queue.append((nx, ny))
            if len(component) >= 28:
                for cx, cy in component:
                    keep[cy * width + cx] = 255
    cleaned_alpha = Image.frombytes("L", (width, height), bytes(keep))
    rgba.putalpha(cleaned_alpha)
    return rgba


def main() -> None:
    atlas = Image.open(ATLAS).convert("RGBA")
    width, height = atlas.size
    if width != height:
        raise ValueError(f"Expected a square 4-by-4 atlas, got {width}x{height}")
    x_edges = [round(index * width / 4) for index in range(5)]
    y_edges = [round(index * height / 4) for index in range(5)]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGB", (512, 512), (39, 31, 48))

    for index, name in enumerate(SPRITES):
        row, column = divmod(index, 4)
        cell = atlas.crop((x_edges[column], y_edges[row], x_edges[column + 1], y_edges[row + 1]))
        cell = discard_speckles(cell)
        rgba = cell.resize((16, 16), Image.Resampling.NEAREST)
        reduced = rgba.convert("RGB").quantize(colors=12, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        texture = reduced.convert("RGB").convert("RGBA")
        texture.putalpha(rgba.getchannel("A").point(lambda value: 255 if value >= 96 else 0))
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
        icon = texture.resize((128, 128), Image.Resampling.NEAREST)
        preview.paste(icon, (column * 128, row * 128), icon)

    preview.save(PREVIEW, optimize=True)


if __name__ == "__main__":
    main()
