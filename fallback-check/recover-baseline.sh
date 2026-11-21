#!/usr/bin/env bash
set -euo pipefail
# This script is only invoked on the public GitHub-hosted validation runner.
test "${GITHUB_ACTIONS:-false}" = true
baseline_sha=e16af6a2a1a00cb68291b6c9611d4604a0b20171
baseline_digest=7e402979865fa5e56edc5f24d26fff9704e99fa53b9f821b8e074241bb4cd9d8
baseline="${RUNNER_TEMP:?}/playtest-9.jar"
if gh api 'repos/WeepingAngeIs/z-UwU_dm/actions/artifacts/14699111060/zip' > "$baseline"; then
    printf '%s  %s\n' "$baseline_digest" "$baseline" | sha256sum --check
    echo 'PLAYTEST 9 BASELINE recovered exact original artifact'
else
    git fetch --no-tags --depth=1 origin "$baseline_sha"
    before="$RUNNER_TEMP/uwu-playtest-9-source"
    git worktree add --detach "$before" "$baseline_sha"
    (
        cd "$before"
        test "$(git rev-parse HEAD)" = "$baseline_sha"
        bash ./gradlew --no-daemon --no-build-cache --max-workers=2 jar
        cp build/libs/UwU-Dimension-1.21.1-1.5.0-beta.13-combined-rc.2-playtest.9.jar "$baseline"
    )
    echo 'PLAYTEST 9 BASELINE rebuilt exact source on GitHub Actions (archive bytes may differ)'
fi
sha256sum "$baseline"
