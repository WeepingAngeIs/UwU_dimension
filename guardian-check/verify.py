from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

ALLOWED = ('BlossomWarden', 'GolemSwap', 'GuardianPatrolGoal', 'GuardianDoorGoal', 'GuardianVillageHomes')
METADATA = 'META-INF/neoforge.mods.toml'
PREFIX = 'dev/uwu/dimension/'
PLAYTEST_DIGEST = '6a4405a633d3730695063d24115181767159f96d8f6da0de076f9abb832b6ef3'
PLAYTEST_ENTRY_COUNT = 3974


def guardian_class(name):
    return name.startswith(PREFIX) and name.endswith('.class') and (
        name[len(PREFIX):].removesuffix('.class').split('$', 1)[0] in ALLOWED
    )


def verify(jar_path, version, classes):
    digest = sha256()
    with ZipFile(jar_path) as jar:
        assert jar.testzip() is None, 'Corrupt JAR'
        entries = sorted(info.filename for info in jar.infolist() if not info.is_dir())
        assert len(set(entries)) == len(entries), 'Duplicate JAR entries'
        assert f'version="{version}"'.encode() in jar.read(METADATA), 'Incorrect mod version'
        for name in ALLOWED:
            assert f'{PREFIX}{name}.class' in entries, f'Missing {name}'
        kept = [name for name in entries if name != METADATA and not guardian_class(name)]
        for name in kept:
            digest.update(name.encode() + b'\0' + sha256(jar.read(name)).digest())
        assert len(kept) == PLAYTEST_ENTRY_COUNT and digest.hexdigest() == PLAYTEST_DIGEST, (
            'Content outside the Guardian scope differs from the verified playtest JAR'
        )
        if classes:
            for name in entries:
                if guardian_class(name):
                    assert jar.read(name) == (classes / name).read_bytes(), name
    print(f'GUARDIAN SCOPE PASS {len(kept)} unchanged entries')
    print(f'GUARDIAN JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('jar', type=Path)
    parser.add_argument('version')
    parser.add_argument('--classes', type=Path)
    args = parser.parse_args()
    verify(args.jar, args.version, args.classes)
