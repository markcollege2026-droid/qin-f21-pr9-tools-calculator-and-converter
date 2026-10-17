# Architecture

```
┌─────────────────────────────── app ───────────────────────────────┐
│ MainActivity (single activity)                                    │
│   └── screen stack: HomeScreen → ConverterScreen → …               │
│         Screen base class + Widgets helpers + KeypadView           │
│         Tools registry (21 tools, capability gating)               │
│         CameraActivity (Camera2 preview, scan + photo)             │
└────────────────────────────────────────────────────────────────────┘
             │                    │                    │
      core/common          core/conversion        core/calc
      JSON, numbers         units + engine         expression, helpers
             │                    │                    │
             └──────────── core/data ────────────────┘
              store, settings, file provider
                        │
                   core/hardware
     capabilities, torch, IR, GNSS, mic, diagnostics
                        │
                   core/design
            colours, type scale, drawables, icons
```

## Decisions

**One activity, a stack of screens.** Every tool is a `Screen` that creates a plain
Android view tree. There is no fragment or navigation library, which keeps the startup
fast, the APK small and the key routing simple: the activity forwards `onKeyDown` to the
visible screen, and the screen decides what a key means.

**Framework views only.** No AndroidX, no Material Components, no Compose – the app runs
on the plain `Theme.Material` of the platform with a custom drawable based design system
in `core/design`.

**Pure logic in pure modules.** `core/common`, `core/conversion` and `core/calc` do not
import anything from `android.*`, so they compile and run on a plain JVM. That is what
`tools/selftest.sh` verifies.

**Own JSON implementation.** The `org.json` classes of the platform are not available on
a plain JVM, so `core/common/Json.kt` implements a small, complete JSON model, parser
and writer. The same file also powers the import/export format of the history.

**Own expression engine.** `core/calc/Expression.kt` is a recursive descent parser
(`+ - * / % ^ !`, functions, constants, `ANS`, implicit multiplication) that produces
friendly error messages instead of stack traces.

**Runtime capability detection.** `core/hardware/Capabilities.kt` collects everything
the app needs to know about the device in one place: cameras, flash, IR emitter,
location providers, microphone, sensors, network. The tool registry uses it to decide
whether a tool is usable, and the screens use it to show a designed "unavailable" state
instead of failing.

**Bundled reference data.** The exchange rates (`app/src/main/assets/data/currency.json`)
and the infrared codes (`app/src/main/assets/data/ir_codes.json`) are plain JSON in the
APK. The rates are indicative, dated and user-overridable; the app never claims them to
be live.

**Vendored ZXing core.** Writing a QR and 1D barcode decoder from scratch would have
been a large and error-prone piece of code, so the Apache-2.0 licensed ZXing *core* is
vendored in `third_party/zxing-core` and compiled from source. It keeps the build
offline and the licence simple, and it adds roughly 1 MB to the compressed APK.

**No `INTERNET` permission.** This is the strongest possible offline guarantee: the
process cannot open a socket.

## Data model

* **History** – `history.json` in the app internal storage, at most 300 entries, written
  atomically (temp file + rename). One entry per conversion, not one per key press.
* **Favourites** – `favorites.json`, the same mechanism. A favourite is a tool id plus
  a payload (for example a converter pair).
* **Settings** – `SharedPreferences` (`qin_f21_toolbox_settings`): keep screen on,
  microphone calibration, currency overrides, the last used unit per category, the
  recently used tools.
* **Export / import** – a JSON document with the `history` and `favorites` arrays, or
  the same history as CSV. The files are shared through
  `com.qin.f21.toolbox.core.data.ToolboxFileProvider`, a minimal `ContentProvider`
  that replaces the AndroidX `FileProvider`.

## Fitting the 2.8" screen

The viewport of the Qin F21 Pro is 480 x 640 px, which is 320 x 427 dp at the 1.5
density bucket. The vertical budget of the busiest screen (the unit converter) is:

| Element | Height |
|---------|--------|
| Header (back, title, subtitle) | 44 dp |
| Category picker + favourite button | 44 dp |
| Value row (compact input + swap) | 36 dp |
| Unit pickers (from → to) | 40 dp |
| Result card | 52 dp |
| "All units" caption | 16 dp |
| Numeric keypad (4 rows) | 120 dp |
| **Remaining for the unit list** | **~70 dp (2 rows, scrollable)** |

The calculator uses the free space for a five row keypad (~55 dp per key), the timer and
the sound meter use a single scrolling column, and the list screens use 44 dp rows so
that every entry is a comfortable D-pad stop. Nothing relies on a gesture that a D-pad
cannot express, and no horizontal scrolling is required for the primary flow of a tool.

## Rendering and performance

* One screen is inflated at a time; the previous one is removed from the view tree, so
  the memory footprint stays small.
* The stopwatch and the timer refresh on the main thread with a 33 ms / 100 ms ticker
  and stop as soon as the screen is hidden.
* The sound meter and the recorder run their capture loop on a background thread and
  post the level to the UI.
* The scanner decodes the camera frames on a background handler thread.
* All icons are vector drawables; the launcher icon is a single adaptive vector.
