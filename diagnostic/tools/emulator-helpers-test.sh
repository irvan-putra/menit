#!/usr/bin/env bash
# Host-only regression tests for the real helpers in emulator-helpers.sh (no adb, no emulator). Exit 0 only if all pass.
set -u
cd "$(dirname "$0")"
T=$(mktemp -d); export PKG=com.vanipy.menit.diag LOG="$T/log.csv"; FAKE="$T/fake.csv"
. ./emulator-helpers.sh
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
[ "$fails" -eq 0 ] && echo "ALL HELPER TESTS PASS" || { echo "FAILS=$fails"; exit 1; }
