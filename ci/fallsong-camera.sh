#!/usr/bin/env bash
set -euo pipefail
sudo apt-get update -qq
sudo apt-get install -y -qq xvfb libgl1-mesa-dri libopenal1
mkdir -p run/config
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:4\npauseOnLostFocus:false\nenableVsync:false\n' > run/options.txt
printf 'earlyWindowControl = false\n' > run/config/fml.toml
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null JAVA_TOOL_OPTIONS=-Xmx4G
timeout 14m xvfb-run -a -s '-screen 0 1280x800x24' ./gradlew -I ci/fallsong-camera.init.gradle -PfieldCamera runClient > build/fallsong-client.log 2>&1 || { tail -100 build/fallsong-client.log; exit 1; }
grep -F 'FALLSONG CLIENT CAPTURE COMPLETE' build/fallsong-client.log
for view in window-rest window-charged waterfall-jump petalfold-bloom bubble-{front,side,rear,first,impact}; do
    test -s "run/screenshots/fallsong-$view.png"
done
