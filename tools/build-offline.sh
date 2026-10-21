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
VERSION_NAME="1.1.0"
VERSION_CODE="2"
# Android 11 (API 30) is the floor the app is built and tested for; API 36 (Android 16) is
# what the manifest now targets, which is what current Android versions require.
MIN_SDK=30
COMPILE_SDK=36
TARGET_SDK=36
BUILD_TYPE="${BUILD_TYPE:-release}"

ANDROID_JAR="$TC/android-$COMPILE_SDK/android.jar"
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
# --debug-mode sets android:debuggable="true" in the linked manifest, which is what makes a
# debug build debuggable without AGP to inject the flag.
LINK_FLAGS=()
[ "$BUILD_TYPE" = "debug" ] && LINK_FLAGS+=(--debug-mode)
"$AAPT2" link \
  -o "$BUILD/apk/base.apk" \
  -I "$ANDROID_JAR" \
  "${LINK_FLAGS[@]}" \
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
# The default JVM heap of the compiler is not enough for this many sources on a small CI
# runner; 1.5 GB is comfortably above what the module set needs (measured ~600 MB).
KOTLIN_HEAP="${KOTLIN_HEAP:-1536m}"
"$KOTLINC" \
  -classpath "$ANDROID_JAR:$BUILD/rclasses" \
  -jvm-target 1.8 \
  -nowarn \
  -J-Xmx"$KOTLIN_HEAP" \
  -J-XX:MaxMetaspaceSize=512m \
  -J-XX:+UseSerialGC \
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
# A release build is signed with a release key, never with the debug key. The key is
# taken from the environment when one is supplied, otherwise a project key is generated
# once under build/ (which is not committed) so that consecutive release builds keep the
# same signing identity - an app cannot be updated in place by a key it was not signed
# with.
mkdir -p "$ROOT/build/outputs"
if [ "$BUILD_TYPE" = "release" ]; then
  # Kept outside build/ on purpose: build/ is wiped at the start of every run, and an
  # update can only be installed over the old app by the same key. Set RELEASE_KEYSTORE to
  # sign with a key of your own.
  KS="${RELEASE_KEYSTORE:-$ROOT/.release-keystore/release.keystore}"
  ALIAS="${RELEASE_KEY_ALIAS:-qintoolbox}"
  STORE_PASS="${RELEASE_STORE_PASS:-qin-f21-toolbox}"
  KEY_PASS="${RELEASE_KEY_PASS:-$STORE_PASS}"
  if [ ! -f "$KS" ]; then
    mkdir -p "$(dirname "$KS")"
    log "creating release key at $KS (set RELEASE_KEYSTORE to use your own)"
    keytool -genkeypair -v -keystore "$KS" -storepass "$STORE_PASS" -alias "$ALIAS" \
      -keypass "$KEY_PASS" -keyalg RSA -keysize 4096 -validity 10000 \
      -dname "CN=Qin F21 Toolbox,O=Qin F21 Toolbox,C=IL" >/dev/null 2>&1 \
      || fail "could not create the release keystore"
  fi
  OUT="$ROOT/build/outputs/qin-f21-toolbox-$VERSION_NAME-release.apk"
else
  KS="$BUILD/debug.keystore"
  ALIAS="androiddebugkey"
  STORE_PASS="android"
  KEY_PASS="android"
  if [ ! -f "$KS" ]; then
    keytool -genkeypair -v -keystore "$KS" -storepass android -alias androiddebugkey \
      -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=Qin F21 Toolbox Debug,O=Offline,C=IL" >/dev/null 2>&1
  fi
  OUT="$ROOT/build/outputs/qin-f21-toolbox-$VERSION_NAME-debug.apk"
fi
log "signing ($BUILD_TYPE key)"
# apksigner only writes the schemes the minimum SDK actually needs: with minSdk 30 that
# is APK Signature Scheme v3 (supported since Android 9), so v1 is switched off on purpose -
# a JAR signature would only be useful below Android 7, which this app does not support.
java -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool sign \
  --ks "$KS" --ks-pass "pass:$STORE_PASS" --ks-key-alias "$ALIAS" --key-pass "pass:$KEY_PASS" \
  --min-sdk-version "$MIN_SDK" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$BUILD/apk/unsigned.apk"

APK="$OUT"
log "verifying signature (must verify for API $MIN_SDK)"
java -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool verify \
  --min-sdk-version "$MIN_SDK" --print-certs "$APK" > "$BUILD/verify.log" 2>&1 \
  || { cat "$BUILD/verify.log"; fail "the APK does not verify for API $MIN_SDK"; }
head -3 "$BUILD/verify.log"
java -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool verify --verbose "$APK" \
  | grep "Verified using" | sed 's/^/  /'

# --- 7b. check the SDK levels the APK actually carries ------------------------
log "checking min/target SDK"
BADGING=$("$AAPT2" dump badging "$APK")
# aapt2 2.20 prints "minSdkVersion:", older builds print "sdkVersion:" - accept both.
echo "$BADGING" | grep -E "^package:|^(min)?[sS]dkVersion:|^targetSdkVersion:" | sed 's/^/  /'
(echo "$BADGING" | grep -qE "^(min)?[sS]dkVersion:'$MIN_SDK'") || fail "minSdk is not $MIN_SDK"
echo "$BADGING" | grep -q "targetSdkVersion:'$TARGET_SDK'" || fail "targetSdk is not $TARGET_SDK"
if [ "$BUILD_TYPE" = "release" ]; then
  if echo "$BADGING" | grep -q "application-debuggable"; then
    fail "the release APK is debuggable - this is not a release build"
  fi
  echo "  debuggable: no"
else
  echo "$BADGING" | grep -q "application-debuggable" \
    || fail "the debug APK is not debuggable - android:debuggable is missing"
  echo "  debuggable: yes (debug build)"
fi
echo "  version: $(echo "$BADGING" | sed -n "s/^package: .*versionCode='\([0-9]*\)' versionName='\([^']*\)'.*/\1 \/ \2/p")"

# --- 8. verify install requirements ------------------------------------------
# Android 11 (API 30, "R") and newer reject an APK whose resources.arsc is not
# stored uncompressed and aligned on a 4-byte boundary, so this check is part
# of the build, not a spot check.
log "verifying resources.arsc alignment (required to install on API 30+)"
python3 - "$APK" <<'PY' || fail "resources.arsc is not stored/aligned - the APK would fail to install"
import sys, zipfile
apk = sys.argv[1]
raw = open(apk, "rb").read()
with zipfile.ZipFile(apk) as z:
    info = z.getinfo("resources.arsc")
off = info.header_offset
sig = raw[off:off + 4]
name_len = int.from_bytes(raw[off + 26:off + 28], "little")
extra_len = int.from_bytes(raw[off + 28:off + 30], "little")
method = int.from_bytes(raw[off + 8:off + 10], "little")
data_off = off + 30 + name_len + extra_len
assert sig == b"PK\x03\x04", "resources.arsc local header not found"
assert method == zipfile.ZIP_STORED, "resources.arsc must be stored uncompressed"
assert data_off % 4 == 0, f"resources.arsc data offset {data_off} not 4-byte aligned"
print(f"resources.arsc: stored uncompressed, data offset {data_off}, 4-byte aligned - OK")
PY

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
