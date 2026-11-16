"""Definitions, frozen legacy progress contracts, and original art/lore preservation."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
from zipfile import ZipFile
ROOT=Path(__file__).resolve().parent.parent
BASE='249e0075c6fc8645c4b3b7c91077286213233319'
def old(path):return subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
catalog=json.loads((ROOT/'docs/ACTIVITY-ADVANCEMENTS.json').read_text())
assert len(catalog)==50 and len({a['id'] for a in catalog})==50
allids={a['id'] for a in catalog}|{'uwudimension:a_rather_pretty_place'}
for a in catalog:
    assert a['parent'] in allids
    seen={a['id']};parent=a['parent']
    while parent!='uwudimension:a_rather_pretty_place':
        assert parent not in seen;seen.add(parent);parent=next(x['parent'] for x in catalog if x['id']==parent)
    path='data/uwudimension/advancement/'+a['id'].split(':',1)[1]+'.json'
    data=json.loads((ROOT/'src/main/resources'/path).read_text())
    assert 'rewards' not in data and data['display']['show_toast'] and not data['display']['announce_to_chat']
    assert data['criteria']==a['criteria'] and data['requirements']==a['requirements']
    assert set(sum(data['requirements'],[]))==set(data['criteria'])
oldfiles=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'src/main/resources'],cwd=ROOT,text=True).splitlines()
with ZipFile(sys.argv[1]) as jar:
    for path in oldfiles:
        data=old(path);now=(ROOT/path).read_bytes()
        if path.startswith('src/main/resources/data/') or path.lower().endswith(('.png','.ogg')):
            assert data.replace(b'\r\n',b'\n')==now.replace(b'\r\n',b'\n'),path
            assert jar.read(path.removeprefix('src/main/resources/')).replace(b'\r\n',b'\n')==data.replace(b'\r\n',b'\n'),path
    for a in catalog:assert 'data/uwudimension/advancement/'+a['id'].split(':')[1]+'.json' in jar.namelist()
lang='src/main/resources/assets/uwudimension/lang/en_us.json'
before=json.loads(old(lang));after=json.loads((ROOT/lang).read_text(encoding='utf-8'))
assert all(after[k]==v for k,v in before.items())
assert len(after)-len(before)==100
for file in ['ReachJournal.java','ReachArrival.java','LoreDiscovery.java']:
    p='src/main/java/dev/uwu/dimension/'+file
    assert old(p).replace(b'\r\n',b'\n')==(ROOT/p).read_bytes().replace(b'\r\n',b'\n'),file
events={a['id'].removeprefix('uwudimension:activities/') for a in catalog if all(c['trigger']=='minecraft:impossible' for c in a['criteria'].values())}
source=(ROOT/'src/main/java/dev/uwu/dimension/SakuraAchievements.java').read_text()
body=source.split('EVENTS=Set.of(',1)[1].split(');',1)[0]
assert set(re.findall(r'"([^"]+)"',body))==events
print('ACTIVITY ASSETS PASS 50 stable acyclic nodes; legacy root, all recipes/data, all images/audio, old language and lore source preserved; no new rewards')
