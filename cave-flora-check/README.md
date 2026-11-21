# Sakura Reach cave flora and UwU Berry

Baseline: `e16af6a2a1a00cb68291b6c9611d4604a0b20171` (Playtest 9).
All compilation and runtime checks belong on the public repository's GitHub-hosted runner.

## Inventory before editing

Sakura Reach's dimension definition selects `minecraft:lush_caves` and
`uwudimension:lantern_grotto`. The grotto's vegetation feature list includes
glow lichen, ceiling moss, cave vines, clay/dripleaf patches, floor moss and spore
blossoms. The vanilla lush biome additionally places rooted azalea trees,
tall grass and ordinary cave vines. The referenced 1.21.1 configured features
resolve to the following plant set:

| Feature family | Blocks / model parts |
| --- | --- |
| Moss patch | Moss block, carpet, short/tall grass, azalea and flowering azalea bushes |
| Dripleaf | Big leaf in every tilt/orientation, big stem, both small halves, leaf sides and tips |
| Azalea tree | Azalea leaves, flowering leaves, oak trunk, rooted dirt and hanging roots |
| Ceiling / walls | Ripe and bare cave-vine tip/body, ordinary vines, glow lichen, spore blossom and its base |

The pre-existing cave renderer already covered moss, grass and cave vines.
This change adds 13 block families and 23 source texture names, without editing
feature placement, stored block states, biome definitions, fluids or waterfalls.

Public source references:

- [Dimension selection at the exact baseline](https://github.com/WeepingAngeIs/z-UwU_dm/blob/e16af6a2a1a00cb68291b6c9611d4604a0b20171/src/main/resources/data/uwudimension/dimension/sakura_reach.json)
- [Lantern grotto at the exact baseline](https://github.com/WeepingAngeIs/z-UwU_dm/blob/e16af6a2a1a00cb68291b6c9611d4604a0b20171/src/main/resources/data/uwudimension/worldgen/biome/lantern_grotto.json)
- [Extracted Minecraft 1.21.1 data](https://github.com/misode/mcmeta/tree/1.21.1-data/data/minecraft/worldgen)
- [Extracted Minecraft 1.21.1 block models](https://github.com/misode/mcmeta/tree/1.21.1-assets/assets/minecraft/models/block)
- [NeoForge 1.21.1 sprite-source API example](https://github.com/neoforged/NeoForge/blob/1.21.1/tests/src/main/java/net/neoforged/neoforge/oldtest/client/CustomSpriteSourceTest.java)

## Palette and world boundary

`CaveFloraSprites` loads a separate copy of each original plant sprite and maps
its light/dark detail onto the existing `uwu_cave/grass_block_top` pink shades or
`uwu_cave/moss_block` light-blue shades. Flowering azaleas use blue foliage and
pink flowers. Source dimensions, alpha silhouettes, texture coordinates,
animation metadata and model geometry remain intact. Explicit resource-pack
overrides under the new `uwudimension:block/uwu_cave/flora/` prefix are respected.
No extracted vanilla PNG, private image or new interpreted artwork is committed.

The generated sprites have separate names. The existing model wrapper selects
them only for Sakura Reach's lush-cave and lantern-grotto biomes; all outside
dimension/biome contexts keep the original sprites and biome tint. The shared
`LushCaveScope` predicate also determines the server's harvested berry.

## Berry behavior

`uwudimension:uwu_berry` is named **UwU Berry** and has vanilla glow-berry food
values (2 hunger, 0.1 saturation modifier). Its item artwork references the
existing pink berry-only layer of the vine art. It is a separate edible item,
not a renamed vanilla stack or a Jade-only caption.

The mixin replaces only the item passed to `Block.popResource` inside the
vanilla ripe-vine use function. Vanilla owns the fruit state, light, sound,
game event, empty-vine behavior and regrowth. Both tip and body harvest are
covered. Outside the exact scoped caves the same native code drops ordinary
glow berries. The new food item does not replace the vanilla block-to-item
mapping or globally alter vanilla berries.

## Hosted checks

The cave-only client route runs `JadeFluidProbe` and `CaveFloraProbe`, with the
bow/book route excluded by the workflow's cave mode. Required markers:

- `JADE REACH BERRY CAPTIONS PASS`
- `CAVE VINE MODELS AND HARVEST PASS`
- `CAVE FLORA PALETTE PASS`

The checks inspect actual baked quads and atlas pixels for all 13 added block
families and every state, including orientations, tilts and waterlogged states.
They assert exact alpha masks, unchanged geometry/color/light outside remapped
UVs, use of the existing pink/blue palette, particle sprites and safe reuse of
previously themed model data. The two scoped cave biomes are checked separately
from Overworld lush caves, Reach cherry grove and Reach Vesper Hollows controls.

Harvest checks observe actual `ItemEntity` submissions from native
`useWithoutItem`, not just a return value: one correctly named berry, no second
drop from an exhausted vine, native bonemeal regrowth, another correct harvest,
and light 14 → 0 → 14 → 0. A real `finishUsingItem` call consumes one UwU Berry
and restores hunger from 10 to 12. Temporary fixture-player food and abilities
are restored afterward.

The hosted client writes `cave-flora-palette.png` and `cave-flora-palette.txt`
inside its disposable game directory. Each sheet pair shows the source sprite
on the left and the scoped UwU sprite on the right for visual review. These are
runtime diagnostics, not private playtest screenshots or existing-world data.
