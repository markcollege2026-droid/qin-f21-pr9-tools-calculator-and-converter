#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Fetches the offline build tool-chain used by tools/build-offline.sh.
#
# The tool-chain is intentionally kept OUT of the repository (see .gitignore).
# Everything is fetched from public mirrors that do not require Google Play or
# a developer account:
#
#   * JRE              - PyPI wheel "jdk4py"
#   * Kotlin compiler  - npm package "kotlin-compiler"
#   * aapt2            - PyPI wheel "aapt2" (Linux binary)
#   * d8 (dexer)      - GitHub mirror of the Android build-tools d8.jar
#   * apksigner        - GitHub mirror of the Android build-tools apksigner.jar
#   * android.jar      - GitHub mirror of the API 30 platform (Android 11)
#
# The resulting app itself is 100% offline: no network permission, no
# analytics, no cloud services.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/tools/toolchain"
mkdir -p "$TC"

log() { printf '\033[36m[toolchain]\033[0m %s\n' "$*"; }

# --- JRE --------------------------------------------------------------------
if [ ! -x "$TC/jre/bin/java" ]; then
  log "Downloading JRE (jdk4py wheel)"
  python3 -m pip download jdk4py --no-deps -d "$TC/tmp" >/dev/null
  mkdir -p "$TC/jre"
  python3 - "$TC/tmp" "$TC/jre" <<'PY'
import glob, os, sys, zipfile
wheel = glob.glob(os.path.join(sys.argv[1], "jdk4py-*.whl"))[0]
with zipfile.ZipFile(wheel) as z:
    for name in z.namelist():
        if name.startswith("jdk4py/java-runtime/"):
            z.extract(name, sys.argv[2])
PY
  mv "$TC/jre/jdk4py/java-runtime"/* "$TC/jre/" 2>/dev/null || true
  rm -rf "$TC/jre/jdk4py" "$TC/tmp"
  chmod +x "$TC/jre/bin/"* 2>/dev/null || true
fi

# --- Kotlin compiler --------------------------------------------------------
if [ ! -x "$TC/kotlinc/bin/kotlinc" ]; then
  log "Downloading Kotlin compiler (npm)"
  (cd "$TC" && npm pack kotlin-compiler >/dev/null)
  tar xzf "$TC"/kotlin-compiler-*.tgz -C "$TC"
  mv "$TC/package" "$TC/kotlinc"
  rm -f "$TC"/kotlin-compiler-*.tgz
  chmod +x "$TC/kotlinc/bin/"* 2>/dev/null || true
fi

# --- aapt2 ------------------------------------------------------------------
if [ ! -x "$TC/aapt2" ]; then
  log "Downloading aapt2 (PyPI wheel)"
  python3 -m pip download aapt2 --no-deps -d "$TC/tmp" >/dev/null
  python3 - "$TC/tmp" "$TC" <<'PY'
import glob, os, sys, zipfile
wheel = glob.glob(os.path.join(sys.argv[1], "aapt2-*.whl"))[0]
with zipfile.ZipFile(wheel) as z:
    with z.open("aapt2/bin/Linux/aapt2") as src, open(os.path.join(sys.argv[2], "aapt2"), "wb") as dst:
        dst.write(src.read())
PY
  rm -rf "$TC/tmp"
  chmod +x "$TC/aapt2"
fi

# --- d8 + apksigner ---------------------------------------------------------
fetch_github_blob() { # repo path destination
  local repo="$1" path="$2" dest="$3" sha
  sha=$(gh api "repos/$repo/git/trees/HEAD?recursive=1" --jq ".tree[] | select(.path==\"$path\") | .sha")
  if [ -z "$sha" ]; then echo "Could not locate $path in $repo" >&2; return 1; fi
  gh api "repos/$repo/git/blobs/$sha" --jq .content | tr -d '\n' | base64 -d > "$dest"
}

if [ ! -f "$TC/d8.jar" ]; then
  log "Downloading d8 (Android build-tools mirror)"
  fetch_github_blob "Promastergame/tinyapk-lab" "tools/windows/d8.jar" "$TC/d8.jar"
fi

if [ ! -f "$TC/apksigner.jar" ]; then
  log "Downloading apksigner (Android build-tools mirror)"
  fetch_github_blob "warren-bank/print-apk-signature" "libs/apksigner/apksigner.jar" "$TC/apksigner.jar"
fi

# --- android.jar (API 30 = Android 11) --------------------------------------
if [ ! -f "$TC/android-30/android.jar" ]; then
  log "Downloading android.jar for API 30 (Android 11)"
  mkdir -p "$TC/tmp"
  curl -sL -o "$TC/tmp/platforms.tar.gz" \
    "https://codeload.github.com/Sable/android-platforms/tar.gz/refs/heads/master"
  tar xzf "$TC/tmp/platforms.tar.gz" -C "$TC/tmp" android-platforms-master/android-30/android.jar
  mkdir -p "$TC/android-30"
  mv "$TC/tmp/android-platforms-master/android-30/android.jar" "$TC/android-30/android.jar"
  rm -rf "$TC/tmp"
fi

log "Tool-chain ready in $TC"
"$TC/aapt2" version
java -version 2>&1 | head -1 || true
