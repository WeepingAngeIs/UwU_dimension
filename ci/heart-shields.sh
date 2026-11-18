#!/usr/bin/env bash
set -euo pipefail
case "${1:-fallsong}" in
  fallsong) export UWU_SHIELD_CASE=fallsong; pass='FALLSONG AND PETAL SPRAY SERVER PASS' ;;
  original) export UWU_SHIELD_CASE=original; pass='HEART SHIELDS SERVER PASS' ;;
  *) echo 'Use fallsong or original' >&2; exit 2 ;;
esac
bash ci/get-mods.sh
./gradlew jar expeditionJar --stacktrace
mkdir -p build/heart-shield-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury}.jar build/libs/*.jar build/expedition/*.jar build/heart-shield-server/mods/
cd build/heart-shield-server
curl --retry 4 --retry-all-errors -fsSL https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nonline-mode=false\nlevel-seed=152045\ndifficulty=normal\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=600000\n' > server.properties
UWU_HEART_SHIELDS=1 timeout 12m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee shield-server.log
grep -F "$pass" shield-server.log
