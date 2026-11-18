#!/usr/bin/env bash
set -euo pipefail
mkdir -p host-evidence
trap 'adb logcat -d > host-evidence/logcat-all.txt; adb shell dumpsys appwidget > host-evidence/appwidget-final.txt' EXIT
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
feed=dict(generated_at='2026-01-01T00:00:00Z', sections=[dict(id='demo',title='Synthetic list',items=[dict(id=str(i),title='Read chapter '+str(i),note='Next small step',status='open') for i in range(1,7)])])
root=E.Element('map');E.SubElement(root,'string',name='feed').text=json.dumps(feed)
E.ElementTree(root).write('/tmp/intodo-demo.xml', encoding='utf-8', xml_declaration=True)
PYSEED
adb shell am force-stop com.vanipy.intodo
adb push /tmp/intodo-demo.xml /data/local/tmp/intodo-demo.xml
adb shell 'mkdir -p /data/data/com.vanipy.intodo/shared_prefs; cp /data/local/tmp/intodo-demo.xml /data/data/com.vanipy.intodo/shared_prefs/instinct_widget.xml; chown -R $(stat -c %u /data/data/com.vanipy.intodo):$(stat -c %g /data/data/com.vanipy.intodo) /data/data/com.vanipy.intodo/shared_prefs'
# Android15 keeps a force-stopped app widget suspended until explicit launch.
# This starts synthetic fixture apps in this disposable emulator only.
for p in menit tumbuh denyut kemas; do
 adb shell am start -W -n "com.vanipy.$p/.MainActivity"
done
adb shell appwidget grantbind --package com.vanipy.widgethost --user 0
if [ "${INTODO_ONLY:-false}" = true ]; then
  for span in 1x1 5x1 5x6; do
    x=${span%x*}; y=${span#*x}
    adb shell am start -W -f 0x04000000 -n com.vanipy.widgethost/.HostActivity --ei single 0 --ei spanX "$x" --ei spanY "$y"
    adb shell uiautomator dump /sdcard/intodo.xml
    adb pull /sdcard/intodo.xml "host-evidence/intodo-$span.xml"
    adb exec-out screencap -p > "host-evidence/intodo-$span.png"
  done
  python3 tools/widget-host/tap-check.py
  adb shell dumpsys package com.vanipy.intodo > host-evidence/intodo-package.txt
  sha256sum host-apks/intodo.apk > host-evidence/intodo-apk-hash.txt
  exit 0
fi
pkgs=(com.vanipy.intodo com.vanipy.saku com.vanipy.menit com.vanipy.tumbuh com.vanipy.denyut com.vanipy.kemas)
# Resize the SAME live HostView and widget ID repeatedly, including counter transitions.
adb shell am start -W -f 0x04000000 -n com.vanipy.widgethost/.HostActivity --ei single 0 --ei spanX 2 --ei spanY 1
for span in 3x1 2x2 3x2 5x6 2x1 3x2; do
 x=${span%x*}; y=${span#*x}
 adb shell am broadcast -a com.vanipy.widgethost.RESIZE -p com.vanipy.widgethost --ei spanX "$x" --ei spanY "$y"
 SINGLE_PKG=com.vanipy.intodo python3 tools/widget-host/wait-mode.py single-content
 cp host-evidence/current.xml "host-evidence/intodo-live-resize-$span.xml"
 adb exec-out screencap -p > "host-evidence/intodo-live-resize-$span.png"
 if [ "$span" = 5x6 ]; then
  python3 -c 'import pathlib; s=pathlib.Path("host-evidence/current.xml").read_text(); assert all("Read chapter "+str(i) in s for i in range(1,7)), "Six items not preserved"'
 fi
done
# The user's Samsung showed counter-only blanks at 2-3 columns; require populated content there.
for i in 0 1 2 3 4 5; do
  for span in 2x1 3x1 2x2 3x2; do
    x=${span%x*}; y=${span#*x}
    adb shell am start -W -f 0x04000000 -n com.vanipy.widgethost/.HostActivity --ei single "$i" --ei spanX "$x" --ei spanY "$y"
    SINGLE_PKG="${pkgs[$i]}" python3 tools/widget-host/wait-mode.py single-content
    adb exec-out screencap -p > "host-evidence/${pkgs[$i]##*.}-$span.png"
    cp host-evidence/current.xml "host-evidence/${pkgs[$i]##*.}-$span.xml"
  done
done
for mode in counters strips intermediate expanded-first expanded-middle expanded-last counters-resized; do
  args=''
  case "$mode" in intermediate) args='--ez intermediate true';; strips) args='--ez strips true';; expanded-first) args='--ez expanded true --ei start 0';; expanded-middle) args='--ez expanded true --ei start 2';; expanded-last) args='--ez expanded true --ei start 4';; esac
  adb shell am start -W -f 0x04000000 -n com.vanipy.widgethost/.HostActivity $args
  # Require the expected layouts, not merely any DEMO string from a stale frame.
  python3 tools/widget-host/wait-mode.py "$mode"
  adb exec-out screencap -p > "host-evidence/$mode.png"
  cp host-evidence/current.xml "host-evidence/$mode.xml"
done
# Representative aspect-ratio boundaries on actual providers; the resource suite covers all30 spans.
for index in 0 1 2 3 4 5; do
  for span in 1x6 5x6; do
    x=${span%x*}; y=${span#*x}
    adb shell am start -W -f 0x04000000 -n com.vanipy.widgethost/.HostActivity --ei single "$index" --ei spanX "$x" --ei spanY "$y"
    adb shell uiautomator dump /sdcard/boundary.xml
    adb pull /sdcard/boundary.xml "host-evidence/boundary-$index-$span.xml"
    adb exec-out screencap -p > "host-evidence/boundary-$index-$span.png"
  done
done
python3 tools/widget-host/tap-check.py
adb logcat -d -s WidgetQualification:I AndroidRuntime:E > host-evidence/host.log
cat host-evidence/host.log
if grep -q 'FATAL EXCEPTION' host-evidence/host.log; then exit 1; fi
adb shell dumpsys appwidget > host-evidence/appwidget.txt
sha256sum host-apks/*.apk app/build/outputs/apk/debug/app-debug.apk > host-evidence/apk-hashes.txt
