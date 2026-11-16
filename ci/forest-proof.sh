#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew jar expeditionJar
mkdir -p build/forest-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury}.jar build/libs/*.jar build/expedition/*.jar build/forest-server/mods/
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' -o build/forest-server/mods/opac.jar
echo '77eadbbb7cbd7298d9a99070ebde552b765dd91bdad5054c9f845a17d2931765  build/forest-server/mods/opac.jar' | sha256sum -c -
cd build/forest-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=%s\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' "$UWU_FIELD_SEED" > server.properties
if [[ "${UWU_SOURCE_WORLD:-fresh}" = upgrade ]]; then
  git fetch origin b0e11e4cf38bfdf12b08701a65822a3619c3d9ee --depth=1
  git worktree add --detach "$RUNNER_TEMP/forest-beta7" b0e11e4cf38bfdf12b08701a65822a3619c3d9ee
  (cd "$RUNNER_TEMP/forest-beta7" && ./gradlew jar)
  mkdir candidate
  mv mods/UwU-Dimension-*.jar candidate/
  cp "$RUNNER_TEMP/forest-beta7"/build/libs/*.jar mods/
  UWU_FOREST_BASELINE=1 timeout 10m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee baseline.log
  grep -F 'FOREST BETA7 BASELINE SAVED' baseline.log
  mv mods/UwU-Dimension-*.jar ./
  mv candidate/*.jar mods/
  UWU_FOREST_UPGRADE=1 timeout 10m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee forest.log
  grep -F 'FOREST BETA7 UPGRADE PASS' forest.log
  exit 0
fi
UWU_FOREST=1 timeout 25m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee forest.log
grep -F 'FOREST NATURAL PASS' forest.log
grep -F 'FOREST SAPLING PASS' forest.log
grep -F 'FOREST 3D CANOPY PASS' forest.log
grep -F 'GIANT SAKURA OPAC PASS' forest.log
