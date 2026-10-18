# Testing

This document states exactly what was verified, how, and what was **not** verified.
Nothing in this repository was tested on physical Qin F21 Pro hardware – no device and
no emulator was available in the build environment (no Android SDK, no system images,
no KVM, and `dl.google.com` / `maven.google.com` are blocked).

---

## 1. Automated self test (executed)

```bash
tools/selftest.sh
```

Compiles the three pure Kotlin modules (`core/common`, `core/conversion`, `core/calc`)
plus `tools/selftest/Selftest.kt` with `kotlinc` and runs them on a plain JVM.

**Result: 183 checks, 0 failures.**

| Area | Covered |
|------|---------|
| JSON | parsing, nested objects, arrays, defaults, writer round trip |
| Numbers | parsing, locale formatting, invalid input |
| Conversion | length, mass, area, volume, speed, time, temperature, digital storage; every one of the 18 categories is checked for a finite, non-degenerate conversion |
| Currency | rate lookup, conversion, overrides, currency list |
| Expression | precedence, parentheses, powers, functions (`sin`, `sqrt`, `ln`, `min`, `max`, `round`, `mod`, `deg`), constants, `ANS`, implicit multiplication, factorial, error handling |
| Calculators | percentage, price breakdown, bill splitting, BMI, loan, interest, GCD/LCM/primes, Roman numerals, number bases, random numbers, date differences, geometry, travel, cooking |
| Local store | JSON round trip of a history entry and of a favourite (the import/export format) |

Two real bugs were found and fixed by these tests:

* the expression engine stripped **every** comma, so `mod(7,3)` lost its second argument
  (now only thousand separators are removed);
* the NUL character in the tokenizer was written as a raw byte instead of an escape
  sequence.

## 2. Build verification (executed)

`tools/build-offline.sh` runs end to end and produces a signed APK:

```
APK (signed)          :     1.10 MB
uncompressed payload  :     3.17 MB
classes.dex           :     3.03 MB
```

The artefact was checked with `aapt2 dump badging`:

* package `com.qin.f21.toolbox`, `minSdkVersion 30`, `targetSdkVersion 30`;
* permissions: `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
  `VIBRATE`, `POST_NOTIFICATIONS` – **no `INTERNET`**;
* every `<uses-feature>` is declared `not-required`, so the app also installs on
  hardware without a camera, GPS or IR emitter;
* the launcher activity and the camera activity are registered.

The signature was verified with `apksigner verify --print-certs` (v1 + v2 + v3).

## 3. Static review performed

* the complete Kotlin source set compiles without errors (kotlinc, `-jvm-target 1.8`);
* the vendored ZXing core compiles against `android.jar` with `javac -source 8 -target 8`;
* every `R.string.*` / `R.drawable.*` / `R.color.*` / `R.style.*` reference used in code
  and in the layouts was cross-checked against the resource files;
* the layout metrics were checked against the 480 × 640 px / 4:3 screen of the device
  (320 × 427 dp at the 1.5 density bucket) and the 44 dp minimum touch target.

## 4. Not verified (honest gaps)

| Area | Status | Why |
|------|--------|-----|
| Android UI rendering and interaction | **not executed** | no emulator / no device available |
| Camera2 preview, QR decoding, photo capture | **not executed** | needs a real camera |
| Torch, IR emitter, GNSS, microphone | **not executed** | need the real hardware |
| D-pad and T9 keypad navigation | **implemented, not exercised** | needs a device |
| Gradle build | **not executed** | no Gradle distribution, no access to the Google/Maven repositories |
| Installed size on a device | **estimated** | the APK size is measured; the installed size is derived from it (see `README.md`) |

The most likely sources of runtime problems are therefore in the Android specific code
paths (Camera2, `AudioRecord`, `ConsumerIrManager`, `LocationManager` callbacks) and in
the layout measurements of the smallest screens. They are written against the API 30
contracts, but they have not been run.

### How to verify on a device

```bash
adb install -r build/outputs/qin-f21-toolbox-1.0.0.apk
adb shell am start -n com.qin.f21.toolbox/.MainActivity
adb logcat | grep -i toolbox
```

Checklist: every screen shows a focus ring on its first control as soon as it opens, the
home screen scrolls with the D-pad, digits open the first nine tools, the
converter accepts hardware keypad digits, the timer and the stopwatch survive the screen
being turned off, the scanner decodes a printed QR code, the sound meter reacts to a
sound, GPS locks onto a position and the infrared screen either shows the code list or
the "no emitter" state.
