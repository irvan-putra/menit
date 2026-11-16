#!/usr/bin/env bash
# Draft, manual, synthetic-only emulator check. No real accounts, other apps, chats or model.
set -euo pipefail
PKG=com.vanipy.menit.diag
COMPONENT=com.vanipy.menit.diag/com.vanipy.menit.diag.DiagService
EVIDENCE_DIR="${RUNNER_TEMP:-/tmp}/menit-diag-emulator-evidence"
TMP_XML="${RUNNER_TEMP:-/tmp}/menit-diag-ui.xml"
LOG="${RUNNER_TEMP:-/tmp}/menit-diag-log.csv"
mkdir -p "$EVIDENCE_DIR"
# read_log, count_rows, wait_live_after_dump and decoy_window_check live in a sibling file so a host test can run the real code.
. "$(dirname "${BASH_SOURCE[0]}")/emulator-helpers.sh"

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

dump_ui() {
  adb shell uiautomator dump --compressed /sdcard/menit-diag-ui.xml >/dev/null
  adb exec-out cat /sdcard/menit-diag-ui.xml > "$TMP_XML"
  probe after_dump_ui
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
TAP_O_BEFORE=0; TAP_C_BEFORE=0
tap_text() {
  local wanted="$1" bounds x y i
  for i in $(seq 1 10); do
    dump_ui
    bounds=$(find_bounds "$wanted" || true)
    if [ -n "$bounds" ]; then
      read -r x y <<< "$bounds"
      # Liveness after the dump (H baseline is taken inside, after the dump returned). Never tap while the service may be unbound.
      wait_live_after_dump || { echo "Diagnostic service wrote no new heartbeat within ${LIVE_WAIT_S} s after the UI dump; refusing to tap while it may be unbound." >&2; return 1; }
      read_log
      TAP_O_BEFORE=$(count_rows O); TAP_C_BEFORE=$(count_rows C)   # fresh-row baselines, taken immediately before the tap
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
  # Typed rows only (the whole log is allowlisted to labels, ids and times). Shows no-row vs UNRECOGNISED vs wrong label.
  echo "--- diagnostics: O rows since the tap (baseline $old_count), H rows and C rows in the log" >&2
  read_log || true
  awk -F, -v n="$old_count" 'BEGIN{c=0} /^O,/{c++; if(c>n) print}' "$LOG" 2>/dev/null | grep -E '^O,-?[0-9]+,[0-9]+,(ALLOWLISTED,1|NOT_ALLOWLISTED,-1|UNRECOGNISED,-1)$' | head -20 >&2 || true
  echo "H rows: $(count_rows H)  C rows: $(count_rows C)  S rows: $(count_rows S)  O rows: $(count_rows O)" >&2
  grep -E '^(S|H|FS|F),-?[0-9]+,[0-9]+(,[0-9]+)?$' "$LOG" 2>/dev/null | tail -8 >&2 || true
  failure_diagnostics
  return 1
}

# A heartbeat is appended only by DiagService.onServiceConnected. Bound both the
# deadline and each read: secure-settings enablement alone is not proof of binding.
# Bounded, synthetic-only failure diagnostics. Disposable emulator with only this synthetic app; no private data exists here.
failure_diagnostics() {
 echo '--- diagnostics: enabled services / accessibility_enabled' >&2
 enabled_now >&2 || true
 timeout 5s adb shell settings get secure accessibility_enabled >&2 || true
 echo '--- diagnostics: probe now' >&2
 probe failure_time
 echo '--- diagnostics: dumpsys accessibility (first 150 lines)' >&2
 timeout 10s adb shell dumpsys accessibility 2>&1 | head -150 | cut -c1-240 >&2 || true
 echo '--- diagnostics: run-as listing and typed row counts' >&2
 timeout 5s adb shell run-as "$PKG" ls -l files 2>&1 | head -10 >&2 || true
 if timeout 3s adb shell run-as "$PKG" cat files/diag-log.csv > "${LOG}.fail" 2>/dev/null; then typed_counts "${LOG}.fail"; else echo "log: unreadable" >&2; fi
 echo '--- diagnostics: bounded logcat (all buffers, fixed patterns, last 60 lines)' >&2
 bounded_logcat
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

# v6 informational probes (they gate nothing): state right after the first H, then ~8 s with NO UI dump running.
probe first_h
probe_window pre_dump_no_ui 8
# Check the launch screen and capture it as visual UI evidence only.
dump_ui
contains_text 'Menit Diagnostic'
contains_text 'Synthetic test only.'
contains_text 'Reader: ON'
adb exec-out screencap -p > "$EVIDENCE_DIR/home.png"
probe after_screencap
# The home dump above suppressed the service. Before the Settings negative sample, require fresh post-dump liveness.
wait_live_after_dump || { echo "Diagnostic service wrote no new heartbeat within ${LIVE_WAIT_S} s after the last home dump; the Settings negative check would be meaningless." >&2; failure_diagnostics; exit 1; }

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
assert_settings_foreground 'immediately before the observation window'
# Bounded sample with no UI dump: O and C must stay unchanged, focus must stay on Settings, and the heartbeat must grow
# (a live service that saw no external events). A missing heartbeat is inconclusive, never a pass.
rc=0; settings_negative_sample || rc=$?
case "$rc" in
  0) ;;
  2) echo 'Diagnostic added an O or C row while Settings was foreground.' >&2; exit 1 ;;
  3) echo 'Android Settings lost foreground focus during the observation window; refusing to interpret it.' >&2; exit 1 ;;
  *) echo "Settings negative check inconclusive: no baseline read, no heartbeat growth, or the last read failed within ${NEG_WAIT_S} s." >&2; failure_diagnostics; exit 1 ;;
esac
adb shell am start -n "$PKG/.DiagActivity" >/dev/null
sleep 1.25

# Each synthetic A/B interaction must produce its exact fresh label after the tap.
# Explicit cases avoid self-checking the expected outcomes from an encoded spec list.
run_ab_case() {
  local button="$1" heading="$2" slug="$3" expected="$4" expected_id="$5"
  tap_text "$button"
  # Check the log first (service is still bound), then dump and capture: the dump suppresses the service.
  assert_new_label "$TAP_O_BEFORE" "$expected" "$expected_id" "$slug"
  assert_screen "$heading" "$slug"
  adb shell input keyevent 4
  sleep 1.25
}
run_ab_case 'Chat A (on the AI list)' 'Synthetic chat A' 'chat-a' 'ALLOWLISTED' '1'
run_ab_case 'Chat B (not on the list)' 'Synthetic chat B' 'chat-b' 'NOT_ALLOWLISTED' '-1'

# Negative-only decoy check: a fresh synthetic view not on the A/B list must not produce
# either positive A/B category. Do not require or claim a positive UNRECOGNISED label.
tap_text 'Unrecognised screen'
# Window check: any A/B label fails; the check also needs fresh own-package events (C) and a fresh valid non-A/B O row,
# so an empty or event-less log can no longer pass. No product label is claimed for the decoy.
rc=0; decoy_window_check "$TAP_O_BEFORE" "$TAP_C_BEFORE" || rc=$?
read_log || true
if [ "$rc" = 2 ]; then
  echo 'Decoy screen was incorrectly assigned a positive A/B label.' >&2
  exit 1
elif [ "$rc" != 0 ]; then
  echo "Decoy check inconclusive: fresh C rows=${DECOY_NEW_C:-0}, fresh non-A/B O rows=${DECOY_NEW_NONAB_O:-0} in the ${DECOY_WAIT_S} s window after the tap (events may not have reached the service)." >&2
  failure_diagnostics
  exit 1
fi
assert_screen 'Synthetic screen' 'unrecognized'

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
