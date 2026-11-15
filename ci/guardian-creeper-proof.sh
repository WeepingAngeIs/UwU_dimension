#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew expeditionJar
mkdir -p build/guardian-creeper-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury}.jar build/libs/*.jar build/expedition/*.jar build/guardian-creeper-server/mods/
cd build/guardian-creeper-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=152045\ndifficulty=hard\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' > server.properties
for run in first second third; do
  UWU_GUARDIAN_CREEPER=1 timeout 14m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "creeper-${run}.log"
  case "$run" in
    first) grep -F 'GUARDIAN CREEPER AI PASS' "creeper-${run}.log" ;;
    second) grep -F 'GUARDIAN CREEPER RESTART PASS' "creeper-${run}.log" ;;
    third) grep -F 'GUARDIAN CREEPER PERSISTENCE PASS' "creeper-${run}.log" ;;
  esac
done
