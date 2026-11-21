"""Hosted-only scope/packaging gate against the exact successful Playtest 9 JAR."""
from argparse import ArgumentParser
from hashlib import sha256
import json
from pathlib import Path
import subprocess
from zipfile import ZipFile

BASE = 'e16af6a2a1a00cb68291b6c9611d4604a0b20171'
BASE_JAR_SHA256 = '7e402979865fa5e56edc5f24d26fff9704e99fa53b9f821b8e074241bb4cd9d8'
# Captured from artifact 14699111060, successful run 44883224098 at BASE.
PRESERVED_COUNT = 4067
PRESERVED_DIGEST = '35f801490f35f4d97d4b25e43fd8b63a080728520f99cc34b11feeefa3db8d58'
PREFIX = 'dev/uwu/dimension/'
CLASSES = {'UwUDimension', 'LanternStorybookClient', 'LanternBookOpeningScreen',
           'CupidBowVisualConfig', 'CupidBowHeldVisual',
           'mixin/CupidBowHeldModelMixin', 'mixin/LanternBookOpenMixin'}
POSES = ('', '_draw0', '_draw1', '_deep', '_draw2')
MODEL_PREFIX = 'assets/uwudimension/models/item/smooth_cupid_bow'
RESOURCES = {'META-INF/neoforge.mods.toml', 'uwudimension.mixins.json'} | {
    MODEL_PREFIX + suffix + extension for suffix in POSES for extension in ('.json', '.obj')}
SOURCE_FILES = {'build.gradle'} | {'src/main/resources/' + p for p in RESOURCES} | {
    'src/main/java/' + PREFIX + name + '.java' for name in CLASSES}
INFRASTRUCTURE = {'lotus-client-check/client.gradle', 'lotus-client-check/run.sh',
                  '.github/workflows/cupid-lantern-fallback.yml'}


def changed_class(name):
    return name.startswith(PREFIX) and name.endswith('.class') and (
        name[len(PREFIX):].removesuffix('.class').split('$', 1)[0] in CLASSES)


def baseline(path):
    return subprocess.check_output(['git', 'show', f'{BASE}:{path}'])


def source_scope():
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], check=True)
    paths = subprocess.check_output(['git', 'diff', '--name-only', BASE, 'HEAD'], text=True).splitlines()
    for path in paths:
        assert path in SOURCE_FILES or path in INFRASTRUCTURE or path.startswith((
            'cupid-lantern-check/', 'lotus-client-check/java/dev/uwu/lotusclientcheck/',
            'lotus-client-check/resources/assets/uwu_lotus_client_check/models/item/playtest9_')), path
    for suffix in POSES:
        source = f'src/main/resources/{MODEL_PREFIX}{suffix}.json'
        fixture = Path(f'lotus-client-check/resources/assets/uwu_lotus_client_check/models/item/playtest9_smooth_cupid_bow{suffix}.json')
        assert fixture.read_bytes() == baseline(source), f'Comparison is not the exact shipped model: {fixture}'
    subprocess.run(['git', 'diff', '--check', BASE, 'HEAD'], check=True)
    print(f'CUPID LANTERN SOURCE SCOPE PASS {len(paths)} approved paths; baseline {BASE}')


def records(raw, prefix):
    return [line for line in raw.decode().splitlines() if line.startswith(prefix + ' ')]


def geometry(jar):
    removed = set()
    for start, end in ((649, 728), (1017, 1096), (3999, 4078), (4367, 4446)):
        removed.update(range(start - 1, end))
    for suffix in POSES:
        path = MODEL_PREFIX + suffix + '.obj'
        old = baseline('src/main/resources/' + path)
        new = jar.read(path)
        old_v, new_v = records(old, 'v'), records(new, 'v')
        old_f, new_f = records(old, 'f'), records(new, 'f')
        assert len(old_v) == len(new_v), f'Unexpected vertex removal: {path}'
        assert old_v[5892:] == new_v[5892:], f'Signature grip/core changed: {path}'
        new_t = records(new, 'vt')
        assert records(old, 'vt') == new_t, f'Authored UV coordinates changed: {path}'
        old_n, new_n = records(old, 'vn'), records(new, 'vn')
        assert new_n[:len(old_n)] == old_n, f'Unrelated original normals changed: {path}'
        kept = [line for index, line in enumerate(old_f) if index not in removed]
        assert len(new_f) == len(old_f) - 320, f'Expected only four 80-face plates removed: {path}'
        assert len(new_f) == len(kept)
        for before, after in zip(kept, new_f):
            # Only normals may change on retained faces. Closed limb topology and painted UVs stay.
            assert [v.rsplit('/', 1)[0] for v in before.split()[1:]] == [
                v.rsplit('/', 1)[0] for v in after.split()[1:]], f'Limb topology/UV changed: {path}'
        assert old_f[6700:] == new_f[6700 - 320:], f'Signature core faces changed: {path}'
        for face in new_f:
            for vertex in face.split()[1:]:
                v, t, n = map(int, vertex.split('/'))
                assert 1 <= v <= len(new_v) and 1 <= t <= len(new_t) and 1 <= n <= len(new_n)

        path = MODEL_PREFIX + suffix + '.json'
        before = json.loads(baseline('src/main/resources/' + path))
        after = json.loads(jar.read(path))
        old_gui, gui = before.pop('perspectives')['gui'], after.pop('perspectives')['gui']
        assert before == after, f'Non-GUI smooth model settings changed: {path}'
        assert gui['display']['gui']['scale'] == [1.08, 1.08, 1.08]
        assert gui['display']['gui']['rotation'] == old_gui['display']['gui']['rotation']
        for direction in ('north', 'south'):
            old_uv = old_gui['elements'][0]['faces'][direction]['uv']
            assert gui['elements'][0]['faces'][direction]['uv'] == [old_uv[2], old_uv[1], old_uv[0], old_uv[3]]
    print('CUPID LIMB AND CORE SCOPE PASS five closed limb meshes; only four crossing plates removed per pose')


def packaged(jar_path, version, classes):
    with ZipFile(jar_path) as jar:
        entries = sorted(info.filename for info in jar.infolist() if not info.is_dir())
        assert len(set(entries)) == len(entries) and jar.testzip() is None, 'Invalid JAR'
        assert f'version="{version}"'.encode() in jar.read('META-INF/neoforge.mods.toml')
        for name in CLASSES:
            path = PREFIX + name + '.class'
            assert path in entries and jar.read(path) == (classes / path).read_bytes(), path
        assert not any('lotusclientcheck' in name for name in entries), 'Probe leaked into runtime JAR'
        digest = sha256()
        preserved = [p for p in entries if p not in RESOURCES and not changed_class(p)]
        for path in preserved:
            digest.update(path.encode() + b'\0' + sha256(jar.read(path)).digest())
        assert len(preserved) == PRESERVED_COUNT and digest.hexdigest() == PRESERVED_DIGEST, (
            f'Content outside the bow/book scope differs from Playtest 9: {len(preserved)} {digest.hexdigest()}')
        geometry(jar)
    print(f'CUPID LANTERN JAR SCOPE PASS {len(preserved)} unchanged packaged entries, including worldgen, story and original bow')
    print(f'CUPID LANTERN JAR SHA256 {sha256(jar_path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('--source-only', action='store_true')
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--version')
    parser.add_argument('--classes', type=Path, default=Path('build/classes/java/main'))
    args = parser.parse_args()
    source_scope()
    if not args.source_only:
        packaged(args.jar, args.version, args.classes)
