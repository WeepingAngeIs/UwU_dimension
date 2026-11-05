#!/usr/bin/env bash
set -euo pipefail
mkdir -p libs
download() {
  curl --retry 4 --retry-all-errors -fsSL "$1" -o "$2"
}
download 'https://cdn.modrinth.com/data/MdwFAVRL/versions/xsVHrAsY/Cobblemon-neoforge-1.8.4%2B1.21.1.jar' libs/cobblemon.jar
download 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' libs/lootr.jar
download 'https://cdn.modrinth.com/data/ordsPcFz/versions/uhJhCT7X/kotlinforforge-5.12.0-all.jar' libs/kotlinforforge.jar
sha256sum --check ci/dependencies.sha256
