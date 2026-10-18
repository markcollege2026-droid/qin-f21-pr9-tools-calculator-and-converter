#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Fetches the offline build tool-chain used by tools/build-offline.sh and
# tools/selftest.sh.
#
# The tool-chain is intentionally kept OUT of the repository (see .gitignore).
# Everything is fetched from public mirrors that do not require Google Play or
# a developer account:
#
#   * JDK 11           - npm package "jdk11" (AdoptOpenJDK 11.0.10, full JDK:
#                        java, javac, jar, keytool)
#   * Kotlin compiler  - npm package "kotlin-compiler"
#   * aapt2            - PyPI wheel "aapt2" (Linux binary)
#   * d8 (dexer)       - GitHub mirror of the Android build-tools d8.jar
#   * apksigner        - GitHub mirror of the Android build-tools apksigner.jar
#   * android.jar      - GitHub mirror of the API 30 platform (Android 11)
#
# Every version is pinned so the build is reproducible. Each component is
# verified right after download and only skipped when a ".done-*" marker
# proves that a previous run completed it, so a partial or interrupted
# download is retried instead of being trusted.
#
# The resulting app itself is 100% offline: no network permission, no
# analytics, no cloud services.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/tools/toolchain"
TMP="$TC/tmp"
mkdir -p "$TC"

log()  { printf '\033[36m[toolchain]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[toolchain] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# --- pinned artefact locations ------------------------------------------------
# The blob SHAs are the git blob ids of the exact files (verified by
# re-hashing the downloaded bytes: sha1("blob <len>\0<content>")).
D8_REPO="Promastergame/tinyapk-lab"
D8_BLOB="e169b926a8ad7f8418a2c8b2ec4101ae156976bd"            # tools/windows/d8.jar, D8 8.2.2-dev
APKSIGNER_REPO="warren-bank/print-apk-signature"
APKSIGNER_BLOB="5b38de849a1c655bb4c776ec1c1a9e5f4ea3f8f7"     # libs/apksigner/apksigner.jar, v0.9
ANDROID_REPO="Sable/android-platforms"
ANDROID_BLOB="4d76f1707c0640ff5119af5570619d601779a866"       # android-30/android.jar, API 30

# Fetch one file from a public GitHub repository by its blob sha.
# Tries the authenticated "gh" CLI first (higher rate limits), then falls
# back to the unauthenticated REST API.
fetch_github_blob() { # repo blob-sha destination
  local repo="$1" blob="$2" dest="$3"
  mkdir -p "$TMP"
  if command -v gh >/dev/null 2>&1; then
    if gh api "repos/$repo/git/blobs/$blob" --jq .content 2>/dev/null \
        | tr -d '\n' | base64 -d > "$dest" && [ -s "$dest" ]; then
      return 0
    fi
    log "authenticated fetch of $repo#$blob failed, retrying via the public API"
    rm -f "$dest"
  fi
  curl -fsSL "https://api.github.com/repos/$repo/git/blobs/$blob" -o "$TMP/blob.json"
  python3 - "$TMP/blob.json" "$dest" <<'PY'
import base64, json, sys
with open(sys.argv[1], "r", encoding="utf-8") as fh:
    doc = json.load(fh)
with open(sys.argv[2], "wb") as out:
    out.write(base64.b64decode(doc["content"]))
PY
}

# --- component bookkeeping ----------------------------------------------------
is_done() { [ -f "$TC/.done-$1" ]; }
mark() { echo "ok" > "$TC/.done-$1"; }

# --- JDK 11 (java, javac, jar, keytool) ---------------------------------------
if ! is_done jdk; then
  rm -rf "$TC/jdk"
  log "Downloading JDK 11 (npm jdk11@2.0.0 - AdoptOpenJDK 11.0.10)"
  mkdir -p "$TMP"
  (cd "$TMP" && npm pack jdk11@2.0.0 >/dev/null 2>&1) || fail "npm pack jdk11@2.0.0 failed"
  tar xzf "$TMP/jdk11-2.0.0.tgz" -C "$TMP"
  # the package ships the JDK as 10 MB split tar chunks
  cat "$TMP"/package/jdk11.tar.0* > "$TMP/jdk11.tar"
  mkdir -p "$TMP/jdk"
  tar xf "$TMP/jdk11.tar" -C "$TMP/jdk"
  mv "$TMP/jdk/jdk11/jdk-11" "$TC/jdk"
  rm -rf "$TMP/jdk11-2.0.0.tgz" "$TMP/jdk11.tar" "$TMP/jdk" "$TMP/package"
  chmod +x "$TC/jdk/bin/"* 2>/dev/null || true
  "$TC/jdk/bin/java" -version >/dev/null || fail "jdk11: java failed to run"
  "$TC/jdk/bin/javac" -version >/dev/null || fail "jdk11: javac failed to run"
  "$TC/jdk/bin/jar" --version >/dev/null || fail "jdk11: jar failed to run"
  mark jdk
fi

# --- Kotlin compiler -----------------------------------------------------------
if ! is_done kotlinc; then
  rm -rf "$TC/kotlinc"
  log "Downloading Kotlin compiler (npm kotlin-compiler@2.4.21)"
  mkdir -p "$TMP"
  (cd "$TMP" && npm pack kotlin-compiler@2.4.21 >/dev/null 2>&1) || fail "npm pack kotlin-compiler@2.4.21 failed"
  tar xzf "$TMP/kotlin-compiler-2.4.21.tgz" -C "$TMP"
  mv "$TMP/package" "$TC/kotlinc"
  rm -f "$TMP"/kotlin-compiler-*.tgz
  chmod +x "$TC/kotlinc/bin/"* 2>/dev/null || true
  PATH="$TC/jdk/bin:$PATH" "$TC/kotlinc/bin/kotlinc" -version >/dev/null 2>&1 \
    || fail "kotlin-compiler: kotlinc failed to run"
  mark kotlinc
fi

# --- aapt2 ----------------------------------------------------------------------
if ! is_done aapt2; then
  rm -f "$TC/aapt2"
  log "Downloading aapt2 (PyPI aapt2==0.2.1)"
  mkdir -p "$TMP"
  python3 -m pip download aapt2==0.2.1 --no-deps -d "$TMP" >/dev/null \
    || fail "pip download aapt2==0.2.1 failed"
  python3 - "$TMP" "$TC" <<'PY'
import glob, os, sys, zipfile
wheel = sorted(glob.glob(os.path.join(sys.argv[1], "aapt2-*.whl")))[0]
with zipfile.ZipFile(wheel) as z:
    with z.open("aapt2/bin/Linux/aapt2") as src, open(os.path.join(sys.argv[2], "aapt2"), "wb") as dst:
        dst.write(src.read())
PY
  chmod +x "$TC/aapt2"
  "$TC/aapt2" version >/dev/null || fail "aapt2 failed to run"
  mark aapt2
fi

# --- d8 + apksigner (pinned GitHub mirrors) ------------------------------------
if ! is_done d8; then
  log "Downloading d8 (Android build-tools mirror, pinned blob)"
  fetch_github_blob "$D8_REPO" "$D8_BLOB" "$TC/d8.jar" || fail "could not fetch d8.jar"
  "$TC/jdk/bin/java" -cp "$TC/d8.jar" com.android.tools.r8.D8 --version >/dev/null \
    || fail "d8.jar failed to run"
  mark d8
fi

if ! is_done apksigner; then
  log "Downloading apksigner (Android build-tools mirror, pinned blob)"
  fetch_github_blob "$APKSIGNER_REPO" "$APKSIGNER_BLOB" "$TC/apksigner.jar" \
    || fail "could not fetch apksigner.jar"
  "$TC/jdk/bin/java" -cp "$TC/apksigner.jar" com.android.apksigner.ApkSignerTool --version >/dev/null \
    || fail "apksigner.jar failed to run"
  mark apksigner
fi

# --- android.jar (API 30 = Android 11, pinned GitHub mirror) -------------------
if ! is_done android; then
  log "Downloading android.jar for API 30 (Android 11, pinned blob)"
  mkdir -p "$TC/android-30"
  fetch_github_blob "$ANDROID_REPO" "$ANDROID_BLOB" "$TC/android-30/android.jar" \
    || fail "could not fetch android-30/android.jar"
  [ "$(wc -c < "$TC/android-30/android.jar")" -gt 10000000 ] \
    || fail "android.jar looks truncated"
  mark android
fi

rm -rf "$TMP"

# --- summary --------------------------------------------------------------------
log "Tool-chain ready in $TC"
"$TC/jdk/bin/java" -version 2>&1 | head -1
"$TC/jdk/bin/javac" -version 2>&1 | head -1
PATH="$TC/jdk/bin:$PATH" "$TC/kotlinc/bin/kotlinc" -version 2>&1 | head -1
"$TC/aapt2" version 2>&1 | head -1
