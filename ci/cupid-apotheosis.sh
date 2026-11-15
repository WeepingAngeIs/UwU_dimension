#!/usr/bin/env bash
# Match the three required jars in the CurseForge test profile. Use the official
# files in the disposable hosted server only.
set -euo pipefail
destination=${1:?Pass the disposable server mods directory}
fetch() {
  local upstream="$1" name="$2" digest="$3"
  curl --retry 4 --retry-all-errors --fail --silent --show-error --location \
    "https://edge.forgecdn.net/files/$upstream/$name" --output "$destination/$name"
  printf '%s  %s\n' "$digest" "$destination/$name" | sha256sum --check -
}
fetch 8993/983 Apotheosis-1.21.1-8.9.0.jar 23a8b4a21d0d8744f097c77c8a96fcba14c4b95f3b1bb542412f40dab272872e
fetch 8463/693 Placebo-1.21.1-9.9.2.jar 1a844a5b081813b1edb82656329e54d38389ed470f6a6516a5887f5303d7daad
fetch 9006/294 ApothicAttributes-1.21.1-2.11.0.jar 17c42bb6dd5c0d820fe090e1bb5959578cd5ab7b70caf98a2a59b319fcc7e1ba
