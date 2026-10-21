#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Compiles the sources the way Gradle does: the core modules into a jar first,
# then the app *against* that jar.
#
# tools/build-offline.sh hands every module to ONE kotlinc invocation, which is
# faster but hides the module boundary - and Kotlin then accepts code that the
# Gradle build rejects. A smart cast of a nullable property that is declared in
# another module is the case that actually happened here:
#
#     if (entry.counterAmount != null) Money.format(entry.counterAmount, ...)
#
# compiles in one pass and fails with "smart cast is impossible, because
# 'counterAmount' is a public API property declared in different module" as soon
# as core/wallet is a separate compilation unit.
#
# The Gradle CI job stays the authoritative check (it also runs the older Kotlin
# 1.8 compiler, which is stricter still), but this catches the boundary errors
# locally in about half a minute. It generates its own R class, so it does not
# depend on build-offline.sh having run first.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${TOOLCHAIN_DIR:-$ROOT/tools/toolchain}"
OUT="${BUILD_DIR:-$ROOT/build}/modcheck"
APP="$ROOT/app"
ANDROID_JAR="$TC/android-30/android.jar"
KOTLINC="$TC/kotlinc/bin/kotlinc"
KOTLIN_STDLIB="$TC/kotlinc/lib/kotlin-stdlib.jar"
AAPT2="$TC/aapt2"

export JAVA_HOME="$TC/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

for tool in "$KOTLINC" "$KOTLIN_STDLIB" "$ANDROID_JAR" "$AAPT2"; do
  [ -e "$tool" ] || { echo "missing tool-chain file '$tool' - run tools/fetch-toolchain.sh first"; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/rclasses" "$OUT/flat"

# --- 1. R class + vendored Java ----------------------------------------------
# The generated R class and the vendored ZXing core are the Java side of the app
# classpath; CameraActivity references both, so both have to be compiled here.
echo "[modules] generating the R class"
RES_ZIPS=()
for res_dir in "$ROOT"/core/*/src/main/res "$APP/src/main/res"; do
  [ -d "$res_dir" ] || continue
  name=$(basename "$(dirname "$(dirname "$(dirname "$res_dir")")")")
  "$AAPT2" compile --dir "$res_dir" -o "$OUT/res-$name.zip"
  RES_ZIPS+=("$OUT/res-$name.zip")
done
python3 -c 'import sys, zipfile; [zipfile.ZipFile(a).extractall(sys.argv[1]) for a in sys.argv[2:]]' \
  "$OUT/flat" "${RES_ZIPS[@]}"
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/src/main/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --auto-add-overlay \
  --min-sdk-version 30 --target-sdk-version 30 \
  "$OUT/flat"/*.flat >/dev/null
find "$OUT/gen" -name "*.java" > "$OUT/java-sources.txt"
find "$ROOT/third_party" -name "*.java" >> "$OUT/java-sources.txt"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/rclasses" @"$OUT/java-sources.txt"
(cd "$OUT/rclasses" && jar cf "$OUT/rclasses.jar" .)

# --- 2. core modules ---------------------------------------------------------
echo "[modules] 1/2 core modules -> core.jar"
find "$ROOT"/core/*/src/main/kotlin -name "*.kt" > "$OUT/core-sources.txt"
"$KOTLINC" \
  -classpath "$ANDROID_JAR" \
  -jvm-target 1.8 -nowarn -jvm-default=enable \
  -d "$OUT/core.jar" \
  @"$OUT/core-sources.txt"

# --- 3. app against the core modules ----------------------------------------
echo "[modules] 2/2 app against core.jar (the module boundary)"
find "$ROOT/app/src/main/kotlin" -name "*.kt" > "$OUT/app-sources.txt"
"$KOTLINC" \
  -classpath "$ANDROID_JAR:$OUT/rclasses.jar:$OUT/core.jar" \
  -jvm-target 1.8 -nowarn -jvm-default=enable \
  -d "$OUT/app-classes" \
  @"$OUT/app-sources.txt"

echo "[modules] OK - the app compiles against the core modules as separate units"
