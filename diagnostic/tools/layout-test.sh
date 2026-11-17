#!/usr/bin/env bash
# Host test for back_fully_visible. Usage: bash layout-test.sh HELPERS_WITH_APPEND
LOG=/dev/null; T=$(mktemp -d); . "$1"
fails=0; ok() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (got $2, want $3)"; fails=$((fails+1)); fi; }
mk() { # $1 = back bounds or "none"
  { echo '<?xml version="1.0"?><hierarchy rotation="0"><node class="android.widget.FrameLayout" bounds="[0,0][1080,2337]"><node class="android.widget.ScrollView" bounds="[0,63][1080,2337]">'
    echo '<node class="android.widget.TextView" text="Synthetic chat A" bounds="[47,111][1033,200]"/>'
    [ "$1" != none ] && echo "<node class=\"android.widget.Button\" text=\"Back\" bounds=\"$1\"/>"
    echo '</node></node></hierarchy>'; } > "$T/d.xml"; }
mk '[42,220][1038,346]'; back_fully_visible "$T/d.xml" >/dev/null; ok "Back under header: pass" $? 0
mk '[42,2260][1038,2337]'; out=$(back_fully_visible "$T/d.xml"); rc=$?; ok "Back clipped at viewport edge: fail" $rc 1
echo "$out" | grep -q 'not fully inside'; ok "clipped message" $? 0
mk '[42,2200][1038,2337]'; back_fully_visible "$T/d.xml" >/dev/null; ok "Back ending exactly at the edge: fail" $? 1
mk none; out=$(back_fully_visible "$T/d.xml"); rc=$?; ok "Back off-screen (absent): fail" $rc 1; echo "$out" | grep -q 'missing'; ok "missing message" $? 0
mk '[42,20][1038,146]'; back_fully_visible "$T/d.xml" >/dev/null; ok "Back above viewport top: fail" $? 1
mk '[42,220][1038,240]'; back_fully_visible "$T/d.xml" >/dev/null; ok "degenerate height: fail" $? 1
echo 'not xml' > "$T/d.xml"; back_fully_visible "$T/d.xml" >/dev/null; ok "unreadable dump: fail" $? 1
[ "$fails" -eq 0 ] && echo "ALL LAYOUT TESTS PASS" || { echo "FAILS=$fails"; exit 1; }
