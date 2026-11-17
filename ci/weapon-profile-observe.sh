#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew jar expeditionJar
mkdir -p build/weapon-profile-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips}.jar build/libs/*.jar build/expedition/*.jar build/weapon-profile-server/mods/
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/5sy6g3kz/versions/VhIOvcXP/bettercombat-neoforge-2.4.0%2B1.21.1.jar' -o build/weapon-profile-server/mods/bettercombat.jar
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gedNE4y2/versions/HJZB6bmA/player-animation-lib-forge-2.0.4%2B1.21.1.jar' -o build/weapon-profile-server/mods/playeranimator.jar
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/9s6osm5g/versions/izKINKFg/cloth-config-15.0.140-neoforge.jar' -o build/weapon-profile-server/mods/cloth-config.jar
echo '0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a  build/weapon-profile-server/mods/bettercombat.jar' | sha256sum -c -
echo 'dbe5de45f5cd60c0e5e47af14e6d564534a98456e973cf670cb881f6938eee92  build/weapon-profile-server/mods/playeranimator.jar' | sha256sum -c -
echo '65e722e0d98431a07c45f8bdd8d529a217cc8c175fde1740248bd5c1b4f3c0d4  build/weapon-profile-server/mods/cloth-config.jar' | sha256sum -c -
cd build/weapon-profile-server
mkdir -p config/bettercombat
cp ../../ci/fixtures/bettercombat/fallback_compatibility.json config/bettercombat/fallback_compatibility.json
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nonline-mode=false\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' > server.properties
UWU_WEAPON_OBSERVE=1 timeout 15m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee observation.log
grep -F 'UWU WEAPON PROFILE OBSERVATION PASS' observation.log
test -s weapon-profile-observation.json
python ../../ci/weapon-profile-compare.py ../../ci/fixtures/bettercombat/beta11-weapon-profile-observation.json.gz weapon-profile-observation.json mods/bettercombat.jar mods/simplyswords.jar
