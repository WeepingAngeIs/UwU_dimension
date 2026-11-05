"""Prepare a real previous-release save on the hosted test server."""
import subprocess, time, threading, queue, sys
proc=subprocess.Popen(['java','-Xms384M','-Xmx4G','@libraries/net/neoforged/neoforge/21.1.249/unix_args.txt','nogui'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
lines=queue.Queue()
def reader():
    for line in proc.stdout:
        lines.put(line)
    lines.put(None)
threading.Thread(target=reader,daemon=True).start()
def command(text):
    proc.stdin.write(text+'\n');proc.stdin.flush()
def wait_for(text,seconds):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        line=lines.get(timeout=max(1,end-time.monotonic()))
        if line is None: raise RuntimeError('Baseline server terminated before '+text)
        if text in line: print(line.strip(),flush=True);return
    raise RuntimeError(text)
try:
    wait_for('Done (',200)
    cmd='execute in uwudimension:heartwater run '
    command(cmd+'forceload add 0 0 15 110')
    time.sleep(8)
    command(cmd+'setblock 2 82 2 minecraft:lodestone')
    wait_for('Changed the block',60)
    command(cmd+'summon uwudimension:petal_keeper 3.5 81 3.5 {PersistenceRequired:1b,Tags:["uwu_upgrade_fixture"],Companion:[I;1,2,3,4],Settled:1b}')
    wait_for('Summoned new',45)
    command(cmd+'data get block 0 53 103')
    wait_for('rose_bathhouse',60)
    command('save-all flush');wait_for('Saved the game',45)
    command('stop')
    if proc.wait(timeout=90):raise RuntimeError('Unclean baseline shutdown')
    print('BASELINE 1.0.0 SAVED real old chamber, keeper and lodestone',flush=True)
finally:
    if proc.poll() is None:proc.kill()
