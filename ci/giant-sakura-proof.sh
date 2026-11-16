#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew expeditionJar
mkdir -p build/giant-sakura-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury}.jar build/libs/*.jar build/expedition/*.jar build/giant-sakura-server/mods/
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' -o build/giant-sakura-server/mods/opac.jar
echo '646fd9796ef2dfd1132e22aacf1a417639233d90fb7eb862a718badfed955d54714c88327b7ddc651f98cd5b7d2a80e8c91233d2232680d4998fdd10ef392ffb  build/giant-sakura-server/mods/opac.jar' | sha512sum --check
cd build/giant-sakura-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' > server.properties
UWU_GIANT_SAKURA=1 timeout 25m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee giant-sakura.log
grep -F 'GIANT SAKURA SAPLING PASS' giant-sakura.log
grep -F 'GIANT SAKURA NATURAL PASS' giant-sakura.log
grep -F 'GIANT SAKURA OPAC PASS' giant-sakura.log
grep -F 'GIANT SAKURA ALL SERVER CHECKS PASSED' giant-sakura.log
