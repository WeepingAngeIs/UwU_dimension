#!/usr/bin/env bash
set -euo pipefail
root=$(pwd)
candidate=$(realpath "${1:?Supply the combined shrine JAR}")
test -f "$candidate"
if [[ ${UWU_SHRINE_FRESH_ONLY:-false} != true && ${UWU_SHRINE_ORDERS_ONLY:-false} != true ]]; then
    baseline=$(realpath "${2:?Supply the verified Playtest 6 JAR}")
    test -f "$baseline"
fi
bash ./gradlew --no-daemon --max-workers=2 -I shrine-check/probe.gradle petalShrineProbeJar

server=$(mktemp -d "${RUNNER_TEMP:-/tmp}/uwu-petal-shrine.XXXXXXXX")
cd "$server"
mkdir mods
cp "$root/build/shrine-check/petal-shrine-probe.jar" mods/
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
    local mode=$1 jar=$2 world=$3 marker=$4
    local log="$root/build/shrine-check/$mode.log"
    cp "$jar" mods/uwu-dimension.jar
    printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=152045\nlevel-name=%s\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\nspawn-monsters=false\n' "$world" >server.properties
    timeout --kill-after=20s 8m java -Xms512m -Xmx3g -XX:+ExitOnOutOfMemoryError \
        "-Duwu.check.shrineOrder=$mode" @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$log"
    if grep -Ei 'OutOfMemoryError|PETAL SHRINE CHECK FAILED|single server tick has taken' "$log"; then
        exit 1
    fi
    grep -F "$marker" "$log"
}

run_stage shoulder "$candidate" shrine-shoulder 'PETAL SHRINE SERVER PASS order=shoulder'
if [[ ${UWU_SHRINE_FRESH_ONLY:-false} == true ]]; then exit 0; fi
run_stage village "$candidate" shrine-village 'PETAL SHRINE SERVER PASS order=village'
shoulder=$(sed -n 's/.*PETAL SHRINE SERVER PASS order=shoulder anchor=/anchor=/p' "$root/build/shrine-check/shoulder.log")
village=$(sed -n 's/.*PETAL SHRINE SERVER PASS order=village anchor=/anchor=/p' "$root/build/shrine-check/village.log")
test -n "$shoulder" && test "$shoulder" = "$village"
if [[ ${UWU_SHRINE_ORDERS_ONLY:-false} == true ]]; then exit 0; fi
run_stage seed-legacy "$baseline" shrine-legacy 'PETAL SHRINE LEGACY WORLD SEEDED'
run_stage legacy "$candidate" shrine-legacy 'PETAL SHRINE LEGACY WORLD PASS'
