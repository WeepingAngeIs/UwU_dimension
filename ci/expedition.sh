#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew expeditionJar
mkdir -p build/field-server/mods
cp libs/{cobblemon,lootr,kotlinforforge}.jar build/libs/*.jar build/expedition/*.jar build/field-server/mods/
cd build/field-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=%s\nview-distance=4\nsimulation-distance=4\nmax-tick-time=600000\n' "$UWU_FIELD_SEED" > server.properties
mkdir -p config/cobblemon/spawning
cp ../../ci/natural-spawning.json config/cobblemon/main.json
cp ../../ci/pack-spawning.json config/cobblemon/spawning/best-spawner-config.json
if [[ "$UWU_FIELD_SEED" == "152045" ]]; then
  bash ../../ci/upgrade-fixture.sh
fi
timeout 25m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee expedition.log
grep -F 'HEARTWATER EXPEDITION PASSED' expedition.log
