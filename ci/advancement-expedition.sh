#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew expeditionJar
mkdir -p build/advancement-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury}.jar build/libs/*.jar build/expedition/*.jar build/advancement-server/mods/
cd build/advancement-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' > server.properties
UWU_ADVANCEMENT=1 timeout 10m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee advancement.log
grep -F 'UWU ADVANCEMENT SERVER PASSED' advancement.log
