#!/usr/bin/env bash
# Standalone host test for bracket_logcat_sorted. Usage: bash diagnostic/tools/bracket-sorted-test.sh diagnostic/tools/emulator-helpers.sh
set -u
T=$(mktemp -d); export PKG=com.vanipy.menit.diag LOG="$T/log.csv" COMPONENT=com.vanipy.menit.diag/com.vanipy.menit.diag.DiagService
. "$1"
mkdir -p "$T/bin"; export FAKELOGCAT="$T/logcat.txt" ARGSFILE="$T/args"
cat > "$T/bin/adb" <<'X'
#!/bin/sh
echo "$*" > "$ARGSFILE"
case "$*" in *logcat*) cat "$FAKELOGCAT" ;; *) exit 1 ;; esac
X
chmod +x "$T/bin/adb"; PATH="$T/bin:$PATH"
fails=0; ok() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (got $2, want $3)"; fails=$((fails+1)); fi; }
PROBE_LAST_LISTED_DEV=19:34:41; PROBE_FIRST_GONE_DEV=19:34:43
# Regression for run 43940253865: buffers are concatenated (not time ordered) and noisy lines at the start of the window
# must not use up the cap before the later, relevant lines.
{ for i in $(seq 1 400); do echo "11-16 19:34:40.$(printf %03d $i) 1044 1044 D DNC-0   : onRemoveNetworkRequest noise $i"; done   # noise, dropped
  for i in $(seq 1 300); do echo "11-16 19:34:40.$(printf %03d $((i%900))) 521 521 I OtherTag: ordinary line $i xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"; done
  echo "11-16 19:34:42.100 521 521 I AccessibilityManagerService: late relevant event"
  echo "11-16 19:34:41.500 519 546 I SettingsProvider: relevant earlier event"
  echo "11-16 19:34:30.000 1 1 I Tag: far before"; echo "11-16 19:34:50.000 1 1 I Tag: far after"; } > "$FAKELOGCAT"
out=$(bracket_logcat_sorted 2>&1)
echo "$out" | grep -q 'late relevant event'; ok "late relevant line kept despite earlier noise" $? 0
echo "$out" | grep -q 'relevant earlier event'; ok "earlier relevant line kept" $? 0
echo "$out" | grep -q 'DNC-0'; ok "noise tags dropped" $? 1
echo "$out" | grep -Eq 'far before|far after'; ok "outside window dropped" $? 1
e=$(echo "$out" | grep -n 'relevant earlier event' | head -1 | cut -d: -f1); l=$(echo "$out" | grep -n 'late relevant event' | head -1 | cut -d: -f1)
ok "priority section is time sorted (earlier before later)" $([ -n "$e" ] && [ -n "$l" ] && [ "$e" -lt "$l" ] && echo y || echo n) y
ok "line cap <=153" $([ "$(echo "$out" | grep -c .)" -le 153 ] && echo y || echo n) y
ok "byte cap <=20000 combined" $([ "$(printf %s "$out" | wc -c)" -le 20000 ] && echo y || echo n) y
grep -q -- '-b main,system,events,crash' "$ARGSFILE"; ok "reads only the four named buffers" $? 0
# stderr from a filter stage must not bypass the cap or leak
cat > "$T/bin/cut" <<'X'
#!/bin/sh
printf 'x%.0s' $(seq 1 111) >&2
exec /usr/bin/cut "$@"
X
chmod +x "$T/bin/cut"; out=$(bracket_logcat_sorted 2>&1); ok "filter-stage stderr cannot exceed the cap" $([ "$(printf %s "$out" | wc -c)" -le 20000 ] && echo y || echo n) y
rm "$T/bin/cut"
# pipefail + errexit must not make a truncated run fail
( set -eo pipefail; bracket_logcat_sorted >/dev/null 2>&1 ); ok "returns 0 under pipefail/errexit" $? 0
# invalid brackets
PROBE_LAST_LISTED_DEV=23:59:58; PROBE_FIRST_GONE_DEV=00:00:02; out=$(bracket_logcat_sorted 2>&1)
echo "$out" | grep -q 'bracket: invalid'; ok "rollover invalid" $? 0
echo "$out" | grep -q 'relevant'; ok "rollover prints no log lines" $? 1
PROBE_LAST_LISTED_DEV=""; out=$(bracket_logcat_sorted 2>&1); echo "$out" | grep -q 'no sample saw the component listed'; ok "no listed sample" $? 0
PROBE_LAST_LISTED_DEV=19:34:41; PROBE_FIRST_GONE_DEV=""; out=$(bracket_logcat_sorted 2>&1); echo "$out" | grep -q 'never seen gone'; ok "never gone" $? 0
PROBE_FIRST_GONE_DEV=19:34:43; : > "$FAKELOGCAT"; out=$(bracket_logcat_sorted 2>&1); echo "$out" | grep -q 'priority lines'; ok "empty logcat still prints header" $? 0
[ "$fails" -eq 0 ] && echo "ALL BRACKET TESTS PASS" || { echo "FAILS=$fails"; exit 1; }
