#!/usr/bin/env bash
# Draft, manual, synthetic-only emulator check. No real accounts, other apps, chats or model.
set -euo pipefail
PKG=com.vanipy.menit.diag
COMPONENT=com.vanipy.menit.diag/com.vanipy.menit.diag.DiagService
EVIDENCE_DIR="${RUNNER_TEMP:-/tmp}/menit-diag-emulator-evidence"
TMP_XML="${RUNNER_TEMP:-/tmp}/menit-diag-ui.xml"
LOG="${RUNNER_TEMP:-/tmp}/menit-diag-log.csv"
mkdir -p "$EVIDENCE_DIR"

adb wait-for-device
adb shell pm clear "$PKG" >/dev/null 2>&1 || true
adb install -r diagnostic/app/build/outputs/apk/debug/app-debug.apk
# Stop the package BEFORE enabling its service: Android's accessibility manager drops a force-stopped package's
# services from the enabled list (AOSP AccessibilityManagerService.onHandleForceStop). Never force-stop after enabling.
adb shell am force-stop "$PKG"
# Enable the diagnostic reader only on the disposable emulator. Settings alone is not evidence of a bound service.
adb shell settings put secure enabled_accessibility_services "$COMPONENT"
adb shell settings put secure accessibility_enabled 1
enabled_now() { timeout 5s adb shell settings get secure enabled_accessibility_services 2>&1 | tr -d '\r' || true; }
case "$(enabled_now)" in *"$COMPONENT"*) ;; *) echo "Enabled-services setting does not contain $COMPONENT right after writing it." >&2; ;; esac
adb shell am start -n "$PKG/.DiagActivity" >/dev/null

read_log() { adb shell run-as "$PKG" cat files/diag-log.csv > "$LOG"; }
count_rows() { grep -c "^$1," "$LOG" || true; }
dump_ui() {
  adb shell uiautomator dump --compressed /sdcard/menit-diag-ui.xml >/dev/null
  adb exec-out cat /sdcard/menit-diag-ui.xml > "$TMP_XML"
}
contains_text() {
  python3 - "$TMP_XML" "$1" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
needle = sys.argv[2]
raise SystemExit(0 if any(needle in n.attrib.get('text', '') for n in root.iter()) else 1)
PY
}
find_bounds() {
  python3 - "$TMP_XML" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter():
    if n.attrib.get('text') == sys.argv[2] or n.attrib.get('content-desc') == sys.argv[2]:
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds', ''))
        if m:
            x1,y1,x2,y2 = map(int, m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            break
PY
}
tap_text() {
  local wanted="$1" bounds x y i
  for i in $(seq 1 10); do
    dump_ui
    bounds=$(find_bounds "$wanted" || true)
    if [ -n "$bounds" ]; then
      read -r x y <<< "$bounds"
      adb shell input tap "$x" "$y"
      return 0
    fi
    # Scroll the home list to reveal the fixed synthetic test buttons.
    adb shell input swipe 540 1600 540 550 400
    sleep 0.25
  done
  echo "UI text not found: $wanted" >&2
  return 1
}
scroll_to_top() {
  # The app reuses one ScrollView across screens. Restore the heading before dumping or capturing pixels.
  for _ in 1 2 3; do adb shell input swipe 540 450 540 1600 350; done
  sleep 0.5
}
assert_screen() {
  local wanted="$1" file="$2"
  scroll_to_top
  dump_ui
  contains_text "$wanted" || { echo "Expected UI text missing after scrolling to top: $wanted" >&2; return 1; }
  # This proves visible synthetic UI only; service behavior is checked separately from persisted log rows.
  adb exec-out screencap -p > "$EVIDENCE_DIR/$file.png"
}
assert_new_label() {
  local old_count="$1" expected="$2" expected_id="$3" slug="$4" i current new_rows
  # Allow the 1 s throttle and poll beyond UIAutomation's dump connection; suppression/no event fails closed.
  for i in $(seq 1 16); do
    sleep 0.25
    read_log
    current=$(count_rows O)
    if [ "$current" -gt "$old_count" ]; then
      new_rows=$(awk -F, -v n="$old_count" 'BEGIN{c=0} /^O,/{c++; if(c>n) print}' "$LOG")
      if printf '%s\n' "$new_rows" | grep -Eq ",${expected},${expected_id}$"; then return 0; fi
    fi
  done
  echo "No new O row with expected label/id $expected/$expected_id after $slug; service may be unbound or UIAutomation suppressed events." >&2
  return 1
}

# A heartbeat is appended only by DiagService.onServiceConnected. Bound both the
# deadline and each read: secure-settings enablement alone is not proof of binding.
# Bounded, synthetic-only failure diagnostics. Disposable emulator with only this synthetic app; no private data exists here.
failure_diagnostics() {
 echo '--- diagnostics: enabled services / accessibility_enabled' >&2
 enabled_now >&2 || true
 timeout 5s adb shell settings get secure accessibility_enabled >&2 || true
 echo '--- diagnostics: dumpsys accessibility (first 60 lines)' >&2
 timeout 10s adb shell dumpsys accessibility 2>&1 | head -60 >&2 || true
 echo '--- diagnostics: run-as listing and read error' >&2
 timeout 5s adb shell run-as "$PKG" ls -l files 2>&1 | head -10 >&2 || true
 timeout 5s adb shell run-as "$PKG" cat files/diag-log.csv 2>&1 >/dev/null | head -3 >&2 || true
 echo '--- diagnostics: bounded logcat (accessibility manager, crashes, this package)' >&2
 timeout 10s adb logcat -d -t 400 -s AccessibilityManagerService:V AndroidRuntime:E DEBUG:E 2>&1 | tail -40 >&2 || true
 timeout 10s adb logcat -d -t 2000 2>&1 | grep -F "$PKG" | tail -30 >&2 || true
 echo '--- diagnostics end' >&2
}
service_bound=false
heartbeat_deadline=$((SECONDS + 30))
while (( SECONDS < heartbeat_deadline )); do
  if timeout 1s adb shell run-as "$PKG" cat files/diag-log.csv > "$LOG" 2>/dev/null; then
    if grep -Eq '^H,-?[0-9]+,[0-9]+$' "$LOG"; then service_bound=true; break; fi
  fi
  sleep 0.5
done
if [ "$service_bound" != true ]; then
 case "$(enabled_now)" in *"$COMPONENT"*) echo 'Component is still listed as enabled.' >&2 ;; *) echo 'Component is no longer listed as enabled: something removed it after it was written.' >&2 ;; esac
 failure_diagnostics
  echo 'No diagnostic-service heartbeat within the bounded 30-second poll; service was not proven bound.' >&2
  exit 1
fi

# Check the launch screen and capture it as visual UI evidence only.
dump_ui
contains_text 'Menit Diagnostic'
contains_text 'Synthetic test only.'
contains_text 'Reader: ON'
adb exec-out screencap -p > "$EVIDENCE_DIR/home.png"

# Check that events from another app do not produce accessibility O/C rows in this diagnostic's store.
settings_is_focused() {
  timeout 2s adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | grep -Fq 'com.android.settings'
}
assert_settings_foreground() {
  local where="$1"
  if ! settings_is_focused; then
    echo "Android Settings lost foreground focus $where; refusing to interpret a negative-capture baseline." >&2
    exit 1
  fi
}
adb shell am start -a android.settings.SETTINGS >/dev/null
settings_focused=false
settings_deadline=$((SECONDS + 15))
while (( SECONDS < settings_deadline )); do
  if settings_is_focused; then settings_focused=true; break; fi
  sleep 0.5
done
if [ "$settings_focused" != true ]; then
  echo 'Android Settings did not become the focused external app within 15 seconds.' >&2
  exit 1
fi
# Require focus to remain on Settings across a settling interval, then re-check immediately
# before each log snapshot so an app-resume race cannot turn the negative test into a false pass.
sleep 2
assert_settings_foreground 'after the settling interval'
read_log
o_before=$(count_rows O)
c_before=$(count_rows C)
assert_settings_foreground 'immediately before the observation window'
sleep 3
assert_settings_foreground 'at the end of the observation window'
read_log
o_after=$(count_rows O)
c_after=$(count_rows C)
[ "$o_before" = "$o_after" ] || { echo 'Diagnostic added an O row while Settings was foreground.' >&2; exit 1; }
[ "$c_before" = "$c_after" ] || { echo 'Diagnostic added a C row while Settings was foreground.' >&2; exit 1; }
adb shell am start -n "$PKG/.DiagActivity" >/dev/null
sleep 1.25

# Each synthetic A/B interaction must produce its exact fresh label after the tap.
# Explicit cases avoid self-checking the expected outcomes from an encoded spec list.
run_ab_case() {
  local button="$1" heading="$2" slug="$3" expected="$4" expected_id="$5"
  read_log
  local before
  before=$(count_rows O)
  tap_text "$button"
  assert_screen "$heading" "$slug"
  assert_new_label "$before" "$expected" "$expected_id" "$slug"
  adb shell input keyevent 4
  sleep 1.25
}
run_ab_case 'Chat A (on the AI list)' 'Synthetic chat A' 'chat-a' 'ALLOWLISTED' '1'
run_ab_case 'Chat B (not on the list)' 'Synthetic chat B' 'chat-b' 'NOT_ALLOWLISTED' '-1'

# Negative-only decoy check: a fresh synthetic view not on the A/B list must not produce
# either positive A/B category. Do not require or claim a positive UNRECOGNISED label.
read_log
before=$(count_rows O)
tap_text 'Unrecognised screen'
assert_screen 'Synthetic screen' 'unrecognized'
sleep 1.25
read_log
new_rows=$(awk -F, -v n="$before" 'BEGIN{c=0} /^O,/{c++; if(c>n) print}' "$LOG")
if printf '%s\n' "$new_rows" | grep -Eq ',(ALLOWLISTED,1|NOT_ALLOWLISTED,-1)$'; then
  echo 'Decoy screen was incorrectly assigned a positive A/B label.' >&2
  exit 1
fi

# Whole-file allowlist: no free text, header, message body or unrecognized record shape may be persisted.
read_log
if grep -Evq '^(S,-?[0-9]+,[0-9]+,[0-9]+|H,-?[0-9]+,[0-9]+|C,-?[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+|FS,-?[0-9]+,[0-9]+|F,-?[0-9]+,[0-9]+,[0-9]+|O,-?[0-9]+,[0-9]+,(ALLOWLISTED,1|NOT_ALLOWLISTED,-1|UNRECOGNISED,-1))$' "$LOG"; then
  echo 'Unexpected row shape or non-label text found in diagnostic log.' >&2
  exit 1
fi
for expected in 'ALLOWLISTED,1' 'NOT_ALLOWLISTED,-1'; do
  grep -Eq "^O,-?[0-9]+,[0-9]+,${expected}$" "$LOG" || { echo "Missing final typed label row: $expected" >&2; exit 1; }
done
printf 'Synthetic check passed: fresh post-tap A/B labels observed; decoy was never assigned either A/B label; Settings added no O/C rows; bound-service heartbeat and whole-file label/time schema verified.\n'

# Retain only synthetic screenshots for visual review; remove the private label log and disposable app data.
adb shell pm clear "$PKG" >/dev/null
rm -f "$LOG" "$TMP_XML"
