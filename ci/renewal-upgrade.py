"""Create a minimal real 1.4.9 save with a recognizable block on either side of the locket."""
import os
import queue
import subprocess
import threading
import time
from pathlib import Path

args = ['java','-Xms384M','-Xmx5G','@libraries/net/neoforged/neoforge/21.1.249/unix_args.txt','nogui']
with Path("baseline.log").open("w") as log, subprocess.Popen(args, stdin=subprocess.PIPE,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1) as proc:
    lines = queue.Queue()
    def capture():
        for line in proc.stdout:
            log.write(line)
            lines.put(line)
        lines.put(None)
    threading.Thread(target=capture,daemon=True).start()
    def wait_for(marker):
        end=time.monotonic()+300
        while time.monotonic()<end:
            line=lines.get(timeout=max(1,end-time.monotonic()))
            if line is None: raise RuntimeError("Baseline stopped before "+marker)
            if marker in line: return
        raise TimeoutError(marker)
    def command(cmd):
        proc.stdin.write(cmd+"\n")
        proc.stdin.flush()
    try:
        wait_for("Done (")
        for dim in ["minecraft:overworld","uwudimension:sakura_reach"]:
            command("execute in "+dim+" run forceload add 320 320")
        for dim in ["minecraft:overworld","uwudimension:sakura_reach"]:
            for attempt in range(60):
                command("execute in "+dim+" if loaded 320 185 320 run setblock 320 185 320 minecraft:lodestone")
                command("execute in "+dim+" if block 320 185 320 minecraft:lodestone run say FIXTURE-"+dim)
                try:
                    until=time.monotonic()+2
                    while time.monotonic()<until:
                        line=lines.get(timeout=max(.1,until-time.monotonic()))
                        if line is None: raise RuntimeError("Baseline stopped")
                        if "FIXTURE-"+dim in line: break
                    else: continue
                    break
                except queue.Empty: pass
            else: raise RuntimeError("Fixture placement failed: "+dim)
            if dim=="uwudimension:sakura_reach":
                # This player-built corner uses the legacy native village materials. Keep its
                # exact states as evidence on both the upgrade boot and the following restart.
                walls=["spruce_planks","oak_fence","oak_stairs[facing=west]","cobblestone"]
                for x,state in enumerate(walls,321):
                    command(f"execute in {dim} run setblock {x} 185 320 minecraft:{state}")
                    marker=f"ARCH-OLD-{x}"
                    command(f"execute in {dim} if block {x} 185 320 minecraft:{state} run say {marker}")
                    wait_for(marker)
            command("execute in "+dim+" run forceload remove 320 320")
        command("save-all flush"); wait_for("Saved the game")
        command("stop")
        if proc.wait(timeout=90): raise RuntimeError("Baseline shutdown failed")
    finally:
        if proc.poll() is None: proc.kill()
