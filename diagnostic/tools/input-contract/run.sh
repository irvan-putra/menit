#!/bin/sh
# Host-only fake inputs. Requires a JDK; never installs/starts Android or opens a network connection.
set -eu
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
"${JAVAC_BIN:-javac}" -d "$OUT" ../../app/src/main/java/com/vanipy/menit/diag/Reconciler.java SyntheticInputContract.java InputContractTest.java AttributionTraces.java
java -cp "$OUT" com.vanipy.menit.diag.InputContractTest
java -cp "$OUT" com.vanipy.menit.diag.AttributionTraces
