#!/bin/sh
# Host-only: runs the real DiagStore/LogParser/Reconciler against minimal Android stubs. Exits non-zero on any failure.
set -e
cd "$(dirname "$0")"; SRC=../../app/src/main/java/com/vanipy/menit/diag; OUT=$(mktemp -d)
javac -d "$OUT" $(find android -name '*.java') $SRC/DiagStore.java $SRC/LogParser.java $SRC/Reconciler.java com/vanipy/menit/diag/T.java
java -cp "$OUT" com.vanipy.menit.diag.T | tee "$OUT/r.txt"; grep -q '^ALL PASS' "$OUT/r.txt"; ! grep -q '^FAIL' "$OUT/r.txt"
