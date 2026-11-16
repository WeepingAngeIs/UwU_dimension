#!/usr/bin/env bash
set -euo pipefail
export UWU_VISIT=network-glitch
bash ci/get-mods.sh
./gradlew jar expeditionJar
root="$(pwd)"
world="$root/build/glitch-network"
export GLITCH_SYNC_DIR="$world/sync"
mkdir -p "$world/mods" "$GLITCH_SYNC_DIR" "$world/shooter" "$world/observer"
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips}.jar build/libs/*.jar build/expedition/*.jar "$world/mods/"
for role in shooter observer; do
  git archive HEAD | tar -x -C "$world/$role"
  cp -a libs "$world/$role/libs"
  mkdir -p "$world/$role/build/probe-compile"
  cp "$root/build/probe-compile/kotlin-stdlib.jar" "$world/$role/build/probe-compile/"
  mkdir -p "$world/$role/run-$role/config"
  printf 'onboardAccessibility:false\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:4\npauseOnLostFocus:false\n' > "$world/$role/run-$role/options.txt"
  printf 'earlyWindowControl = false\n' > "$world/$role/run-$role/config/fml.toml"
done
cd "$world"
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=152045\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-tick-time=600000\n' > server.properties
java -Xms512m -Xmx2500m @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui > server.log 2>&1 &
server_pid=$!
observer_pid=
shooter_pid=
trap 'kill "$server_pid" ${observer_pid:-} ${shooter_pid:-} 2>/dev/null || true' EXIT
for i in {1..180}; do
  grep -q 'Done (' server.log && break
  if ! kill -0 "$server_pid" 2>/dev/null; then tail -100 server.log; exit 1; fi
  sleep 2
done
grep -F 'Done (' server.log
for role in observer shooter; do
  (
    cd "$world/$role"
    export GLITCH_ROLE="$role" LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null JAVA_TOOL_OPTIONS=-Xmx2200m
    timeout 18m xvfb-run -a -s '-screen 0 960x600x24' ./gradlew --no-daemon -I ci/glitch-network.init.gradle -PfieldCamera runClient > "$world/$role.log" 2>&1
  ) &
  if [[ "$role" == observer ]]; then observer_pid=$!; else shooter_pid=$!; fi
done
wait "$observer_pid"
wait "$shooter_pid"
grep -F 'NETWORK GLITCH SERVER READY two real remote players' "$world/server.log"
grep -F 'NETWORK GLITCH SERVER LAUNCH PASS' "$world/server.log"
grep -F 'NETWORK GLITCH SERVER ADVANCE PASS' "$world/server.log"
grep -F 'NETWORK GLITCH OBSERVER PASS' "$world/observer.log"
grep -F 'NETWORK GLITCH SHOOTER PASS' "$world/shooter.log"
for frame in held flight changed; do test -s "$world/observer/run-observer/screenshots/network-glitch-$frame.png"; done
