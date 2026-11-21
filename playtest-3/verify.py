from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

REQUIRED = (
    'DawnpetalAbilities', 'DawnpetalAbility', 'Keeper', 'KeeperDoorPassage',
    'ReturnbloomFacing', 'ReturnbloomTurnBack', 'RevivalBlossomClient', 'RevivalBlossomMotion',
    'RevivalBlossoms', 'SharedAbilityHotbar', 'compat/SakuraFluidJadePlugin',
    'mixin/DaggerAttackMixin', 'mixin/DawnpetalBetterCombatMixin', 'mixin/DawnpetalPacketRangeMixin',
    'mixin/RevivalBlossomPositionMixin',
)
REMOVED = ('ReturnbloomPrime', 'ReturnbloomPrimedHitEvent')
METADATA = 'META-INF/neoforge.mods.toml'
CHANGED_RESOURCES = (METADATA, 'uwudimension.mixins.json', 'assets/uwudimension/lang/en_us.json')
PREFIX = 'dev/uwu/dimension/'
PLAYTEST_2_DIGEST = '37d4cb4abb9a63a54b45b3614b4b0ef14f4c27d7ed1b5349a28ec8f8b771dc36'
PLAYTEST_2_ENTRIES = 3962


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
        assert len(preserved) == PLAYTEST_2_ENTRIES and digest.hexdigest() == PLAYTEST_2_DIGEST, (
            'Content outside the four approved edits differs from playtest 2'
        )
        if classes:
            for name in entries:
                if changed_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'PLAYTEST 3 SCOPE PASS {len(preserved)} preserved entries')
    print(f'PLAYTEST 3 JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
