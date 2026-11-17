#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew jar expeditionJar
git fetch origin 3731d3606a141bde49ba08635dbfd86e11817ed4 --depth=1
python ci/overlap-assets.py build/libs/UwU-Dimension-*.jar
mkdir -p build/overlap-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips}.jar build/libs/*.jar build/expedition/*.jar build/overlap-server/mods/
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' -o build/overlap-server/mods/opac.jar
echo '77eadbbb7cbd7298d9a99070ebde552b765dd91bdad5054c9f845a17d2931765  build/overlap-server/mods/opac.jar' | sha256sum -c -
cd build/overlap-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=%s\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' "$OVERLAP_SEED" > server.properties
UWU_OVERLAP=first timeout 25m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee first.log
grep -F 'OVERLAP SERVER PASS' first.log
grep -F 'OVERLAP ROTATIONS PASS' first.log
grep -F 'OVERLAP DIRECT-WRITE LIMIT CONFIRMED' first.log
UWU_OVERLAP=restart timeout 10m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee restart.log
grep -F 'OVERLAP RESTART PASS' restart.log
