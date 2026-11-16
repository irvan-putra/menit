#!/usr/bin/env bash
# Host-only regression tests for the real helpers in emulator-helpers.sh (no adb, no emulator). Exit 0 only if all pass.
set -u
cd "$(dirname "$0")"
T=$(mktemp -d); export PKG=com.vanipy.menit.diag LOG="$T/log.csv"; FAKE="$T/fake.csv"
COMPONENT=com.vanipy.menit.diag/com.vanipy.menit.diag.DiagService
. ./emulator-helpers.sh
PROBE_LOG="$T/probe.csv"
# Fake adb for probe tests: serves ENABLED, A11Y and the fake log; no device needed.
ENABLED=""; A11Y=0
mkdir -p "$T/bin"; export FAKE
REAL_CUT=$(command -v cut); export REAL_CUT
CUT_CALLS="$T/cut-calls"; export CUT_CALLS
cat > "$T/bin/cut" <<'X'
#!/bin/sh
printf 'args=%s noise=%s\n' "$*" "${CUT_NOISE:-unset}" >> "$CUT_CALLS"
if [ "${CUT_NOISE:-0}" = 1 ]; then printf '%111s' 'CUT_DIAGNOSTIC' >&2; fi
exec "$REAL_CUT" "$@"
X
cat > "$T/bin/adb" <<'X'
#!/bin/sh
case "$*" in
  *"settings get secure enabled_accessibility_services"*) printf '%s\r\n' "$ENABLED" ;;
  *"settings get secure accessibility_enabled"*) [ -n "$ADV" ] && printf '%s' "$ADV" > "$DEVFILE"; printf '%s\r\n' "$A11Y" ;;
  *"cat files/diag-log.csv"*) cat "$FAKE" ;;
  *"date +%H:%M:%S"*) if [ -s "$DEVFILE" ]; then printf '%s\r\n' "$(cat "$DEVFILE")"; else printf '%s\r\n' "$DEVTIME"; fi ;;
  *"cat /proc/uptime"*) printf '123.45 99.00\r\n' ;;
  *logcat*) cat "$FAKELOGCAT" ;;
  *) exit 1 ;;
esac
X
chmod +x "$T/bin/adb" "$T/bin/cut"; PATH="$T/bin:$PATH"; DEVFILE="$T/devclock"; ADV=""; export ENABLED A11Y DEVTIME FAKELOGCAT DEVFILE ADV; DEVTIME=16:41:00; FAKELOGCAT="$T/logcat.txt"
PROBE_IN_WAIT=0
# Replace the device read with a copy of the fake file; READ_DELAY simulates a slow device read.
read_log() { [ -n "${READ_DELAY:-}" ] && sleep "$READ_DELAY"; cp "$FAKE" "$LOG"; }
fails=0; ok() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (got $2, want $3)"; fails=$((fails+1)); fi; }
LIVE_WAIT_S=2; DECOY_WAIT_S=2

# 1. H written only during the dump (before the baseline) cannot release the wait
printf 'H,5,10\nH,5,20\n' > "$FAKE"; echo "H,5,30" >> "$FAKE"    # last row stands in for an H written while the dump ran
wait_live_after_dump; ok "H only during dump does not release" $? 1
# 2. A later H releases it
printf 'H,5,10\n' > "$FAKE"; ( sleep 1; echo "H,5,99" >> "$FAKE" ) &
wait_live_after_dump; ok "later H releases" $? 0; wait
# 3. Deadline is wall-clock even when each read is slow (a loop count would take much longer)
printf 'H,5,10\n' > "$FAKE"; READ_DELAY=1; s=$SECONDS; wait_live_after_dump; rc=$?; e=$((SECONDS-s)); unset READ_DELAY
ok "slow reads: returns 1" $rc 1; ok "slow reads: bounded by wall clock (<=5 s)" $([ $e -le 5 ] && echo y || echo n) y

# 3b. failed baseline reads, then old H rows only: must not release (old H cannot look new against a zero baseline)
printf 'H,5,10\nH,5,20\n' > "$FAKE"; FAILS_LEFT=3
read_log() { if [ "$FAILS_LEFT" -gt 0 ]; then FAILS_LEFT=$((FAILS_LEFT-1)); : > "$LOG"; return 1; fi; cp "$FAKE" "$LOG"; }
wait_live_after_dump; ok "failed baseline then only old H does not release" $? 1
# 3c. baseline never readable: fail closed
read_log() { : > "$LOG"; return 1; }
wait_live_after_dump; ok "baseline unavailable fails closed" $? 1
# 3d. failed baseline reads, then a genuinely later H: releases
printf 'H,5,10\n' > "$FAKE"; FAILS_LEFT=2; ( sleep 1.3; echo "H,5,77" >> "$FAKE" ) &
read_log() { if [ "$FAILS_LEFT" -gt 0 ]; then FAILS_LEFT=$((FAILS_LEFT-1)); : > "$LOG"; return 1; fi; cp "$FAKE" "$LOG"; }
wait_live_after_dump; ok "failed baseline then later H releases" $? 0; wait
read_log() { [ -n "${READ_DELAY:-}" ] && sleep "$READ_DELAY"; cp "$FAKE" "$LOG"; }

base() { printf 'O,5,100,UNRECOGNISED,-1\nC,5,1,2,3,4\n' > "$FAKE"; }   # pre-tap rows: baseline O=1, C=1
# 4. decoy: nothing new at all (empty post-tap window) is inconclusive, not a pass
base; decoy_window_check 1 1; ok "decoy: no event is inconclusive" $? 1
# 5. decoy: C only
base; echo "C,5,5,6,7,8" >> "$FAKE"; decoy_window_check 1 1; ok "decoy: C only is inconclusive" $? 1
# 6. decoy: O only
base; echo "O,5,200,UNRECOGNISED,-1" >> "$FAKE"; decoy_window_check 1 1; ok "decoy: O only is inconclusive" $? 1
# 7. decoy: fresh C + fresh valid non-A/B O passes
base; printf 'C,5,5,6,7,8\nO,5,200,UNRECOGNISED,-1\n' >> "$FAKE"; decoy_window_check 1 1; ok "decoy: fresh C and fresh non-A/B O passes" $? 0
# 8. old rows (before the baseline) do not count
base; decoy_window_check 1 1; ok "decoy: pre-tap rows do not count" $? 1
# 9. any A/B fails
for row in 'O,5,200,ALLOWLISTED,1' 'O,5,200,NOT_ALLOWLISTED,-1'; do base; printf 'C,5,5,6,7,8\nO,5,150,UNRECOGNISED,-1\n%s\n' "$row" >> "$FAKE"; decoy_window_check 1 1; ok "decoy: $row fails" $? 2; done
# 10. an A/B row arriving late inside the window still fails
base; printf 'C,5,5,6,7,8\nO,5,150,UNRECOGNISED,-1\n' >> "$FAKE"; ( sleep 1; echo "O,5,300,ALLOWLISTED,1" >> "$FAKE" ) &
decoy_window_check 1 1; ok "decoy: late A/B inside the window fails" $? 2; wait
# Settings-foreground sample fixtures (no dumps; NEG_WAIT_S shortened for the host test)
NEG_WAIT_S=3; focus_ok() { return 0; }
nbase() { printf 'H,5,10\nO,5,100,UNRECOGNISED,-1\nC,5,1,2,3,4\n' > "$FAKE"; }
nbase; ( sleep 1; echo "H,5,60" >> "$FAKE" ) &
settings_negative_sample; ok "settings: live H and no O/C passes" $? 0; wait
nbase; settings_negative_sample; ok "settings: stale H is inconclusive" $? 1
nbase; ( sleep 0.6; echo "H,5,60" >> "$FAKE"; echo "O,5,200,UNRECOGNISED,-1" >> "$FAKE" ) &
settings_negative_sample; ok "settings: added O fails" $? 2; wait
nbase; ( sleep 0.6; echo "H,5,60" >> "$FAKE"; echo "C,5,5,6,7,8" >> "$FAKE" ) &
settings_negative_sample; ok "settings: added C fails" $? 2; wait
nbase; ( sleep 1; echo "H,5,60" >> "$FAKE" ) & focus_ok() { return 1; }
settings_negative_sample; ok "settings: lost focus fails" $? 3; wait; focus_ok() { return 0; }
read_log() { : > "$LOG"; return 1; }; settings_negative_sample; ok "settings: no baseline read is inconclusive" $? 1
read_log() { [ -n "${READ_DELAY:-}" ] && sleep "$READ_DELAY"; cp "$FAKE" "$LOG"; }

# ---- v6 probe tests ----
read_log() { cp "$FAKE" "$LOG"; }
printf 'H,5,10\nH,5,20\nO,5,100,UNRECOGNISED,-1\n' > "$FAKE"; ENABLED="$COMPONENT"; A11Y=1
out=$(probe t1 2>&1 >/dev/null); echo "$out" | grep -Eq 'label=t1 sec=[0-9]+ utc=[0-9:]+ dev=16:41:00 up=123.45 enabled=listed a11y=1 H=2 rows=3$'; ok "probe: shapes and counts" $? 0
ENABLED=null; A11Y=0; out=$(probe t2 2>&1 >/dev/null); echo "$out" | grep -q 'enabled=empty a11y=0'; ok "probe: null setting shows empty" $? 0
ENABLED="com.other.secret/Thing"; out=$(probe t3 2>&1 >/dev/null); echo "$out" | grep -q 'enabled=other'; ok "probe: other value shape" $? 0
echo "$out" | grep -q 'com.other'; ok "probe: raw settings value never printed" $? 1
printf 'H,5,10\nprivate free text here\n' > "$FAKE"; out=$(probe t4 2>&1 >/dev/null)
echo "$out" | grep -q 'H=invalid rows=invalid'; ok "probe: non-allowlisted log gives invalid" $? 0
echo "$out" | grep -q 'private'; ok "probe: invalid log content never printed" $? 1
out=$(typed_counts "$FAKE" 2>&1); echo "$out" | grep -q 'not printed'; ok "typed_counts: invalid log hidden" $? 0
echo "$out" | grep -q 'private'; ok "typed_counts: no content leak" $? 1
printf 'S,1,2,3\nH,1,2\nC,1,2,3,4,5\nO,1,2,UNRECOGNISED,-1\n' > "$FAKE"; out=$(typed_counts "$FAKE" 2>&1)
[ "$out" = "log typed rows: S=1 H=1 C=1 O=1 FS=0 F=0" ]; ok "typed_counts: valid counts" $? 0
: > "$FAKE"; out=$(typed_counts "$FAKE" 2>&1); echo "$out" | grep -q 'empty or unreadable'; ok "typed_counts: empty" $? 0
# probes inside the wait loop must not change its outcome or baseline logic
PROBE_IN_WAIT=1
printf 'H,5,10\n' > "$FAKE"; wait_live_after_dump 2>/dev/null; ok "wait with probes: stale H still fails" $? 1
printf 'H,5,10\n' > "$FAKE"; ( sleep 1; echo "H,5,99" >> "$FAKE" ) &
wait_live_after_dump 2>/dev/null; ok "wait with probes: later H still releases" $? 0; wait
PROBE_IN_WAIT=0
# probe_window is bounded
printf 'H,5,10\n' > "$FAKE"; s=$SECONDS; probe_window x 3 2>/dev/null; e=$((SECONDS-s)); ok "probe_window bounded" $([ $e -le 6 ] && echo y || echo n) y
# ---- v7 bracket tests ----
# Regression: a slow a11y/CSV read after the enabled read must not move the bracket start past the real loss.
# Probe 1: enabled read at 16:41:00 (listed), then the a11y read "takes" the clock to 16:41:10. The setting actually
# vanished at 16:41:03 (event below). Probe 2 reads gone at 16:41:10..16:41:10.
printf 'H,5,10\n' > "$FAKE"; PROBE_LAST_LISTED_DEV=""; PROBE_FIRST_GONE_DEV=""
printf '16:41:00' > "$DEVFILE"; ADV=16:41:10; ENABLED="$COMPONENT"; probe s1 2>/dev/null
ENABLED=null; ADV=""; probe s2 2>/dev/null
ok "slow read: listed lower bound is the pre-read clock" "$PROBE_LAST_LISTED_DEV" 16:41:00
ok "slow read: gone upper bound is the post-read clock" "$PROBE_FIRST_GONE_DEV" 16:41:10
{ echo "11-16 16:40:50.000 1 1 I Tag: early"; echo "11-16 16:41:03.000 519 546 I SettingsProvider: real-loss-event"; echo "11-16 16:41:30.000 1 1 I Tag: late"; } > "$FAKELOGCAT"
out=$(bracket_logcat 2>&1); echo "$out" | grep -q real-loss-event; ok "slow read: event during the slow reads is retained" $? 0
echo "$out" | grep -Eq 'Tag: (early|late)'; ok "slow read: outside events still dropped" $? 1
# rollover / backwards clock is not a valid bracket
PROBE_LAST_LISTED_DEV=23:59:58; PROBE_FIRST_GONE_DEV=00:00:02
out=$(bracket_logcat 2>&1); echo "$out" | grep -q 'bracket: invalid'; ok "rollover is reported invalid" $? 0
echo "$out" | grep -q 'SettingsProvider'; ok "rollover prints no log lines" $? 1
# unknown clock never becomes a bracket
printf 'unknown' > "$DEVFILE"; PROBE_LAST_LISTED_DEV=""; PROBE_FIRST_GONE_DEV=""; ENABLED="$COMPONENT"; ADV=""; probe u1 2>/dev/null
ok "unknown clock: no listed bracket recorded as valid" "$(bracket_logcat 2>&1 | grep -c 'bracket (device')" 0
rm -f "$DEVFILE"

printf 'H,5,10\n' > "$FAKE"; PROBE_LAST_LISTED_DEV=""; PROBE_FIRST_GONE_DEV=""
ENABLED="$COMPONENT"; DEVTIME=16:41:00; probe b1 2>/dev/null
ENABLED="$COMPONENT"; DEVTIME=16:41:01; probe b2 2>/dev/null
ENABLED=null; DEVTIME=16:41:03; probe b3 2>/dev/null
ENABLED=null; DEVTIME=16:41:04; probe b4 2>/dev/null
ok "bracket: last listed recorded" "$PROBE_LAST_LISTED_DEV" 16:41:01
ok "bracket: first gone recorded (not overwritten)" "$PROBE_FIRST_GONE_DEV" 16:41:03
ENABLED="$COMPONENT"; DEVTIME=16:41:05; probe b5 2>/dev/null
ok "bracket: re-listed resets first-gone" "$PROBE_FIRST_GONE_DEV" ""
ENABLED=unreadable; ENABLED=""; DEVTIME=16:41:06; probe b6 2>/dev/null; ok "bracket: empty after listed sets gone" "$PROBE_FIRST_GONE_DEV" 16:41:06
PROBE_LAST_LISTED_DEV=16:41:01; PROBE_FIRST_GONE_DEV=16:41:03
{ for i in 1 2 3 4 5; do echo "11-16 16:40:5$i.100 1 1 I Tag: before $i"; done
  echo "11-16 16:41:00.500 1 1 I SomeTag: padded-in"
  echo "11-16 16:41:02.000 519 546 I SettingsProvider: in-window event"
  echo "11-16 16:41:02.100 519 546 I AppsFilter: noisy dropped"
  echo "11-16 16:41:04.200 1 1 I Tag: padded-in-end"
  echo "11-16 16:41:05.000 1 1 I Tag: after window"; } > "$FAKELOGCAT"
out=$(bracket_logcat 2>&1)
echo "$out" | grep -q 'in-window event'; ok "bracket_logcat: in-window line kept" $? 0
echo "$out" | grep -q 'padded-in-end'; ok "bracket_logcat: 1 s pad kept" $? 0
echo "$out" | grep -Eq 'before|after window'; ok "bracket_logcat: outside lines dropped" $? 1
echo "$out" | grep -q 'AppsFilter'; ok "bracket_logcat: noise dropped" $? 1
for i in $(seq 1 400); do echo "11-16 16:41:02.$((i%1000)) 1 1 I Tag: $(printf 'x%.0s' $(seq 1 300))"; done > "$FAKELOGCAT"
CUT_NOISE=1; export CUT_NOISE; out=$(set -o pipefail; hash -r; command -v cut > "$T/cut-path"; bracket_logcat 2>&1); rc=$?; CUT_NOISE=0; export CUT_NOISE
bytes=$(printf %s "$out" | wc -c | tr -d ' ')
lines=$(printf '%s\n' "$out" | grep -c .)
ok "bracket_logcat: fake cut emitted 111 stderr bytes" "$(grep -c 'args=-c1-240 noise=1' "$CUT_CALLS")" 1
ok "bracket_logcat: fake cut path is used" "$(cat "$T/cut-path")" "$T/bin/cut"
ok "bracket_logcat: pipefail tolerates bounded SIGPIPE" "$rc" 0
ok "bracket_logcat: simulated cut diagnostic suppressed" "$(echo "$out" | grep -c CUT_DIAGNOSTIC)" 0
ok "bracket_logcat: <=150 log lines plus header (measured $lines)" $([ "$lines" -le 151 ] && echo y || echo n) y
ok "bracket_logcat: <=20000 bytes including header (measured $bytes)" $([ "$bytes" -le 20000 ] && echo y || echo n) y
echo "$out" | grep -q '^bracket (device clock'; ok "bracket_logcat: header present" $? 0
PROBE_LAST_LISTED_DEV=""; out=$(bracket_logcat 2>&1); echo "$out" | grep -q 'no sample saw the component listed'; ok "bracket_logcat: no listed sample message" $? 0
PROBE_LAST_LISTED_DEV=16:41:01; PROBE_FIRST_GONE_DEV=""; out=$(bracket_logcat 2>&1); echo "$out" | grep -q 'never seen gone'; ok "bracket_logcat: never gone message" $? 0
[ "$fails" -eq 0 ] && echo "ALL HELPER TESTS PASS" || { echo "FAILS=$fails"; exit 1; }
