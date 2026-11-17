"""Freeze the verified beta.10 resources and every unrelated production source file."""
from pathlib import Path
from zipfile import ZipFile
import subprocess,sys
ROOT=Path(__file__).resolve().parent.parent
BASE='3731d3606a141bde49ba08635dbfd86e11817ed4'
changed={'HeartwaterFeature.java','BlossomVillage.java','MoonveilTreehouse.java','VillageSakura.java','InteriorFurnishings.java','mixin/RealmVillageStructuresMixin.java'}
files=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'src/main'],cwd=ROOT,text=True).splitlines()
count=0
with ZipFile(sys.argv[1]) as jar:
 for path in files:
  previous=subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
  if path.startswith('src/main/resources/') and not path.endswith('META-INF/neoforge.mods.toml'):
   assert (ROOT/path).read_bytes()==previous,path
   assert jar.read(path.removeprefix('src/main/resources/'))==previous,path
   count+=1
  if path.startswith('src/main/java/dev/uwu/dimension/') and path.removeprefix('src/main/java/dev/uwu/dimension/') not in changed:
   assert (ROOT/path).read_bytes()==previous,path
 assert not any(n.startswith('dev/uwu/test/') for n in jar.namelist())
print('OVERLAP ASSETS PASS',count,'exact beta.10 resources, all 50 achievements, Cupid/guardian/lore/saved-data sources unchanged; no test classes in release')
