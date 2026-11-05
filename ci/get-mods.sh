#!/usr/bin/env bash
set -euo pipefail
mkdir -p libs
download() {
  curl --retry 4 --retry-all-errors -fsSL "$1" -o "$2"
}
download 'https://cdn.modrinth.com/data/MdwFAVRL/versions/xsVHrAsY/Cobblemon-neoforge-1.8.4%2B1.21.1.jar' libs/cobblemon.jar
download 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' libs/lootr.jar
download 'https://edge.forgecdn.net/files/8335/665/kotlinforforge-5.12.0-all.jar' libs/kotlinforforge.jar
download 'https://edge.forgecdn.net/files/8530/938/reactivemusic-neoforge-1.3.5%2b1.21.1.jar' libs/reactivemusic.jar
download 'https://edge.forgecdn.net/files/7355/747/another_furniture-neoforge-4.0.2.jar' libs/another_furniture-neoforge-4.0.2.jar
sha256sum --check ci/dependencies.sha256


mkdir -p build/probe-compile
unzip -p libs/kotlinforforge.jar META-INF/jarjar/kotlin-stdlib-2.4.0.jar > build/probe-compile/kotlin-stdlib.jar
