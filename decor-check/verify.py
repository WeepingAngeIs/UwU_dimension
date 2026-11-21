from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

REQUIRED = ('Arsenal',
 'ArsenalClient',
 'BakerToaster',
 'InteriorFurnishings',
 'LanternBookModels',
 'SettlementLayout',
 'ToastClaims',
 'UwUDimension')
METADATA = 'META-INF/neoforge.mods.toml'
CHANGED_RESOURCES = ('META-INF/neoforge.mods.toml',
 'assets/uwudimension/blockstates/baker_toaster.json',
 'assets/uwudimension/lang/en_us.json',
 'assets/uwudimension/models/block/baker_toaster.json',
 'assets/uwudimension/models/block/lantern_storybook.json',
 'assets/uwudimension/models/item/cupid_bow.json',
 'assets/uwudimension/models/item/lantern_for_both_shores_cover.json',
 'assets/uwudimension/models/item/smooth_cupid_bow.json',
 'assets/uwudimension/models/item/smooth_cupid_bow.obj',
 'assets/uwudimension/models/item/smooth_cupid_bow_deep.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_deep.obj',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw0.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw0.obj',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw1.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw1.obj',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw2.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw2.obj',
 'assets/uwudimension/models/item/smooth_cupid_finish.mtl',
 'assets/uwudimension/models/item/uwu_bread.json',
 'assets/uwudimension/sounds.json',
 'assets/uwudimension/sounds/toaster_ding.ogg',
 'assets/uwudimension/textures/block/baker_toaster.png',
 'assets/uwudimension/textures/block/baker_toaster_channel.png',
 'assets/uwudimension/textures/block/baker_toaster_enamel.png',
 'assets/uwudimension/textures/item/lantern_for_both_shores_cover.png',
 'assets/uwudimension/textures/item/lantern_for_both_shores_cover.png.mcmeta',
 'assets/uwudimension/textures/item/smooth_cupid_bow.png',
 'assets/uwudimension/textures/item/smooth_cupid_bow.png.mcmeta',
 'assets/uwudimension/textures/item/smooth_cupid_bow_deep.png',
 'assets/uwudimension/textures/item/smooth_cupid_bow_deep.png.mcmeta',
 'assets/uwudimension/textures/item/smooth_cupid_bow_draw0.png',
 'assets/uwudimension/textures/item/smooth_cupid_bow_draw0.png.mcmeta',
 'assets/uwudimension/textures/item/smooth_cupid_bow_draw1.png',
 'assets/uwudimension/textures/item/smooth_cupid_bow_draw1.png.mcmeta',
 'assets/uwudimension/textures/item/smooth_cupid_bow_draw2.png',
 'assets/uwudimension/textures/item/smooth_cupid_bow_draw2.png.mcmeta',
 'assets/uwudimension/textures/item/smooth_cupid_lace.png',
 'assets/uwudimension/textures/item/smooth_cupid_palette.png',
 'assets/uwudimension/textures/item/smooth_cupid_palette.png.mcmeta',
 'assets/uwudimension/textures/item/uwu_bread.png')
PREFIX = 'dev/uwu/dimension/'
PLAYTEST_4_DIGEST = '956fdae3918ffc484e38f3f7ace34257dc16a6d9b098e20ed6ee60fe0fd322f7'
PLAYTEST_4_ENTRIES = 3996


def changed_class(name):
    return name.startswith(PREFIX) and name.endswith('.class') and (
        name[len(PREFIX):].removesuffix('.class').split('$', 1)[0] in REQUIRED
    )


def verify(jar_path, version, classes):
    digest = sha256()
    with ZipFile(jar_path) as jar:
        assert jar.testzip() is None, 'Corrupt JAR'
        entries = sorted(info.filename for info in jar.infolist() if not info.is_dir())
        assert len(set(entries)) == len(entries), 'Duplicate JAR entries'
        assert f'version="{version}"'.encode() in jar.read(METADATA), 'Incorrect mod version'
        for resource in CHANGED_RESOURCES:
            assert resource in entries, f'Missing {resource}'
        for name in REQUIRED:
            assert f'{PREFIX}{name}.class' in entries, f'Missing {name}'
        preserved = [e for e in entries if e not in CHANGED_RESOURCES and not changed_class(e)]
        for entry in preserved:
            digest.update(entry.encode() + b'\0' + sha256(jar.read(entry)).digest())
        assert len(preserved) == PLAYTEST_4_ENTRIES and digest.hexdigest() == PLAYTEST_4_DIGEST, (
            'Content outside the approved edit scope differs from playtest 4'
        )
        if classes:
            for name in entries:
                if changed_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'DECOR SCOPE PASS {len(preserved)} preserved entries')
    print(f'DECOR JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
