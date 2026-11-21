from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

REQUIRED = ()
PREFIX = "dev/uwu/dimension/"
METADATA = 'META-INF/neoforge.mods.toml'
CHANGED_RESOURCES = ('META-INF/neoforge.mods.toml',
 'assets/uwudimension/models/block/seated_cupid_twin_tails.obj',
 'assets/uwudimension/models/item/smooth_cupid_bow.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_deep.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw0.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw1.json',
 'assets/uwudimension/models/item/smooth_cupid_bow_draw2.json')
PLAYTEST_7_ENTRIES = 4071
PLAYTEST_7_DIGEST = '4cffc0e1b1d84db4d25a98d02fcf17c179cf67eb6d8aa00bc4a5ac4e9c7cf8b9'

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
        assert len(preserved) == PLAYTEST_7_ENTRIES and digest.hexdigest() == PLAYTEST_7_DIGEST, (
            'Content outside the approved edit scope differs from playtest 7'
        )
        if classes:
            for name in entries:
                if changed_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'CUPID VISUALS SCOPE PASS {len(preserved)} preserved entries')
    print(f'CUPID VISUALS JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
