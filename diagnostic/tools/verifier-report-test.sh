#!/usr/bin/env bash
# Standalone host test for the v8 verifier_report block. Usage: bash diagnostic/tools/verifier-report-test.sh diagnostic/tools/emulator-helpers.sh
set -u
T=$(mktemp -d); export PKG=com.vanipy.menit.diag LOG="$T/log.csv" COMPONENT=com.vanipy.menit.diag/com.vanipy.menit.diag.DiagService
. "$1"
mkdir -p "$T/bin"
cat > "$T/bin/adb" <<'X'
#!/bin/sh
case "$*" in
  *"settings get global"*) printf '%s\r\n' "$GLOBALV" ;;
  *"pm list packages -i"*) printf 'package:com.vanipy.menit.diag  installer=%s\r\n' "$INSTALLER" ;;
  *) exit 1 ;;
esac
X
chmod +x "$T/bin/adb"; PATH="$T/bin:$PATH"; export GLOBALV INSTALLER
fails=0; ok() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (got $2, want $3)"; fails=$((fails+1)); fi; }
GLOBALV=1; INSTALLER=com.android.shell; out=$(verifier_report t 2>&1)
ok "ints and installer" "$out" "VERIFIER label=t verify_adb_installs=1 package_verifier_enable=1 package_verifier_user_consent=1 installer=com.android.shell"
GLOBALV=null; INSTALLER=null; out=$(verifier_report t 2>&1); echo "$out" | grep -q 'verify_adb_installs=null .*installer=null'; ok "null shapes" $? 0
GLOBALV='secret words here'; INSTALLER='weird value!'; out=$(verifier_report t 2>&1)
echo "$out" | grep -q 'secret'; ok "global free text never printed" $? 1
echo "$out" | grep -q 'verify_adb_installs=other'; ok "global other shape" $? 0
echo "$out" | grep -q 'installer=other'; ok "installer other shape" $? 0
echo "$out" | grep -q 'weird'; ok "installer free text never printed" $? 1
GLOBALV=""; INSTALLER=""; out=$(verifier_report t 2>&1); echo "$out" | grep -q 'installer=none'; ok "empty installer is none" $? 0
[ "$fails" -eq 0 ] && echo "ALL VERIFIER TESTS PASS" || { echo "FAILS=$fails"; exit 1; }
