#!/usr/bin/env bash
set -euo pipefail
export UWU_VISIT=network-cupid
bash ci/get-mods.sh
./gradlew jar expeditionJar
root="$(pwd)"
world="$root/build/cupid-network"
mkdir -p "$world/mods" run/mods run/config/smart_particles
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury,simplytooltips}.jar build/libs/*.jar build/expedition/*.jar "$world/mods/"
curl --retry 4 --retry-all-errors -fsSL https://cursemaven.com/curse/maven/smart-particles-1420751/7470043/smart-particles-1420751-7470043.jar -o run/mods/smart_particles.jar
curl --retry 4 --retry-all-errors -fsSL https://cursemaven.com/curse/maven/particle-core-985426/7581040/particle-core-985426-7581040.jar -o run/mods/particle_core.jar
printf '%s\n' 'a19d81c5a06180dd8539260c90bf42a5faa2a867ae215dc79b18c8c0b4833672  run/mods/smart_particles.jar' '38c05981d1dc8428c7ab1130b9ec7f05bca1b70f909aba9c454c9611702a8dda  run/mods/particle_core.jar' | sha256sum --check
printf '%s\n' '{"particleLimit":5000,"smartCameraCulling":true}' > run/config/smart_particles/config.json
printf 'version=1\ndisableParticles=false\nbyTypeReductions={}\nmaxParticlesPerSheet=16384\nparticleRenderDistanceMultiplier=1.0\nasynchronousTicking=true\nreduceAllChance=0.0\nreduceDecreasedChance=0.0\nturnOffPotionParticlesV2=["NONE"]\n' > run/config/particle_core_config.toml
printf 'onboardAccessibility:false\ntutorialStep:none\nrenderDistance:5\nsimulationDistance:5\nparticles:2\npauseOnLostFocus:false\n' > run/options.txt
printf 'earlyWindowControl = false\n' > run/config/fml.toml
cd "$world"
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25574\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=152045\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-tick-time=240000\n' > server.properties
java -Xms512m -Xmx2500m @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui > server.log 2>&1 &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT
for i in {1..180}; do
    grep -q 'Done (' server.log && break
    if ! kill -0 "$server_pid" 2>/dev/null; then tail -100 server.log; exit 1; fi
    sleep 2
done
grep -F 'Done (' server.log
cd "$root"
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null JAVA_TOOL_OPTIONS=-Xmx3000m
timeout 18m xvfb-run -a -s '-screen 0 1280x800x24' ./gradlew -PfieldCamera runClient | tee "$world/client.log"
grep -F 'NETWORK CUPID BOTH SERVER HITS PASS' "$world/server.log"
grep -F 'NETWORK CUPID VISUAL PASS' "$world/client.log"
for frame in 1-draw 1-trail 1-entity-impact 2-draw 2-trail 2-block-impact; do
    test -s "run/screenshots/network-cupid-$frame.png"
done

grep -F "NETWORK GUARDIAN FAMILY PASS" "$world/client.log"
test -s run/screenshots/network-guardian-family.png
