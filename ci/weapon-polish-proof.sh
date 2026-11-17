#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew jar expeditionJar
mkdir -p build/weapon-polish-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips}.jar build/libs/*.jar build/expedition/*.jar build/weapon-polish-server/mods/
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' -o build/weapon-polish-server/mods/opac.jar
echo '77eadbbb7cbd7298d9a99070ebde552b765dd91bdad5054c9f845a17d2931765  build/weapon-polish-server/mods/opac.jar' | sha256sum -c -
cd build/weapon-polish-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' > server.properties
UWU_POLISH_SERVER=first timeout 15m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee first.log
grep -F 'WEAPON POLISH SERVER PASS first' first.log
UWU_POLISH_SERVER=restart timeout 10m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee restart.log
grep -F 'WEAPON POLISH SERVER PASS restart' restart.log
