from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

REQUIRED = ('DuskRewardChestRenderer',
 'HeartwaterFeature',
 'NativeRewardChestBlockEntity',
 'NativeRewardChestMenu',
 'NativeRewardChests',
 'compat/SakuraFluidJadePlugin',
 'lush/client/LushCaveModels',
 'lush/client/LushRenderView')
REMOVED = ()
METADATA = 'META-INF/neoforge.mods.toml'
CHANGED_RESOURCES = ('META-INF/neoforge.mods.toml',
 'assets/uwudimension/blockstates/dusk_reward_chest.json',
 'assets/uwudimension/lang/en_us.json',
 'assets/uwudimension/models/block/dusk_reward_chest_body.json',
 'assets/uwudimension/models/block/dusk_reward_chest_lid.json',
 'assets/uwudimension/sounds.json',
 'assets/uwudimension/sounds/dusk_chest_close.ogg',
 'assets/uwudimension/sounds/dusk_chest_open.ogg',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines.png',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines_lit.png',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines_lit_foliage.png',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines_lit_glow.png',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines_plant.png',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines_plant_lit.png',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines_plant_lit_foliage.png',
 'assets/uwudimension/textures/block/uwu_cave/cave_vines_plant_lit_glow.png',
 'assets/uwudimension/textures/block/uwu_cave/grass_block_side.png',
 'assets/uwudimension/textures/block/uwu_cave/grass_block_side_overlay.png',
 'assets/uwudimension/textures/block/uwu_cave/grass_block_top.png',
 'assets/uwudimension/textures/block/uwu_cave/moss_block.png',
 'assets/uwudimension/textures/block/uwu_cave/short_grass.png',
 'assets/uwudimension/textures/block/uwu_cave/tall_grass_bottom.png',
 'assets/uwudimension/textures/block/uwu_cave/tall_grass_top.png')
PREFIX = 'dev/uwu/dimension/'
PLAYTEST_3_DIGEST = 'e5b2a93bb845520e8bacfe1f2598c0e3e6206c86a248f64a7ab1eb2c3d09b1d0'
PLAYTEST_3_ENTRIES = 3972


def changed_class(name):
    return name.startswith(PREFIX) and name.endswith('.class') and (
        name[len(PREFIX):].removesuffix('.class').split('$', 1)[0] in REQUIRED + REMOVED
    )


def verify(jar_path, version, classes):
    digest = sha256()
    with ZipFile(jar_path) as jar:
        assert jar.testzip() is None, 'Corrupt JAR'
        entries = sorted(info.filename for info in jar.infolist() if not info.is_dir())
        assert len(set(entries)) == len(entries), 'Duplicate JAR entries'
        assert f'version="{version}"'.encode() in jar.read(METADATA), 'Incorrect mod version'
        for name in REQUIRED:
            assert f'{PREFIX}{name}.class' in entries, f'Missing {name}'
        for name in REMOVED:
            assert not any(e == f'{PREFIX}{name}.class' or e.startswith(f'{PREFIX}{name}$') for e in entries), name
        preserved = [e for e in entries if e not in CHANGED_RESOURCES and not changed_class(e)]
        for entry in preserved:
            digest.update(entry.encode() + b'\0' + sha256(jar.read(entry)).digest())
        assert len(preserved) == PLAYTEST_3_ENTRIES and digest.hexdigest() == PLAYTEST_3_DIGEST, (
            'Content outside the approved edit scope differs from playtest 3'
        )
        if classes:
            for name in entries:
                if changed_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'CAVE POLISH SCOPE PASS {len(preserved)} preserved entries')
    print(f'CAVE POLISH JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
