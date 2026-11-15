#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/renewal-server/mods
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/5sy6g3kz/versions/VhIOvcXP/bettercombat-neoforge-2.4.0%2B1.21.1.jar' -o build/renewal-server/mods/bettercombat.jar
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gedNE4y2/versions/HJZB6bmA/player-animation-lib-forge-2.0.4%2B1.21.1.jar' -o build/renewal-server/mods/playeranimator.jar
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/9s6osm5g/versions/izKINKFg/cloth-config-15.0.140-neoforge.jar' -o build/renewal-server/mods/cloth-config.jar
echo '0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a  build/renewal-server/mods/bettercombat.jar' | sha256sum -c -
echo 'dbe5de45f5cd60c0e5e47af14e6d564534a98456e973cf670cb881f6938eee92  build/renewal-server/mods/playeranimator.jar' | sha256sum -c -
echo '65e722e0d98431a07c45f8bdd8d529a217cc8c175fde1740248bd5c1b4f3c0d4  build/renewal-server/mods/cloth-config.jar' | sha256sum -c -
export UWU_TEST_BETTERCOMBAT=1 UWU_FIELD_SEED=152045
bash ci/renewal-server.sh
grep -F 'UWU BETTER COMBAT OFFHAND PASS actual packet handler front/rear and hand restoration' build/renewal-server/first.log
grep -F 'UWU BETTER COMBAT ANGLE PASS boundaries, cooldown, airborne critical and armor' build/renewal-server/first.log
grep -F 'UWU GLITCH BETTER COMBAT PASS form-specific Twin Chakram and explicit player preset' build/renewal-server/first.log

