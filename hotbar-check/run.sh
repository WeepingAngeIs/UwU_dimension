#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/hotbar-check-deps build/hotbar-client/config/bettercombat
fetch() {
    curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 120 "$1" -o "build/hotbar-check-deps/$2.jar"
    printf '%s  %s\n' "$3" "build/hotbar-check-deps/$2.jar" | sha256sum --check
}
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' lootr 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/5sy6g3kz/versions/VhIOvcXP/bettercombat-neoforge-2.4.0%2B1.21.1.jar' bettercombat 0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a
fetch 'https://cdn.modrinth.com/data/gedNE4y2/versions/HJZB6bmA/player-animation-lib-forge-2.0.4%2B1.21.1.jar' playeranimator dbe5de45f5cd60c0e5e47af14e6d564534a98456e973cf670cb881f6938eee92
fetch 'https://cdn.modrinth.com/data/9s6osm5g/versions/izKINKFg/cloth-config-15.0.140-neoforge.jar' cloth-config 65e722e0d98431a07c45f8bdd8d529a217cc8c175fde1740248bd5c1b4f3c0d4
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:45\nenableVsync:false\npauseOnLostFocus:false\n' > build/hotbar-client/options.txt
printf 'earlyWindowControl = false\n' > build/hotbar-client/config/fml.toml
printf '{"firstPersonAnimations":"AUTO"}\n' > build/hotbar-client/config/bettercombat/client.json5
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
timeout --kill-after=20s 10m xvfb-run -a -s '-screen 0 800x600x24' bash gradlew --no-daemon --max-workers=2 -I hotbar-check/client.gradle runHotbarClient 2>&1 | tee build/hotbar-client.log
for marker in 'HOTBAR INPUT PASS' 'HOTBAR DISPLAY PASS' 'DIRK THROW PASS' 'HOTBAR CLIENT PASS'; do
    grep -F "$marker" build/hotbar-client.log
done
