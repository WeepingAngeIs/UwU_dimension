#!/usr/bin/env bash
set -euo pipefail
root=$(pwd)
release=$(realpath "${1:?Supply the path to the built production JAR}")
test -f "$release"
python3 - "$release" <<'PY'
import json
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as jar:
    sounds = json.loads(jar.read("assets/uwudimension/sounds.json"))
    for faction, pitch in (("petal", 1.17), ("dusk", 0.73)):
        assert sounds[f"{faction}_hurt"] == {
            "subtitle": f"subtitles.uwudimension.{faction}_voice",
            "sounds": [{"name": f"uwudimension:{faction}_voice", "type": "event", "pitch": pitch}],
        }
        original = [f"uwudimension:{faction}_{i}" for i in range(1, 4)]
        assert sounds[f"{faction}_voice"]["sounds"] == original
        for sound in original:
            jar.getinfo("assets/uwudimension/sounds/" + sound.split(":")[1] + ".ogg")
print("KEEPER HURT ASSETS PASS each hurt event aliases the original voice with its own pitch")
PY
bash ./gradlew --no-daemon --max-workers=2 -I guardian-check/probe.gradle guardianHomeProbeJar
server=$(mktemp -d "${RUNNER_TEMP:-/tmp}/uwu-guardian-home.XXXXXXXX")
cd "$server"
mkdir mods
fetch() {
    curl --retry 3 --retry-all-errors --connect-timeout 20 --max-time 150 --fail --silent --show-error --location "$1" --output "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum --check
}
fetch 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar' mods/another_furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
cp "$release" "$root/build/guardian-check/guardian-home-probe.jar" mods/
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' >eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\nspawn-monsters=false\n' >server.properties
timeout --kill-after=20s 12m java -Xms512m -Xmx3g -XX:+ExitOnOutOfMemoryError -Duwu.check.duskChest="${UWU_DUSK_CHEST_ONLY:-false}" -Duwu.check.decor="${UWU_DECOR_CHECK:-false}" @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$root/build/guardian-check/server.log"
markers=('GUARDIAN HOME BINDING PASS' 'GUARDIAN HOME SCHEDULE PASS' 'GUARDIAN HOME DOORS PASS' 'GUARDIAN HOME SERVER PASS' 'KEEPER DOOR SERVER PASS' 'KEEPER HURT ROUTING PASS' 'GUARDIAN RESCUE PASS')
if [[ ${UWU_DUSK_CHEST_ONLY:-false} == true ]]; then markers=('DUSK CHEST SERVER PASS'); fi
if [[ ${UWU_DECOR_CHECK:-false} == true ]]; then
    markers=('BAKER TOASTER SERVER PASS' 'LANTERN READING HOUSES PASS' 'CUPID BOW GAMEPLAY PARITY PASS')
fi
for marker in "${markers[@]}"; do
    grep -F "$marker" "$root/build/guardian-check/server.log"
done
if grep -Ei 'OutOfMemoryError|GUARDIAN HOME CHECK FAILED|single server tick has taken' "$root/build/guardian-check/server.log"; then
    exit 1
fi
