"""Fetch exact locally inspected optional client versions for disposable hosted tests."""
import hashlib
import json
from pathlib import Path
import time
import urllib.request

root=Path(__file__).resolve().parent
out=Path("build/weapon-compat")
out.mkdir(parents=True,exist_ok=True)
for entry in json.loads((root/"fixtures/weapon-compat/downloads.json").read_text()):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(entry["url"],timeout=90) as response:
                data=response.read()
            break
        except Exception:
            if attempt==3:
                raise
            time.sleep(2)
    if hashlib.sha256(data).hexdigest()!=entry["sha256"]:
        raise AssertionError("Optional dependency digest differs "+entry["file"])
    (out/entry["file"]).write_bytes(data)
    print("PINNED COMPAT",entry["file"])
