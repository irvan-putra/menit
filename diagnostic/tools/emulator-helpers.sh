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
  local hb="" deadline lastp=-1
  deadline=$((SECONDS + LIVE_WAIT_S))
  while (( SECONDS < deadline )); do
    if [ "${PROBE_IN_WAIT:-1}" = 1 ] && (( SECONDS != lastp )); then lastp=$SECONDS; probe wait_live; fi   # informational only
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

# ---- v6 diagnostic-only probes (informational; never gate anything, never print log contents) ----
PROBE_LOG=${PROBE_LOG:-${LOG}.probe}
ROW_ALLOW_RE='^(S,-?[0-9]+,[0-9]+,[0-9]+|H,-?[0-9]+,[0-9]+|C,-?[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+|FS,-?[0-9]+,[0-9]+|F,-?[0-9]+,[0-9]+,[0-9]+|O,-?[0-9]+,[0-9]+,(ALLOWLISTED,1|NOT_ALLOWLISTED,-1|UNRECOGNISED,-1))$'
# 0 only if every line of file $1 is an allowlisted typed row (empty file is valid)
log_valid() { ! grep -Evq "$ROW_ALLOW_RE" "$1"; }
# Shape only, never the raw value: listed | empty | other
enabled_shape() {
  local v; v=$(timeout 5s adb shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r') || v="?"
  case "$v" in *"${COMPONENT:-@@none@@}"*) echo listed ;; ""|null) echo empty ;; "?") echo unreadable ;; *) echo other ;; esac
}
a11y_shape() {
  local v; v=$(timeout 5s adb shell settings get secure accessibility_enabled 2>/dev/null | tr -d '\r') || v="?"
  case "$v" in 0|1|null) echo "$v" ;; *) echo other ;; esac
}
# One timestamped line to stderr. Uses its own file so it cannot disturb $LOG baselines. Counts only after whole-file
# validation; otherwise prints counts as "invalid" and never any row content.
PROBE_LAST_LISTED_DEV=""; PROBE_FIRST_GONE_DEV=""
# Device clock (same clock as logcat) HH:MM:SS and device uptime seconds; shape-checked, never free text.
dev_clock() { local v; v=$(timeout 3s adb shell date +%H:%M:%S 2>/dev/null | tr -d '\r') || v=""; case "$v" in [0-2][0-9]:[0-5][0-9]:[0-5][0-9]) echo "$v" ;; *) echo unknown ;; esac; }
dev_uptime() { local v; v=$(timeout 3s adb shell cat /proc/uptime 2>/dev/null | tr -d '\r' | cut -d' ' -f1) || v=""; case "$v" in ''|*[!0-9.]*) echo unknown ;; *) echo "$v" ;; esac; }
# One timestamped line to stderr. Uses its own file so it cannot disturb $LOG baselines. Counts come only after
# whole-file validation; otherwise "invalid" and never any row content. Call it directly (not in a subshell) so the
# PROBE_*_DEV bracket variables persist. They record the device-clock time of the last probe that saw the component
# listed and the first later probe that did not. That brackets the change between two samples; it is not the change time.
probe() {
  local label="$1" h="unreadable" n="unreadable" en a11y dev up pre post
  # Device clock immediately BEFORE and AFTER the enabled-setting read. The later reads below can be slow, so only
  # these two stamps bound that read: pre is the lower bound of a "listed" sample, post the upper bound of a "gone" one.
  pre=$(dev_clock); en=$(enabled_shape); post=$(dev_clock)
  a11y=$(a11y_shape)
  if timeout 3s adb shell run-as "$PKG" cat files/diag-log.csv > "$PROBE_LOG" 2>/dev/null; then
    if log_valid "$PROBE_LOG"; then h=$(grep -c '^H,' "$PROBE_LOG" || true); n=$(grep -c '' "$PROBE_LOG" || true); else h=invalid; n=invalid; fi
  fi
  dev=$(dev_clock); up=$(dev_uptime)
  if [ "$en" = listed ]; then PROBE_LAST_LISTED_DEV="$pre"; PROBE_FIRST_GONE_DEV=""
  elif [ -n "$PROBE_LAST_LISTED_DEV" ] && [ -z "$PROBE_FIRST_GONE_DEV" ] && [ "$en" != unreadable ]; then PROBE_FIRST_GONE_DEV="$post"; fi
  echo "PROBE label=$label sec=$SECONDS utc=$(date -u +%H:%M:%S) dev=$dev up=$up enabled=$en a11y=$a11y H=$h rows=$n" >&2
}
# Informational no-UI sample (no dump runs): $1 label prefix, $2 seconds; back-to-back probes (each read takes ~1 s)
probe_window() {
  local end=$((SECONDS + $2))
  while (( SECONDS < end )); do probe "$1"; sleep 0.3; done
}
# Unfiltered-by-subject but noise-reduced system logcat for the bracket between the last probe that saw the component
# listed and the first one that did not (device clock, 1 s padding). Fixed bounds: 150 lines, 240 chars/line, 20000 bytes.
# Disposable synthetic emulator only. Prints nothing if no bracket was recorded.
bracket_logcat() {
  local a="$PROBE_LAST_LISTED_DEV" b="$PROBE_FIRST_GONE_DEV"
  case "$a" in [0-2][0-9]:[0-5][0-9]:[0-5][0-9]) ;; *) echo "bracket: no sample saw the component listed" >&2; return 0 ;; esac
  case "$b" in [0-2][0-9]:[0-5][0-9]:[0-5][0-9]) ;; *) echo "bracket: component never seen gone in a probe" >&2; return 0 ;; esac
  # Same-day clock only: an unknown stamp (checked above) or a later stamp that is not >= the earlier one (midnight
  # rollover or clock step) is never presented as a valid bracket.
  if [[ "$b" < "$a" ]]; then echo "bracket: invalid (device clock went backwards or rolled over: $a then $b)" >&2; return 0; fi
  {
    echo "bracket (device clock, widened to the bounds of the enabled-setting reads): earliest-possible-loss start $a, latest-gone end $b"
    timeout 15s adb logcat -b all -d -v threadtime 2>/dev/null | awk -v a="$a" -v b="$b" '
      function s(t,  p){ split(t,p,/[:.]/); return p[1]*3600+p[2]*60+p[3] }
      { if (s($2) >= s(a)-1 && s($2) <= s(b)+1) print }' \
      | grep -Ev 'AppsFilter|am_pss|Looper|ImeTracker|input_focus|SystemServerTiming|GoogleInputMethod' \
      | head -150 | cut -c1-240
  } | head -c 20000 >&2 || true
}
# Bounded failure evidence. Typed row counts only after whole-file validation; logcat limited to fixed patterns, line
# length and total bytes. No arbitrary content dump.
typed_counts() {
  if [ ! -s "$1" ]; then echo "log: empty or unreadable" >&2; return 0; fi
  if log_valid "$1"; then
    echo "log typed rows: S=$(grep -c '^S,' "$1" || true) H=$(grep -c '^H,' "$1" || true) C=$(grep -c '^C,' "$1" || true) O=$(grep -c '^O,' "$1" || true) FS=$(grep -c '^FS,' "$1" || true) F=$(grep -c '^F,' "$1" || true)" >&2
  else
    echo "log: contains non-allowlisted shapes (not printed)" >&2
  fi
}
LOGCAT_PATTERN='Force stopping|force-stop|forceStop|UiAutomation|AccessibilityManagerService|AccessibilityServiceConnection|am_kill|am_proc_died|Process .* has died|am_crash|am_anr|PackageManager.*(removed|replaced)|Play Protect|VerifyApps|Skipping enabling service|disallowed by device admin|SettingsProvider|enabled_accessibility_services|accessibility_enabled|onUnbind|Unbinding|unbind'
bounded_logcat() {
  timeout 15s adb logcat -b all -d -v threadtime 2>/dev/null | grep -E "${PKG//./\\.}|$LOGCAT_PATTERN" | tail -60 | cut -c1-240 | head -c 12000 >&2 || true
}
