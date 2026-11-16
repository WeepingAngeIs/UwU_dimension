"""Keep each hosted client's native LWJGL libraries isolated, using Mojang's pinned manifest."""
import hashlib,io,json,sys,urllib.request,zipfile
from pathlib import Path

def download(url):
    with urllib.request.urlopen(url,timeout=90) as response:return response.read()

manifest=json.loads(download('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
entry=next(v for v in manifest['versions'] if v['id']=='1.21.1')
raw=download(entry['url']);assert hashlib.sha1(raw).hexdigest()==entry['sha1']
version=json.loads(raw)
target=Path(sys.argv[1]);target.mkdir(parents=True,exist_ok=True)
records=[]
for library in version['libraries']:
    artifact=library.get('downloads',{}).get('artifact',{})
    path=artifact.get('path','')
    if not path.startswith('org/lwjgl/') or not path.endswith('-natives-linux.jar'):continue
    data=download(artifact['url']);assert hashlib.sha1(data).hexdigest()==artifact['sha1']
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in z.namelist():
            if name.endswith('.so'):(target/Path(name).name).write_bytes(z.read(name))
    records.append({'path':path,'sha1':artifact['sha1']})
assert (target/'liblwjgl.so').is_file() and (target/'liblwjgl_opengl.so').is_file() and (target/'libglfw.so').is_file()
(target/'manifest.json').write_text(json.dumps(records,indent=2)+'\n')
print('Verified private LWJGL native set',len(records),'Mojang artifacts')
