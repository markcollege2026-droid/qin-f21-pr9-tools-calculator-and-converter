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
* The Kotlin flag is `-jvm-default=enable`: the old `-Xjvm-default=all` mode was removed
  in Kotlin 2.2, and the tool-chain is pinned to 2.4.21. The code base contains no
  `@JvmDefault` annotation, so `enable` produces the same bytecode as the old `all`.
* The JDK from the `jdk11` npm package is a full JDK (it ships `javac`, `jar` and
  `keytool`); a bare JRE is not enough for this pipeline.
* `apksigner` needs an existing `build/outputs` directory.
* `zipalign` is not part of the pipeline (the binary that could be downloaded needs a
  `libc++.so` that is not available). The APK is still valid and installable; the
  alignment is only an optimisation.

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
  1. JDK 17 (Temurin) and the Android SDK (platform 30 + build-tools 33.0.1) via
     `setup-java` / `setup-android`;
  2. `./gradlew :app:assembleDebug :app:assembleRelease` (the wrapper and the Gradle
     dependency cache are maintained by `gradle/actions/setup-gradle`);
  3. the debug and release APKs are uploaded as build artefacts.

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

* AGP 7.4.2, Kotlin 1.8.22, Gradle 7.6.2 (wrapper committed in `gradle/wrapper`),
  `compileSdk = 30`, `minSdk = 30`, `targetSdk = 30`, built on JDK 17.
* `android.useAndroidX=false` – the app uses the plain platform framework, there is no
  AndroidX or Material dependency.
* The ZXing core is added as a source directory (`third_party/zxing-core`), so Gradle
  needs no extra repository.
* The release build enables R8 and resource shrinking; the rules in
  `app/proguard-rules.pro` keep the line numbers for readable stack traces.
* `assembleRelease` produces an **unsigned** release APK (no release signing config is
  configured on purpose); sign it with your own key before distributing.

The Gradle build runs in CI (see above) with the Android SDK; it also works in any
standard development environment with a local SDK (platform 30) and JDK 17.

---

## Building the icon set

The 61 vector icons in `core/design/src/main/res/drawable` are generated:

```bash
python3 tools/generate_icons.py core/design/src/main/res/drawable
```

The script needs Pillow for the launcher glyph only; the vector paths themselves are
plain text.
