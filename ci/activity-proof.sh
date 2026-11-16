#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew jar expeditionJar
git fetch origin 249e0075c6fc8645c4b3b7c91077286213233319 --depth=1
python ci/activity-assets.py build/libs/UwU-Dimension-*.jar
mkdir -p build/activity-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips}.jar build/libs/*.jar build/expedition/*.jar build/activity-server/mods/
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' -o build/activity-server/mods/opac.jar
echo '77eadbbb7cbd7298d9a99070ebde552b765dd91bdad5054c9f845a17d2931765  build/activity-server/mods/opac.jar' | sha256sum -c -
cd build/activity-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' > server.properties
if [[ "$ACTIVITY_WORLD" = upgrade ]]; then
  git worktree add --detach "$RUNNER_TEMP/activity-beta9" 249e0075c6fc8645c4b3b7c91077286213233319
  (cd "$RUNNER_TEMP/activity-beta9" && ./gradlew jar)
  mkdir candidate
  mv mods/UwU-Dimension-*.jar candidate/
  cp "$RUNNER_TEMP/activity-beta9"/build/libs/*.jar mods/
  UWU_ACTIVITY_BASELINE=1 timeout 10m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee baseline.log
  grep -F 'ACTIVITY BETA9 BASELINE SAVED' baseline.log
  mv mods/UwU-Dimension-*.jar ./
  mv candidate/*.jar mods/
fi
UWU_ACTIVITY_SERVER=1 UWU_ACTIVITY_TEST=1 timeout 25m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee first.log
grep -F 'ACTIVITY SERVER PASS' first.log
if [[ "$ACTIVITY_WORLD" = upgrade ]]; then grep -F 'ACTIVITY LEGACY UPGRADE PASS' first.log; fi
UWU_ACTIVITY_RESTART=1 timeout 10m java -Xms512m -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee restart.log
grep -F 'ACTIVITY RESTART PASS' restart.log
