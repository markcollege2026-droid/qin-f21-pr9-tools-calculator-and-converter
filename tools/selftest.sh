#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Self test for the pure Kotlin modules (JSON, conversion, calculators).
#
# Those modules do not use the Android framework, so they can be verified on a
# plain JVM without a device or an emulator.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${TOOLCHAIN_DIR:-$ROOT/tools/toolchain}"
OUT="${BUILD_DIR:-$ROOT/build}/selftest"

export JAVA_HOME="$TC/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$TC/kotlinc/bin/kotlinc"
KOTLIN_STDLIB="$TC/kotlinc/lib/kotlin-stdlib.jar"

for tool in "$KOTLINC" "$KOTLIN_STDLIB"; do
  [ -e "$tool" ] || { echo "missing tool-chain file '$tool'"; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT"

echo "[selftest] compiling the pure Kotlin modules"
find "$ROOT/core/common/src/main/kotlin" \
     "$ROOT/core/conversion/src/main/kotlin" \
     "$ROOT/core/calc/src/main/kotlin" \
     "$ROOT/core/data/src/main/kotlin" \
     "$ROOT/core/wallet/src/main/kotlin" \
     "$ROOT/tools/selftest" -name "*.kt" > "$OUT/sources.txt"

# core/data is compiled against the platform stubs, only the pure parts are executed
"$KOTLINC" -classpath "$TC/android-30/android.jar" -jvm-target 1.8 -nowarn \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "[selftest] running the checks"
java -cp "$OUT/classes:$KOTLIN_STDLIB" com.qin.f21.toolbox.selftest.Selftest
