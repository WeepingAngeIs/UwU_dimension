#!/usr/bin/env bash
# Preserve thread stacks of a sustained server tick or shutdown wait in hosted evidence.
set +e
out="$(realpath -m "$1")"
mkdir -p "$out"
while sleep 75; do
  jcmd -l | while read -r pid main rest; do
    case "$main $rest" in
      *BootstrapLauncher*|*DevLaunch*|*bootstraplauncher*|*neoforged.fml*|*forgeclient*|*forgeserver*)
        timeout 10s jcmd "$pid" Thread.print -l > "$out/threads-$pid.tmp" 2>/dev/null && mv "$out/threads-$pid.tmp" "$out/threads-$pid.txt" ;;
    esac
  done
done
