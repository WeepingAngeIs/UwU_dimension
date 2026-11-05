#!/usr/bin/env bash
# Called inside an already saved and stopped field-server directory.
set -euo pipefail
test -s mirror-checkpoint.properties
timeout 8m java -Duwu.bossReload=true -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee mirror-restart.log
grep -F 'MIRROR COURT DISK RESTART PASS' mirror-restart.log
