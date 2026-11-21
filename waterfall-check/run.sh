#!/usr/bin/env bash
set -euo pipefail
root=$(pwd)
release=$(realpath "${1:?Supply the path to the built production JAR}")
test -f "$release"
bash ./gradlew --no-daemon --max-workers=2 -I waterfall-check/probe.gradle waterfallProbeJar
server=$(mktemp -d "${RUNNER_TEMP:-/tmp}/uwu-waterfall.XXXXXXXX")
cd "$server"
mkdir mods
fetch() {
    curl --retry 3 --retry-all-errors --connect-timeout 20 --max-time 150 --fail --silent --show-error --location "$1" --output "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum --check
}
fetch 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch 'https://cdn.modrinth.com/data/COlSi5iR/versions/yxOYFgnK/c2me-neoforge-mc1.21.1-0.4.0-alpha.0.122.jar' mods/c2me.jar 672bd9a7dc3fae20817f3563e140d82be90845ef50ce26f57698082d4b2918c0
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar' mods/another_furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
cp "$release" "$root/build/waterfall-check/waterfall-probe.jar" mods/
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' >eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\n' >server.properties
timeout --kill-after=20s 15m java -Xms512m -Xmx3g -XX:+ExitOnOutOfMemoryError @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$root/build/waterfall-check/server.log"
log="$root/build/waterfall-check/server.log"
if grep -Ei 'OutOfMemoryError|Error upgrading chunk|HEARTWATER WATERFALL CHECK FAILED|single server tick has taken' "$log"; then
    exit 1
fi
grep -F 'HEARTWATER WATERFALL PASS' "$log"
