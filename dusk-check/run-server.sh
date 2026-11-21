#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
root=$(pwd)
candidate=$(realpath "${1:?Supply the candidate JAR}")
baseline=$(realpath "${2:?Supply the exact Playtest 9 JAR}")
test -f "$candidate" && test -f "$baseline"
bash ./gradlew --no-daemon --no-build-cache --max-workers=2 -I dusk-check/probe.gradle duskProbeJar
server=$(mktemp -d "${RUNNER_TEMP:?}/uwu-dusk.XXXXXXXX")
cd "$server"
mkdir mods
cp "$root/build/dusk-check/dusk-probe.jar" mods/
fetch() {
    curl --retry 3 --retry-all-errors --connect-timeout 20 --max-time 150 --fail --silent --show-error --location "$1" --output "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum --check
}
fetch 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar' mods/another_furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' >eula.txt
run_stage() {
    local stage=$1 jar=$2 world=$3 marker=$4
    local log="$root/build/dusk-check/$stage.log"
    cp "$jar" mods/uwu-dimension.jar
    printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=152045\nlevel-name=%s\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\nspawn-monsters=false\n' "$world" >server.properties
    timeout --kill-after=20s 8m java -Xms512m -Xmx3g -XX:+ExitOnOutOfMemoryError \
        "-Duwu.check.duskStage=$stage" @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$log"
    if grep -Ei 'OutOfMemoryError|DUSK CHECK FAILED|single server tick has taken' "$log"; then exit 1; fi
    grep -F "$marker" "$log"
}
run_stage fresh-forward "$candidate" dusk-forward 'DUSK FRESH PASS order=forward'
run_stage fresh-reverse "$candidate" dusk-reverse 'DUSK FRESH PASS order=reverse'
forward=$(sed -n 's/.*DUSK FRESH PLAN /DUSK FRESH PLAN /p' "$root/build/dusk-check/fresh-forward.log")
reverse=$(sed -n 's/.*DUSK FRESH PLAN /DUSK FRESH PLAN /p' "$root/build/dusk-check/fresh-reverse.log")
test -n "$forward" && test "$forward" = "$reverse"
run_stage fresh-restart "$candidate" dusk-forward 'DUSK FRESH RESTART PASS'
run_stage seed-legacy "$baseline" dusk-legacy 'DUSK LEGACY SEEDED'
run_stage legacy "$candidate" dusk-legacy 'DUSK LEGACY PASS'
run_stage legacy-restart "$candidate" dusk-legacy 'DUSK LEGACY RESTART PASS'
