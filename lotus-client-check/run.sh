#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/lotus-client-deps build/lotus-client/config
fetch() {
    curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 120 "$1" -o "build/lotus-client-deps/$2.jar"
    printf '%s  %s\n' "$3" "build/lotus-client-deps/$2.jar" | sha256sum --check
}
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' lootr 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://www.cursemaven.com/curse/maven/jade-324717/8591319/jade-324717-8591319.jar' jade 276f1f65a20d04f536989c4f311949d770b52c440c23c2b904a08860f3c93810
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\n' > build/lotus-client/options.txt
printf 'earlyWindowControl = false\n' > build/lotus-client/config/fml.toml
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
timeout --kill-after=20s 10m xvfb-run -a -s '-screen 0 800x600x24' bash gradlew --no-daemon --max-workers=2 -I lotus-client-check/client.gradle runLotusClient 2>&1 | tee build/lotus-client.log
grep -F 'LOTUS FIRST PERSON PASS' build/lotus-client.log
grep -F 'LOTUS DYNAMIC LIGHT PASS' build/lotus-client.log
grep -F 'LOTUS COMPANION MOMENTUM PASS' build/lotus-client.log
grep -F 'LANTERN COVER CLIENT PASS' build/lotus-client.log
if [[ ${UWU_DECOR_CHECK:-false} != true ]]; then
    grep -F 'JADE REACH FLUID CAPTIONS PASS' build/lotus-client.log
    grep -F 'JADE REACH BERRY CAPTIONS PASS' build/lotus-client.log
    grep -F 'CAVE VINE MODELS AND HARVEST PASS' build/lotus-client.log
fi
grep -F 'CUPID CREATIVE CHOICES PASS' build/lotus-client.log
grep -F 'CUPID CREATIVE AND CLIENT POSES PASS' build/lotus-client.log
