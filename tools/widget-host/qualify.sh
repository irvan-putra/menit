#!/usr/bin/env bash
set -euo pipefail
mkdir -p host-evidence
adb root
adb wait-for-device
adb shell wm size 1080x2340
adb shell wm density 420
adb shell settings put system font_scale 1.0
for apk in host-apks/*.apk; do adb install -r "$apk"; done
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r tools/widget-host/app/build/outputs/apk/debug/app-debug.apk
# Synthetic profiles only in a disposable emulator. This does not enable demo in release defaults.
for p in menit tumbuh denyut kemas; do
  adb shell am force-stop "com.vanipy.$p"
  printf '<map><boolean name="demo_mode" value="true" /></map>\n' > /tmp/profile.xml
  adb push /tmp/profile.xml /data/local/tmp/profile.xml
  adb shell "mkdir -p /data/data/com.vanipy.$p/shared_prefs; cp /data/local/tmp/profile.xml /data/data/com.vanipy.$p/shared_prefs/${p}_prefs.xml; chown -R \$(stat -c %u /data/data/com.vanipy.$p):\$(stat -c %g /data/data/com.vanipy.$p) /data/data/com.vanipy.$p/shared_prefs"
done
python3 - <<'PY'
import json,pathlib
items=[]
for i in range(1,12):
 items.append(dict(id=i,area=(i-1)%4,title='Synthetic open item '+str(i),firstStep='Take one small step',status='open',weight=3,created=100000000,updated=100000000,log=[]))
pathlib.Path('/tmp/tumbuh-demo.json').write_text(json.dumps(dict(format='tumbuh-backup', version=1, appVersion='host-synthetic-fixture', items=items)))
PY
adb push /tmp/tumbuh-demo.json /data/local/tmp/tumbuh-demo.json
adb shell 'mkdir -p /data/data/com.vanipy.tumbuh/files; cp /data/local/tmp/tumbuh-demo.json /data/data/com.vanipy.tumbuh/files/tumbuh-demo.json; chown -R $(stat -c %u /data/data/com.vanipy.tumbuh):$(stat -c %g /data/data/com.vanipy.tumbuh) /data/data/com.vanipy.tumbuh/files'
python3 - <<'PYSEED'
import json,xml.etree.ElementTree as E
feed=dict(generated_at='2026-01-01T00:00:00Z', sections=[dict(id='demo',title='Synthetic list',items=[dict(id=str(i),title='Demo item '+str(i),note='Synthetic fixture only',status='open') for i in range(1,7)])])
root=E.Element('map');E.SubElement(root,'string',name='feed').text=json.dumps(feed)
E.ElementTree(root).write('/tmp/intodo-demo.xml', encoding='utf-8', xml_declaration=True)
PYSEED
adb shell am force-stop com.vanipy.intodo
adb push /tmp/intodo-demo.xml /data/local/tmp/intodo-demo.xml
adb shell 'mkdir -p /data/data/com.vanipy.intodo/shared_prefs; cp /data/local/tmp/intodo-demo.xml /data/data/com.vanipy.intodo/shared_prefs/instinct_widget.xml; chown -R $(stat -c %u /data/data/com.vanipy.intodo):$(stat -c %g /data/data/com.vanipy.intodo) /data/data/com.vanipy.intodo/shared_prefs'
adb shell appwidget grantbind --package com.vanipy.widgethost --user 0
for mode in counters strips expanded-first expanded-last counters-resized; do
  args=''
  case "$mode" in strips) args='--ez strips true';; expanded-first) args='--ez expanded true --ei start 0';; expanded-last) args='--ez expanded true --ei start 3';; esac
  adb shell am start -W -f 0x04000000 -n com.vanipy.widgethost/.HostActivity $args
  # Wait for provider updates and view inflation, bounded by 30 seconds.
  for i in $(seq 1 15); do
    adb shell uiautomator dump /sdcard/host.xml
    adb pull /sdcard/host.xml host-evidence/current.xml
    if grep -q 'DEMO' host-evidence/current.xml; then break; fi
    sleep 2
  done
  if grep -Eqi "Problem loading widget|Couldn.t add widget|BIND FAILED|not installed" host-evidence/current.xml; then exit 1; fi
  sleep 2
  adb exec-out screencap -p > "host-evidence/$mode.png"
  cp host-evidence/current.xml "host-evidence/$mode.xml"
done
python3 tools/widget-host/tap-check.py
adb logcat -d -s WidgetQualification:I AndroidRuntime:E > host-evidence/host.log
cat host-evidence/host.log
if grep -q 'FATAL EXCEPTION' host-evidence/host.log; then exit 1; fi
adb shell dumpsys appwidget > host-evidence/appwidget.txt
sha256sum host-apks/*.apk app/build/outputs/apk/debug/app-debug.apk > host-evidence/apk-hashes.txt
