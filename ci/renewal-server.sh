#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
if [[ "${UWU_SOURCE_WORLD:-}" = upgrade-beta.1 ]]; then ./gradlew expeditionJar legacyArchitectureJar
else ./gradlew expeditionJar; fi
mkdir -p build/renewal-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips}.jar build/libs/*.jar build/expedition/*.jar build/renewal-server/mods/
if [[ "$UWU_FIELD_SEED" = 152045 ]]; then
  export UWU_TEST_APOTHEOSIS=1
  bash ci/cupid-apotheosis.sh build/renewal-server/mods
  curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' -o build/renewal-server/mods/open-parties-and-claims.jar
  echo "77eadbbb7cbd7298d9a99070ebde552b765dd91bdad5054c9f845a17d2931765  build/renewal-server/mods/open-parties-and-claims.jar" | sha256sum -c -
else
  export UWU_TEST_APOTHEOSIS=0
fi
cd build/renewal-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nonline-mode=false\nlevel-seed=%s\nview-distance=3\nsimulation-distance=3\nmax-tick-time=240000\n' "$UWU_FIELD_SEED" > server.properties
if [[ "$UWU_FIELD_SEED" = 4372191 ]]; then
  baseline=59b2281e29a1c634c7f3b508d2b46cc804b0a565
  if [[ "${UWU_SOURCE_WORLD:-}" = upgrade-beta.1 ]]; then baseline=fa896723ce4824c184c86b94b294375def3720b1; fi
  git worktree add --detach "$RUNNER_TEMP/uwu-baseline" "$baseline"
  (cd "$RUNNER_TEMP/uwu-baseline" && ./gradlew jar)
  mkdir candidate
  mv mods/{UwU-Dimension-*,heartwater-hosted-probe-*}.jar candidate/
  cp "$RUNNER_TEMP/uwu-baseline"/build/libs/*.jar mods/
  if [[ "${UWU_SOURCE_WORLD:-}" = upgrade-beta.1 ]]; then cp ../legacy-sampling/legacy-architecture-sampler-*.jar mods/; fi
  python ../../ci/renewal-upgrade.py
  if [[ "${UWU_SOURCE_WORLD:-}" = upgrade-beta.1 ]]; then
    test -s old-native-dusk.txt
    mv mods/legacy-architecture-sampler-*.jar ../
  fi
  mv world baseline-world
  cp -a baseline-world world
  mv mods/UwU-Dimension-*.jar ../
  mv candidate/*.jar mods/
fi
for visit in first restart; do
  UWU_RENEWAL=1 timeout 24m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$visit.log"
  grep -F 'SAKURA FULL SERVER VISIT PASS' "$visit.log"
  if [[ "$visit" = first ]]; then
    grep -F 'VILLAGE RENEWAL PASS' first.log
    if [[ "$UWU_FIELD_SEED" = 152045 ]]; then grep -F 'VILLAGE LIFE PASS' first.log; fi
    grep -F 'MIRROR COURT PASS' first.log
    grep -F 'SAKURA RENEWAL SERVER PASS' first.log
    test -s mirror-checkpoint.properties
  else
    grep -F 'MIRROR COURT DISK RESTART PASS' restart.log
    grep -F 'SAKURA DISK RESTART PASS' restart.log
  fi
done
