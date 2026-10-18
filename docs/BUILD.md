# Building the toolbox

The project can be built in two ways. Both produce the same application; the offline
pipeline exists because the build environment used for this work had no Android SDK and
no access to `maven.google.com`.

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
| 4 | `kotlinc` (2.4.21, `-jvm-target 1.8`) | all Kotlin sources → `build/classes` |
| 5 | `d8` (`--min-api 30 --release`) | `build/dex/classes.dex` |
| 6 | `tools/package_apk.py` | `build/apk/unsigned.apk` (base.apk + dex + assets) |
| 7 | `keytool` + `apksigner` | signed `build/outputs/qin-f21-toolbox-1.0.0.apk` (v1+v2+v3) |

At the end the script prints the measured size report.

### Tool-chain contents (`tools/toolchain`, gitignored)

| File | Version | Origin |
|------|---------|--------|
| `jdk/` | AdoptOpenJDK 11.0.10 (`javac`, `keytool`, `jar`) | npm package `jdk11@2.0.0` |
| `kotlinc/` | Kotlin compiler 2.4.21 + stdlib | npm package `kotlin-compiler` |
| `aapt2` | 2.19 (build 7832930) | PyPI package `aapt2` |
| `d8.jar` | 8.2.2-dev | GitHub `Promastergame/tinyapk-lab` |
| `apksigner.jar` | 0.9 | GitHub `warren-bank/print-apk-signature` |
| `android-30/android.jar` | API 30 platform stub | GitHub `Sable/android-platforms` |

`tools/fetch-toolchain.sh` repeats those downloads; the archives are cached in `/tmp`
when they are already present.

### Notes and gotchas

* `aapt2 compile --dir` writes a **zip**; `aapt2 link` expects the `.flat` files, so the
  script unpacks the archives before linking.
* `d8` refuses directories – the script jars the class folders first.
* `javac` needs `-encoding UTF-8` because the ZXing sources contain non-ASCII characters.
* `apksigner` needs an existing `build/outputs` directory.
* `zipalign` is not part of the pipeline (the binary that could be downloaded needs a
  `libc++.so` that is not available). The APK is still valid and installable; the
  alignment is only an optimisation.

### Debug key

The script creates `build/debug.keystore` on first use
(`CN=Qin F21 Toolbox Debug, O=Offline, C=IL`, password `android`). Replace it with your
own key before distributing the app.

---

## 2. Gradle build (needs a local Android SDK with platform 30)

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # minified release APK
./gradlew :app:installDebug       # install on a connected device
```

* AGP 7.4.2, Kotlin 1.8.22, `compileSdk = 30`, `minSdk = 30`, `targetSdk = 30`.
* `android.useAndroidX=false` – the app uses the plain platform framework, there is no
  AndroidX or Material dependency.
* The ZXing core is added as a source directory (`third_party/zxing-core`), so Gradle
  needs no extra repository.
* The release build enables R8 and resource shrinking; the rules in
  `app/proguard-rules.pro` keep the line numbers for readable stack traces.

The Gradle build was **not executed** in this environment (no Gradle distribution and no
access to `services.gradle.org` / `maven.google.com`), so it is provided for normal
development environments and is unverified here. The verified path is the offline
pipeline above.

---

## Building the icon set

The 61 vector icons in `core/design/src/main/res/drawable` are generated:

```bash
python3 tools/generate_icons.py core/design/src/main/res/drawable
```

The script needs Pillow for the launcher glyph only; the vector paths themselves are
plain text.
