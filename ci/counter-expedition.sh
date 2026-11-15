#!/usr/bin/env bash
set -euo pipefail
bash ci/get-mods.sh
./gradlew expeditionJar
mkdir -p build/counter-server/mods
cp libs/{cobblemon,lootr,kotlinforforge,another_furniture-neoforge-4.0.2,simplyswords,fzzy_config,architectury}.jar build/libs/*.jar build/expedition/*.jar build/counter-server/mods/
cd build/counter-server
curl --retry 4 -fsSL 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar' -o installer.jar
java -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\nmax-tick-time=600000\n' > server.properties
UWU_ARSENAL=1 timeout 18m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee counter.log
grep -F 'UWU COUNTER ARSENAL PASSED' counter.log
python3 - <<'PY'
from pathlib import Path
import re
p = Path('world/serverconfig/uwudimension-server.toml')
p.parent.mkdir(parents=True, exist_ok=True)
config = p.read_text() if p.exists() else ''
config, count = re.subn(r'(?m)^([ \t]*weaponAcquisitionEnabled[ \t]*=[ \t]*)true[ \t]*$', r'\g<1>false', config)
if count == 0:
    assert not re.search(r'(?m)^[ \t]*weaponAcquisitionEnabled[ \t]*=', config), 'Unexpected existing policy value'
    config = 'weaponAcquisitionEnabled = false\n' + config
else:
    assert count == 1, 'Duplicate acquisition policy values'
p.write_text(config)
PY
UWU_ROSTER_POLICY=1 timeout 12m java -Xms512M -Xmx5G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee counter-policy.log
grep -F 'UWU RETUNING RESTART POLICY PASS' counter-policy.log
