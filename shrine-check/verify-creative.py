from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

REQUIRED = ('CupidShrine', 'CupidShrineRenderer')
PREFIX = 'dev/uwu/dimension/'
METADATA = 'META-INF/neoforge.mods.toml'
CHANGED_RESOURCES = ('META-INF/neoforge.mods.toml',
 'assets/uwudimension/models/item/cupid_shrine.json')
PLAYTEST_8_ENTRIES = 4072
PLAYTEST_8_DIGEST = 'e5b6573db937a91c460908a09599868a0fe9d61d4bcd8b9e77d592c23fc62da1'

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
        assert len(preserved) == PLAYTEST_8_ENTRIES and digest.hexdigest() == PLAYTEST_8_DIGEST, (
            'Content outside the approved edit scope differs from playtest 8'
        )
        if classes:
            for name in entries:
                if changed_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'CREATIVE SHRINE SCOPE PASS {len(preserved)} preserved entries')
    print(f'CREATIVE SHRINE JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
