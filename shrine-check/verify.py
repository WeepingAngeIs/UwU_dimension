from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

REQUIRED = ('CupidGrace',
 'CupidShrine',
 'CupidShrineRenderer',
 'CupidShrineVisits',
 'HeartwaterFeature',
 'PetalShrinePlacement',
 'SeatedCupidFollowerFlower',
 'SeatedCupidStatueVisual',
 'SettlementLayout',
 'UwUDimension',
 'VillageSakura')
METADATA = 'META-INF/neoforge.mods.toml'
CHANGED_RESOURCES = ('META-INF/neoforge.mods.toml',
 'assets/uwudimension/blockstates/cupid_shrine.json',
 'assets/uwudimension/lang/en_us.json',
 'assets/uwudimension/models/block/cupid_shrine_marker.json',
 'assets/uwudimension/models/block/seated_cupid_twin_tails.json',
 'assets/uwudimension/models/block/seated_cupid_twin_tails.mtl',
 'assets/uwudimension/models/block/seated_cupid_twin_tails.obj',
 'assets/uwudimension/textures/block/seated_cupid_twin_tails/cupid.png',
 'assets/uwudimension/textures/block/seated_cupid_twin_tails/eyes.png',
 'assets/uwudimension/textures/block/seated_cupid_twin_tails/head_wings.png',
 'assets/uwudimension/textures/block/seated_cupid_twin_tails/pedestal.png',
 'assets/uwudimension/textures/mob_effect/cupid_grace.png')
PREFIX = 'dev/uwu/dimension/'
PLAYTEST_6_DIGEST = 'd1b045136a8efb39ccfa24cfeb9f4fd7e968387cbc4fb0f20624940d5d7b934a'
PLAYTEST_6_ENTRIES = 4044


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
        assert len(preserved) == PLAYTEST_6_ENTRIES and digest.hexdigest() == PLAYTEST_6_DIGEST, (
            'Content outside the approved edit scope differs from playtest 6'
        )
        if classes:
            for name in entries:
                if changed_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'PETAL SHRINE SCOPE PASS {len(preserved)} preserved entries')
    print(f'PETAL SHRINE JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
