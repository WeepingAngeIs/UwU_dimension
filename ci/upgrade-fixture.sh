#!/usr/bin/env bash
# Boot Minecraft with the previous released mod, place real player-style saved data, then exchange JARs.
set -euo pipefail
mkdir -p ../new-release
mv mods/UwU-Dimension-*.jar mods/heartwater-hosted-probe-*.jar ../new-release/
baseline="$RUNNER_TEMP/uwu-100-source"
git worktree add --detach "$baseline" 5bd010392a1304dee7c3b1790e0307ae15a6a6b1
(cd "$baseline" && ./gradlew build)
cp "$baseline"/build/libs/UwU-Dimension-*.jar mods/
python ../../ci/old-save.py
mv mods/UwU-Dimension-1.21.1-1.0.0.jar ../
mv ../new-release/*.jar mods/
