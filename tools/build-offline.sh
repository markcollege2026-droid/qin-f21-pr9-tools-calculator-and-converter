#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Offline build for the Qin F21 Toolbox.
#
# Builds a signed, installable APK using only the tool-chain in tools/toolchain:
#   aapt2 (resources) -> javac (R class) -> kotlinc (app code) -> d8 (dex)
#   -> package -> apksigner
#
# The Gradle build (settings.gradle.kts) produces the same app for environments
# that have a standard Android SDK installed.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${TOOLCHAIN_DIR:-$ROOT/tools/toolchain}"
BUILD="${BUILD_DIR:-$ROOT/build}"
APP="$ROOT/app"
VERSION_NAME="1.0.0"
VERSION_CODE="1"
MIN_SDK=30
TARGET_SDK=30

ANDROID_JAR="$TC/android-30/android.jar"
KOTLINC="$TC/kotlinc/bin/kotlinc"
KOTLIN_STDLIB="$TC/kotlinc/lib/kotlin-stdlib.jar"
AAPT2="$TC/aapt2"
D8_JAR="$TC/d8.jar"
APKSIGNER_JAR="$TC/apksigner.jar"

export JAVA_HOME="$TC/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

log()  { printf '\033[36m[build]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[build] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

for tool in "$ANDROID_JAR" "$KOTLINC" "$KOTLIN_STDLIB" "$AAPT2" "$D8_JAR" "$APKSIGNER_JAR"; do
  [ -e "$tool" ] || fail "missing tool-chain file '$tool' - run tools/fetch-toolchain.sh first"
done

rm -rf "$BUILD"
mkdir -p "$BUILD"/{res,gen,classes,rclasses,dex,apk}

# --- 1. compile resources ----------------------------------------------------
log "compiling resources"
RES_ZIPS=()
for res_dir in "$ROOT"/core/*/src/main/res "$APP/src/main/res"; do
  [ -d "$res_dir" ] || continue
  name=$(basename "$(dirname "$(dirname "$(dirname "$res_dir")")")")
  out="$BUILD/res/$name.zip"
  "$AAPT2" compile --dir "$res_dir" -o "$out"
  RES_ZIPS+=("$out")
done
[ ${#RES_ZIPS[@]} -gt 0 ] || fail "no resource directories found"

# aapt2 link expects the compiled .flat files, the compile step emits them inside a zip
mkdir -p "$BUILD/flat"
python3 -c 'import sys, zipfile; [zipfile.ZipFile(a).extractall(sys.argv[1]) for a in sys.argv[2:]]' \
  "$BUILD/flat" "${RES_ZIPS[@]}"
RES_FLAT=("$BUILD/flat"/*.flat)

# --- 2. link resources + manifest -------------------------------------------
log "linking resources"
"$AAPT2" link \
  -o "$BUILD/apk/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/src/main/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --auto-add-overlay \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  "${RES_FLAT[@]}"

# --- 3. compile the generated R class and the vendored Java sources ----------
log "compiling Java sources (R class + ZXing core)"
find "$BUILD/gen" -name "*.java" > "$BUILD/gen-sources.txt"
find "$ROOT/third_party" -name "*.java" >> "$BUILD/gen-sources.txt"
[ -s "$BUILD/gen-sources.txt" ] || fail "no Java sources found"
mkdir -p "$BUILD/rclasses"
if ! javac -nowarn -encoding UTF-8 -source 8 -target 8 \
      -bootclasspath "$ANDROID_JAR" \
      -d "$BUILD/rclasses" @"$BUILD/gen-sources.txt" > "$BUILD/javac.log" 2>&1; then
  cat "$BUILD/javac.log"
  fail "javac failed"
fi

# --- 4. compile the Kotlin sources ------------------------------------------
log "compiling Kotlin sources"
find "$ROOT"/core/*/src/main/kotlin "$APP/src/main/kotlin" -name "*.kt" > "$BUILD/kt-sources.txt"
[ -s "$BUILD/kt-sources.txt" ] || fail "no Kotlin sources found"
"$KOTLINC" \
  -classpath "$ANDROID_JAR:$BUILD/rclasses" \
  -jvm-target 1.8 \
  -nowarn \
  -jvm-default=enable \
  -d "$BUILD/classes" \
  @"$BUILD/kt-sources.txt"

# --- 5. dex ------------------------------------------------------------------
log "dexing"
# d8 only accepts archives or single class files, not directories
(cd "$BUILD/classes" && jar cf "$BUILD/classes.jar" .)
(cd "$BUILD/rclasses" && jar cf "$BUILD/rclasses.jar" .)
java -cp "$D8_JAR" com.android.tools.r8.D8 \
  --lib "$ANDROID_JAR" \
  --min-api "$MIN_SDK" \
  --release \
  --output "$BUILD/dex" \
  "$BUILD/classes.jar" "$BUILD/rclasses.jar" "$KOTLIN_STDLIB"

# --- 6. package --------------------------------------------------------------
log "packaging"
python3 "$ROOT/tools/package_apk.py" \
  "$BUILD/apk/base.apk" "$BUILD/dex/classes.dex" "$BUILD/apk/unsigned.apk" "$APP/src/main/assets"

# --- 7. sign -----------------------------------------------------------------
log "signing (debug key)"
mkdir -p "$ROOT/build/outputs"
KS="$BUILD/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -storepass android -alias androiddebugkey \
    -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Qin F21 Toolbox Debug,O=Offline,C=IL" >/dev/null 2>&1
fi
java -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$ROOT/build/outputs/qin-f21-toolbox-$VERSION_NAME.apk" \
  "$BUILD/apk/unsigned.apk"

APK="$ROOT/build/outputs/qin-f21-toolbox-$VERSION_NAME.apk"
log "verifying signature"
java -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool verify --print-certs "$APK" | head -3

python3 - "$APK" <<'PY'
import os, sys, zipfile
apk = sys.argv[1]
total_zip = os.path.getsize(apk)
uncompressed = 0
with zipfile.ZipFile(apk) as z:
    for info in z.infolist():
        uncompressed += info.file_size
print("\n=== size report ===")
print("APK (signed)          : %8.2f MB" % (total_zip / 1048576.0))
print("uncompressed payload  : %8.2f MB" % (uncompressed / 1048576.0))
print("classes.dex           : %8.2f MB" % (os.path.getsize(os.path.join(os.path.dirname(apk), "..", "dex", "classes.dex")) / 1048576.0))
PY
log "APK: $APK"
