from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

REQUIRED = ('RevivalBlossoms', 'client/RevivalHandPose')
METADATA = 'META-INF/neoforge.mods.toml'
CHANGED_RESOURCES = (METADATA,)
PREFIX = 'dev/uwu/dimension/'
PLAYTEST_5_DIGEST = '9321a90684fbd9d93e48afd355e4f098d4f38451dea2073ed45176dc8850c051'
PLAYTEST_5_ENTRIES = 4045


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
        assert len(preserved) == PLAYTEST_5_ENTRIES and digest.hexdigest() == PLAYTEST_5_DIGEST, (
            'Content outside the approved edit scope differs from playtest 5'
        )
        if classes:
            for name in entries:
                if changed_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'DIRK SCOPE PASS {len(preserved)} preserved entries')
    print(f'DIRK JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
