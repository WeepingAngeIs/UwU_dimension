"""Release contract against beta.12's actual source assets and saved identities."""
import gzip
import json
from pathlib import Path
import subprocess
import sys
from zipfile import ZipFile

root=Path(__file__).resolve().parent.parent
base="11816cbda60c4e8e53d829b664a42cc37fd2a282"
def old(name):
    return subprocess.check_output(["git","show",base+":"+name],cwd=root)
paths=subprocess.check_output(["git","ls-tree","-r","--name-only",base,"src/main/resources"],cwd=root,text=True).splitlines()
with ZipFile(sys.argv[1]) as jar:
    for p in paths:
        if p.endswith((".png",".ogg")) or p.startswith("src/main/resources/data/"):
            data=old(p).replace(b"\r\n",b"\n")
            assert (root/p).read_bytes().replace(b"\r\n",b"\n")==data,p
            assert jar.read(p.removeprefix("src/main/resources/")).replace(b"\r\n",b"\n")==data,p
    records=json.loads((root/"ci/fixtures/weapon-presentation.json").read_text())
    assert len({r["item"] for r in records})==150
    for r in records:
        model=json.loads(jar.read("assets/uwudimension/models/item/"+r["model"]+".json"))
        assert model["display"]==r["display"],r["model"]
        for override in model.get("overrides",[]):
            target=override["model"].split(":")[1]
            assert "assets/uwudimension/models/"+target+".json" in jar.namelist(),target
    for name in ["cupid_bow","ripple_bow"]:
        model=json.loads(jar.read("assets/uwudimension/models/item/"+name+".json"))
        draws=[o for o in model["overrides"] if "uwudimension:draw" in o["predicate"] or "uwudimension:drawing" in o["predicate"]]
        assert len(draws)==4,(name,draws)
        assert len({o["model"] for o in draws})==4
        assert [o["predicate"].get("uwudimension:draw",0) for o in draws]==[0,.4,.75,1]
    assert not any(p.startswith("dev/uwu/test/") for p in jar.namelist())
for p in ["ReachJournal.java","ReachArrival.java","LoreDiscovery.java","AuthoredFootprints.java","SakuraAchievements.java"]:
    name="src/main/java/dev/uwu/dimension/"+p
    assert (root/name).read_bytes().replace(b"\r\n",b"\n")==old(name).replace(b"\r\n",b"\n"),p
assert json.loads(old("src/main/resources/assets/uwudimension/counter_roster.json"))==json.loads((root/"src/main/resources/assets/uwudimension/counter_roster.json").read_text())
print("WEAPON POLISH ASSETS PASS original sprites/audio/data, lore, structure protection, achievements, counter IDs, 150 presentation identities, four bow stages, no probe classes")
