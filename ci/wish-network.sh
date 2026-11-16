#!/usr/bin/env bash
set -euo pipefail
export UWU_VISIT=network-wishes
bash ci/get-mods.sh
curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gF3BGWvG/versions/yWmXD7GW/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' -o libs/opac.jar
echo '77eadbbb7cbd7298d9a99070ebde552b765dd91bdad5054c9f845a17d2931765  libs/opac.jar' | sha256sum -c -
if [[ "$WISH_COMBAT" = bettercombat ]]; then
  curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/5sy6g3kz/versions/VhIOvcXP/bettercombat-neoforge-2.4.0%2B1.21.1.jar' -o libs/bettercombat.jar
  curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/gedNE4y2/versions/HJZB6bmA/player-animation-lib-forge-2.0.4%2B1.21.1.jar' -o libs/playeranimator.jar
  curl --retry 4 -fsSL 'https://cdn.modrinth.com/data/9s6osm5g/versions/izKINKFg/cloth-config-15.0.140-neoforge.jar' -o libs/cloth-config.jar
  printf '%s\n' '0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a  libs/bettercombat.jar' 'dbe5de45f5cd60c0e5e47af14e6d564534a98456e973cf670cb881f6938eee92  libs/playeranimator.jar' '65e722e0d98431a07c45f8bdd8d529a217cc8c175fde1740248bd5c1b4f3c0d4  libs/cloth-config.jar' | sha256sum -c -
fi
./gradlew jar expeditionJar
root="$(pwd)"
world="$root/build/wish-network"
export WISH_SYNC_DIR="$world/sync"
mkdir -p "$world/mods" "$WISH_SYNC_DIR" "$world/archer" "$world/witness"
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips,opac}.jar build/libs/*.jar build/expedition/*.jar "$world/mods/"
if [[ "$WISH_COMBAT" = bettercombat ]]; then cp libs/{bettercombat,playeranimator,cloth-config}.jar "$world/mods/"; fi
for role in archer witness; do
  git archive HEAD | tar -x -C "$world/$role"
  cp -a libs "$world/$role/libs"
  mkdir -p "$world/$role/build/probe-compile" "$world/$role/run-$role/config"
  cp build/probe-compile/kotlin-stdlib.jar "$world/$role/build/probe-compile/"
  printf 'onboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:20\nenableVsync:false\npauseOnLostFocus:false\nkey_key.sneak:key.keyboard.left.control\n' > "$world/$role/run-$role/options.txt"
  printf 'earlyWindowControl = false\n' > "$world/$role/run-$role/config/fml.toml"
done
cd "$world"
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25576\nonline-mode=false\npvp=true\nlevel-type=minecraft:flat\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=600000\n' > server.properties
java -Xms256m -Xmx2000m @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui > server.log 2>&1 &
server_pid=$!
archer_pid=
witness_pid=
trap 'kill "$server_pid" ${archer_pid:-} ${witness_pid:-} 2>/dev/null || true' EXIT
for i in {1..180}; do
  grep -q 'Done (' server.log && break
  if ! kill -0 "$server_pid" 2>/dev/null; then tail -100 server.log; exit 1; fi
  sleep 2
done
grep -F 'Done (' server.log
if [[ "$WISH_COMBAT" = bettercombat ]]; then
  sudo tc qdisc add dev lo root netem delay 120ms 25ms
fi
for role in witness archer; do
  (
    cd "$world/$role"
    export WISH_ROLE="$role" LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null JAVA_TOOL_OPTIONS=-Xmx2000m
    timeout 24m xvfb-run -a -s '-screen 0 854x480x24' ./gradlew --no-daemon -I ci/wish-network.init.gradle -PfieldCamera runClient > "$world/$role.log" 2>&1
  ) &
  if [[ "$role" = archer ]]; then archer_pid=$!; else witness_pid=$!; fi
done
wait "$archer_pid"
wait "$witness_pid"
grep -F 'NETWORK WISH FULL PASS' "$world/server.log"
grep -F 'CUPID WISH PASS' "$world/server.log"
grep -F 'CUPID OPAC WISH PASS' "$world/server.log"
grep -F 'WISH CLIENT PASS archer' "$world/archer.log"
grep -F 'WISH CLIENT LOGOUT witness' "$world/witness.log"
python "$root/ci/renewal-assets.py" "$root"/build/libs/UwU-Dimension-*.jar
python "$root/ci/anya-tree-contract.py" "$root"/build/libs/UwU-Dimension-*.jar
