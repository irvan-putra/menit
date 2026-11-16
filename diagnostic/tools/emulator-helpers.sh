# Sourced by emulator-smoke.sh and by emulator-helpers-test.sh. Needs PKG and LOG. Synthetic-only, typed rows only.
# Row shapes (whole-file allowlist in the main script): S,H,C,FS,F and O,<boot>,<ms>,<LABEL>,<id>.
LIVE_WAIT_S=${LIVE_WAIT_S:-10}      # post-dump liveness deadline, wall-clock (SECONDS), not a loop count
NEG_WAIT_S=${NEG_WAIT_S:-10}        # Settings-foreground sample, wall-clock; covers at least one 5 s heartbeat
DECOY_WAIT_S=${DECOY_WAIT_S:-4}     # post-tap window for the decoy check, wall-clock

read_log() { timeout 3s adb shell run-as "$PKG" cat files/diag-log.csv > "$LOG"; }
count_rows() { grep -c "^$1," "$LOG" || true; }
# O rows after the first $1 O rows
new_o_rows() { awk -F, -v n="$1" 'BEGIN{c=0} /^O,/{c++; if(c>n) print}' "$LOG"; }

# Call right after dump_ui returns. `uiautomator dump` connects UiAutomation with default flags, which suppresses
# accessibility services while it is connected (AOSP android-14.0.0_r1 UiAutomationManager.suppressingAccessibilityServicesLocked).
# The baseline H count is the first SUCCESSFUL log read after the dump returned (a failed or truncated read is never a
# baseline of zero), so old H rows can neither be counted as new nor release the wait.
# A later H row (onServiceConnected writes one, then one every 5 s) proves the service is alive after the dump. It is
# liveness evidence, not proof of which rebind path was taken. Returns 1 after LIVE_WAIT_S wall-clock seconds.
wait_live_after_dump() {
  local hb="" deadline
  deadline=$((SECONDS + LIVE_WAIT_S))
  while (( SECONDS < deadline )); do
    if read_log 2>/dev/null; then
      if [ -z "$hb" ]; then hb=$(count_rows H)            # first SUCCESSFUL read after the dump is the baseline
      elif [ "$(count_rows H)" -gt "$hb" ]; then return 0; fi
    fi
    sleep 0.25
  done
  return 1   # no baseline read, or no later H row, within the deadline: fail closed
}

# Decoy check over the whole post-tap window. $1 = O baseline, $2 = C baseline, both saved immediately before the tap.
# Returns 2 if any A/B label appears at any time in the window, 1 (inconclusive) if there is no fresh C row or no fresh
# valid non-A/B O row at the end of the window, 0 otherwise. It does not name or claim a product label for the decoy.
decoy_window_check() {
  local o_before="$1" c_before="$2" deadline new_c new_o
  deadline=$((SECONDS + DECOY_WAIT_S))
  while (( SECONDS < deadline )); do
    sleep 0.25
    read_log 2>/dev/null || continue
    new_o=$(new_o_rows "$o_before")
    if printf '%s\n' "$new_o" | grep -Eq '^O,-?[0-9]+,[0-9]+,(ALLOWLISTED,1|NOT_ALLOWLISTED,-1)$'; then return 2; fi
  done
  read_log 2>/dev/null || return 1
  new_o=$(new_o_rows "$o_before")
  if printf '%s\n' "$new_o" | grep -Eq '^O,-?[0-9]+,[0-9]+,(ALLOWLISTED,1|NOT_ALLOWLISTED,-1)$'; then return 2; fi
  new_c=$(( $(count_rows C) - c_before ))
  DECOY_NEW_C=$new_c
  DECOY_NEW_NONAB_O=$(printf '%s\n' "$new_o" | grep -Ec '^O,-?[0-9]+,[0-9]+,UNRECOGNISED,-1$' || true)
  [ "$new_c" -gt 0 ] && [ "$DECOY_NEW_NONAB_O" -gt 0 ]
}

# Settings-foreground negative sample. Call after Settings is focused and settled. NO UI dump may run during it (a dump
# suppresses the service). Baselines H/O/C come from the first SUCCESSFUL read; the whole NEG_WAIT_S window must keep
# O and C unchanged and focus on Settings, and H must grow (a live service that saw nothing), else the sample is
# inconclusive rather than a pass. Returns 0 pass, 1 inconclusive (no baseline, stale H or last read failed),
# 2 O or C rows were added, 3 focus was lost. focus_ok is provided by the caller (default: settings_is_focused).
focus_ok() { settings_is_focused; }
settings_negative_sample() {
  local h0="" o0="" c0="" bdl deadline o c h grown=0 last_ok=0
  bdl=$((SECONDS + 5))
  while (( SECONDS < bdl )); do
    if read_log 2>/dev/null; then h0=$(count_rows H); o0=$(count_rows O); c0=$(count_rows C); break; fi
    sleep 0.25
  done
  [ -n "$h0" ] || return 1
  deadline=$((SECONDS + NEG_WAIT_S))
  while (( SECONDS < deadline )); do
    focus_ok || return 3
    if read_log 2>/dev/null; then
      last_ok=1; o=$(count_rows O); c=$(count_rows C); h=$(count_rows H)
      { [ "$o" = "$o0" ] && [ "$c" = "$c0" ]; } || return 2
      [ "$h" -gt "$h0" ] && grown=1
    else
      last_ok=0
    fi
    sleep 0.5
  done
  focus_ok || return 3
  [ "$last_ok" = 1 ] && [ "$grown" = 1 ]
}
