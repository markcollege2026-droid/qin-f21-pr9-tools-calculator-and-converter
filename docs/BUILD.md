# Building the toolbox

The project can be built in two ways. Both produce the same application and both are
verified in CI (see "Continuous integration" below). The offline pipeline exists because
the build environment used for this work had no Android SDK and no access to
`maven.google.com`.

---

## 1. Offline pipeline (recommended for a sandbox without an SDK)

```bash
tools/fetch-toolchain.sh     # downloads the tool-chain into tools/toolchain (gitignored)
tools/build-offline.sh       # builds and signs the APK
```

### What the script does

| Step | Tool | Result |
|------|------|--------|
| 1 | `aapt2 compile --dir` | flat resource files per module (`core/*/src/main/res`, `app/src/main/res`) |
| 2 | `aapt2 link` | `build/apk/base.apk` + the generated `R.java` in `build/gen` |
| 3 | `javac` (JDK 11) | `R.java` **and** the vendored ZXing sources → `build/rclasses` |
| 4 | `kotlinc` (1.9.25, `-jvm-target 1.8`) | all Kotlin sources → `build/classes` |
| 5 | `d8` (`--min-api 30 --release`) | `build/dex/classes.dex` |
| 6 | `tools/package_apk.py` | `build/apk/unsigned.apk` (base.apk + dex + assets) |
| 7 | `keytool` + `apksigner` | signed `build/outputs/qin-f21-toolbox-1.1.0-release.apk` (APK Signature Scheme v3) |

At the end the script prints the measured size report.

### Tool-chain contents (`tools/toolchain`, gitignored)

| File | Version | Origin |
|------|---------|--------|
| `jdk/` | AdoptOpenJDK 11.0.10 (`javac`, `keytool`, `jar`) | npm package `jdk11@2.0.0` |
| `kotlinc/` | Kotlin compiler 1.9.25 + stdlib | npm package `kotlin-compiler` |
| `aapt2` | 2.20 (build 15009934) | npm package `aaptjs3` (bundles the AOSP `aapt2` binary) |
| `d8.jar` | 8.2.2-dev | GitHub `Promastergame/tinyapk-lab` |
| `apksigner.jar` | 0.9 | GitHub `warren-bank/print-apk-signature` |
| `android-36/android.jar` | API 36 platform stub (plus `android-30` for compatibility checks) | GitHub `Sable/android-platforms` |

`tools/fetch-toolchain.sh` is idempotent: every component is verified right after
download and is only skipped when a `.done-*` marker file proves that a previous run
completed it, so an interrupted or partially cached tool-chain is re-fetched instead of
being trusted. All versions are pinned (see the script header) so the build is
reproducible.

### Notes and gotchas

* `aapt2 compile --dir` writes a **zip**; `aapt2 link` expects the `.flat` files, so the
  script unpacks the archives before linking.
* `d8` refuses directories – the script jars the class folders first.
* `javac` needs `-encoding UTF-8` because the ZXing sources contain non-ASCII characters.
  A `javac` failure aborts the build (the compiler output is printed); an earlier
  revision of the script swallowed compiler errors, which turned a broken `R.java` into a
  confusing Kotlin compile failure.
* The compiler versions are pinned together on purpose: **Kotlin 1.9.25** is the newest
  compiler whose metadata the bundled R8/d8 8.2.2 understands, and it matches the Kotlin
  version Gradle uses. `-jvm-default=enable` is a Kotlin 2.x flag and must not come back.
* `kotlinc` needs an explicit heap on small machines: the build passes
  `-J-Xmx1536m -J-XX:MaxMetaspaceSize=512m -J-XX:+UseSerialGC` (override with
  `KOTLIN_HEAP=...`). Without it a 4 GB sandbox fails with `OutOfMemoryError`.
* `aapt2` must be **2.20 or newer** for `compileSdk 36`; the 2.19 build (PyPI wheel)
  aborts on modern resource tables with `RES_TABLE_TYPE_TYPE entry offsets overlap`.
* The signing key lives in `.release-keystore/release.keystore` (gitignored), *outside*
  `build/`, which is wiped on every run. Replacing it means a new signing identity, and
  an APK signed with a different key cannot be installed as an update.
* The JDK from the `jdk11` npm package is a full JDK (it ships `javac`, `jar` and
  `keytool`); a bare JRE is not enough for this pipeline.
* `apksigner` needs an existing `build/outputs` directory.
* No external `zipalign` binary is needed: `tools/package_apk.py` writes every
  uncompressed entry 4-byte aligned (padding the local header's extra field, the
  same technique zipalign uses) while assembling the APK. This is a **requirement,
  not an optimisation**: Android 11 (API 30, "R") and newer refuse to install an
  APK whose `resources.arsc` is not stored uncompressed and aligned on a 4-byte
  boundary. `tools/build-offline.sh` verifies the alignment of the final signed
  APK and fails the build if it is wrong.

### Continuous integration

`.github/workflows/build.yml` runs on every push to `main` and on every pull request,
with two jobs:

* **Offline pipeline** –
  1. `tools/fetch-toolchain.sh`: pinned, verified tool-chain (cached with
     `actions/cache` keyed on the fetch script);
  2. `tools/selftest.sh`: the 183 pure-Kotlin checks;
  3. `tools/build-offline.sh`: the signed APK;
  4. `apksigner verify` on the artefact, then the APK is uploaded as a build artefact.

* **Gradle build** –
  1. JDK 17 (Temurin) and the Android SDK (platform 36 + build-tools 35.0.0) via
     `setup-java` / `setup-android`;
  2. `./gradlew :app:lintRelease :app:assembleDebug :app:assembleRelease` (the wrapper
     and the Gradle dependency cache are maintained by `gradle/actions/setup-gradle`).
     The plain `lintRelease` task runs first because the automatic `lintVitalRelease`
     gate inside `assembleRelease` fails without writing a report or listing the issues;
     a failing lint run additionally re-runs the task with `abortOnError=false` so the
     full report is produced and the fatal findings are surfaced as annotations;
  3. the debug and release APKs are uploaded as build artefacts.

### Keys

`tools/build-offline.sh` builds and signs a **release** APK by default and creates the key
at `.release-keystore/release.keystore` (gitignored, kept outside `build/` because that
directory is wiped on every run). Reuse that file to ship updates; an APK signed with a
different key cannot be installed over the existing app.

`BUILD_TYPE=debug tools/build-offline.sh` produces a debuggable APK signed with
`build/debug.keystore` (`CN=Qin F21 Toolbox Debug`, password `android`) - for development
only, never for distribution.

---

## 2. Gradle build (needs a local Android SDK with platform 36 and build-tools 35.0.0)

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # minified release APK
./gradlew :app:installDebug       # install on a connected device
```

* AGP 8.5.2, Kotlin 1.9.25, Gradle 8.9 (wrapper committed in `gradle/wrapper`),
  `compileSdk = 36`, `minSdk = 30` (Android 11), `targetSdk = 36` (Android 16), built on
  JDK 17. `minSdk 30` is a hard floor: Android 11+ only, by design.
* `android.useAndroidX=false` – the app uses the plain platform framework, there is no
  AndroidX or Material dependency.
* The ZXing core is compiled from source (`third_party/zxing-core`) by the pure-Java
  `:third_party:zxing` library module that the app depends on – so Gradle needs no
  extra repository.
* The release build enables R8 and resource shrinking; the rules in
  `app/proguard-rules.pro` keep the line numbers for readable stack traces.
* `assembleRelease` produces an **unsigned** release APK (no release signing config is
  configured on purpose); sign it with your own key before distributing.

The Gradle build runs in CI (see above) with the Android SDK; it also works in any
standard development environment with a local SDK (platform 36, build-tools 35.0.0) and JDK 17.

---

## Building the icon set

The 61 vector icons in `core/design/src/main/res/drawable` are generated:

```bash
python3 tools/generate_icons.py core/design/src/main/res/drawable
```

The script needs Pillow for the launcher glyph only; the vector paths themselves are
plain text.
