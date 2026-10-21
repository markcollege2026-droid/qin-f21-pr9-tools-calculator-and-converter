# Tool inventory — what the app actually contains

A complete, code-derived list of every tool in Qin F21 Toolbox **1.1.0** (versionCode 2),
written as a starting specification for a ground-up rebuild. Everything here was read out
of `app/src/main/kotlin/com/qin/f21/toolbox/Tools.kt` (the registry, the single source of
truth for the home screen, search, favourites and history) and the modules it points at.

**21 tools in 5 categories.** 8 of them need hardware, 13 are pure software.

| # | Tool | Id | Category | Needs | In one line |
|---|------|----|----------|-------|-------------|
| 1 | Unit converter | `converter` | Convert | — | 18 categories, 220 units |
| 2 | Currency | `currency` | Convert | — | 76 currencies from bundled rates |
| 3 | Time zones | `timezone` | Convert | — | 83 curated zones, offset difference |
| 4 | Date & time | `datecalc` | Convert | — | Date difference, add/subtract interval |
| 5 | Calculator | `calculator` | Calculate | — | Scientific expression calculator |
| 6 | Quick calculators | `quickcalc` | Calculate | — | 15 one-off everyday calculations |
| 7 | Geometry | `geometry` | Calculate | — | 7 shape formulas |
| 8 | Stopwatch | `stopwatch` | Measure | — | Elapsed time with laps |
| 9 | Timer | `timer` | Measure | — | Countdown with alert |
| 10 | Sound meter | `soundmeter` | Measure | microphone | Mic level in dB / dBFS |
| 11 | QR & barcode | `scanner` | Media | camera | Scan codes, decode on device |
| 12 | Camera | `camera` | Media | camera | Take a photo, keep it locally |
| 13 | Voice recorder | `recorder` | Media | microphone | WAV clips on the device |
| 14 | Infrared remote | `irremote` | Media | infrared | 26 bundled NEC codes + custom hex |
| 15 | Flashlight | `flashlight` | Media | torch | Camera flash as a torch |
| 16 | GPS | `gps` | Device | gps | Position, altitude, speed, satellites |
| 17 | Diagnostics | `diagnostics` | Device | — | 7 device/hardware report sections |
| 18 | History | `history` | Device | — | Every stored result, export CSV/JSON |
| 19 | Favourites | `favorites` | Device | — | Pinned tools and unit pairs |
| 20 | Settings | `settings` | Device | — | Rates, calibration, import, clearing |
| 21 | About & help | `about` | Device | — | Version, offline guarantees, shortcuts |

---

## Convert

### 1. Unit converter (`converter`)
18 categories, **220 units** in total. Each conversion is defined by a transform
(`linear`, `linear + offset` for temperatures, `reciprocal` for fuel economy), not by
per-pair code, so every unit in a category converts to every other one.

| Category | Units | Category | Units |
|---|---|---|---|
| Length | 23 | Pressure | 14 |
| Mass | 16 | Energy | 16 |
| Area | 15 | Power | 10 |
| Volume | 32 | Frequency | 7 |
| Speed | 10 | Angle | 9 |
| Time | 13 | Density | 8 |
| Temperature | 6 | Flow rate | 8 |
| Digital storage | 17 | Concentration | 5 |
| Fuel economy | 5 | Torque | 6 |

Features: from/to pickers with swap, live result card, "in all N units" reference table,
on-screen keypad toggle, unit-pair favourites, results written to history.
Data: `core/conversion/.../Units.kt` (catalogue), `ConversionEngine.kt` (maths).

### 2. Currency (`currency`)
**76 currencies** against a USD base, from a bundled rate table (dated 2025-01-15), used
fully offline. Shows the conversion plus the rate in both directions
(`1 EUR = 1.087 USD · 1 USD = 0.92 EUR`). Rates can be overridden one at a time in
Settings and reset to the bundle. Results go to history.
Data: `app/src/main/assets/data/currency.json`.
Currencies: USD EUR GBP JPY CNY HKD TWD KRW INR IDR MYR SGD THB VND PHP AUD NZD CAD MXN
BRL ARS CLP COP PEN UYU CHF SEK NOK DKK ISK PLN CZK HUF RON BGN HRK RSD UAH BYN MDL RUB
TRY ILS AED SAR QAR KWD BHD OMR JOD EGP MAD TND DZD NGN KES ETB TZS UGX ZAR NAD GHS XOF XAF
PKR BDT LKR NPR AFN IRR IQD KZT UZS AZN GEL AMD.

### 3. Time zones (`timezone`)
Device zone on top, **83 curated zones**, from/to pair, current time in both, UTC offsets
and the signed difference ("Difference +2 h 30 min"). Uses the platform `java.time`
database — no bundled tz file.

### 4. Date & time (`datecalc`)
Two functions on one screen: the **difference** between two dates (years/months/days plus
total days and hours), and **adding or subtracting an interval** from a date. "Today"
shortcut on every field.

---

## Calculate

### 5. Calculator (`calculator`)
Expression calculator with its own parser (`core/calc`): `+ − × ÷ %`, parentheses, sign
flip, `sin cos tan log ln √ π`, unary minus, operator precedence. Persistent result card,
calculation history, and the numeric keypad types straight into the expression.

### 6. Quick calculators (`quickcalc`) — 15 entries
| Entry | Result shown |
|---|---|
| Percentage of a number | X % of Y, and the reverse |
| Percentage change | Growth/decrease between two values |
| Discount, tax and tip | Total, amount saved, price after tax |
| Split a bill | Per person, remainder |
| Body mass index | BMI + category + healthy weight range |
| Loan payment | Monthly instalment, total paid, total interest |
| Interest | Simple vs compound |
| GCD, LCM and primes | GCD, LCM, factor list, prime check |
| Roman numerals | Integer ↔ Roman |
| Number base | Binary, octal, decimal, hex |
| Random numbers | Values in a range |
| Fuel cost | Trip cost and cost per km |
| Speed and pace | Average km/h and min/km |
| Cooking time | Scaled time and fan-oven equivalent |
| Age | Years/months/days and total days |

### 7. Geometry (`geometry`) — 7 shapes
Circle (area, circumference) · Rectangle (area, perimeter) · Triangle (area from base and
height) · Right triangle (hypotenuse) · Trapezoid (area) · Sphere (volume, surface) ·
Cylinder (volume).

---

## Measure

### 8. Stopwatch (`stopwatch`)
Start / pause / reset, elapsed time at 1/100 s, lap list with split times, state label
(Ready / Running / Paused).

### 9. Timer (`timer`)
Countdown with presets (1, 3, 5, 10, 25 min) and free entry. State label, remaining time,
pause/resume. On finish: notification + vibration, via a notification channel created at
startup; asks for `POST_NOTIFICATIONS` on first use and falls back to vibration alone if
the user declines.

### 10. Sound meter (`soundmeter`) — needs microphone
Live level in dB (estimated) with the exact dBFS, RMS and peak percentages, plus a
spectrum view. The dB estimate uses a calibration offset (default 94 dB reference) that is
editable in Settings. States: idle, measuring, permission needed, microphone missing.

---

## Media

### 11. QR & barcode (`scanner`) — needs camera
Camera preview with in-app decoding (vendored ZXing core), format label, the last decoded
code, and copy to clipboard. Each scan is stored in history; nothing leaves the device.

### 12. Camera (`camera`) — needs camera
Preview, shutter, photo saved into the app folder through the file provider, photo info
(dimensions, size), torch toggle while previewing. No gallery, no upload.

### 13. Voice recorder (`recorder`) — needs microphone
Records to WAV, lists recordings with duration and size, share/export through the file
provider. (No per-file delete — the files live in the app folder.)

### 14. Infrared remote (`irremote`) — needs an IR emitter
**4 bundled device lists, 26 codes**, all NEC protocol at 38 kHz:

| Device | Codes |
|---|---|
| Generic TV | 10 — Power, Power toggle, Volume ±, Mute, Channel ±, Input, Menu, Home |
| Generic set-top box | 7 — Power, Guide, Menu, Back, OK, Volume ± |
| Generic projector | 4 — Power, Mute, Input, Menu |
| Generic air conditioner | 5 — Power, Temperature ±, Fan speed, Mode |

Plus a **custom code field** (hex, e.g. `0x20DF10EF` or `20 DF 10 EF`) for codes that are
not in the bundled list.
If no emitter is detected the code list stays visible as a reference.

### 15. Flashlight (`flashlight`) — needs torch
Toggles the camera flash through `CameraManager.setTorchMode` (API 23+), with on/off state
and a clear message when the device has no usable flash.

---

## Device

### 16. GPS (`gps`) — needs location
Latitude/longitude, altitude, speed (km/h), bearing, accuracy (±m), fix time, satellites
used / in view, and provider state. Copy coordinates. Handles the three states: permission
needed, provider disabled, waiting for a fix.

### 17. Diagnostics (`diagnostics`)
Seven sections, each with an availability state (Available / Not present / Permission
needed / Disabled): **Device, Display, Memory, Storage, Battery, Sensors, Network**.
Sensors covered: accelerometer, gyroscope, magnetometer, light, proximity, barometer, step
counter, heart rate, plus "other".

### 18. History (`history`)
Every result the app produces, newest first, with tool, title, summary and timestamp.
Copy an entry, export the lot as **CSV or JSON** through the file provider, clear all.

### 19. Favourites (`favorites`)
Pinned tools and pinned converter unit pairs, shown on the home screen as a strip.

### 20. Settings (`settings`)
Keep the screen on · microphone calibration offset (dB) · exchange-rate overrides and reset
to bundled rates · import a backup (JSON) · clear history · clear favourites · version and
the offline note.

### 21. About & help (`about`)
Version, "Minimum Android 11 (API 30)", offline guarantee text, detected hardware list,
keypad shortcuts (D-pad moves focus, centre activates, Enter confirms, number keys type,
Star flips sign, Hash clears, Back returns), third-party licences (ZXing, Apache 2.0).

---

## App-wide behaviour (not a tool, but part of the product)

* **Home** — category sections with counts, quick-access tiles, favourites strip, recently
  used strip, and a **search** over titles, descriptions and ids.
* **Screen stack** — one activity, a manual `Screen` stack (`ui/Screen.kt`), shared app bar
  with title, favourite star and back.
* **Hardware gating** — 14 capabilities are probed at startup
  (`core/hardware/Capabilities.kt`: accelerometer, barometer, bluetooth, camera, compass,
  gps, gyroscope, ir, light, microphone, proximity, torch, vibrator, wifi); a tool whose
  hardware is missing is shown as "Not available on this device" rather than failing.
* **Keypad-first design** — every screen is fully operable from the Qin's physical keypad.
* **Storage** — everything local (`core/data`: `ToolboxStore` + JSON), export/import through
  `ToolboxFileProvider`.
* **Offline** — no `INTERNET` permission at all.

### Permissions declared
`CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `VIBRATE`,
`TRANSMIT_IR`, `POST_NOTIFICATIONS`. All hardware `uses-feature` entries are
`required="false"`, so the app installs on any device.

### Bundled data (the only "content" in the app)
| File | Size | Content |
|---|---|---|
| `assets/data/currency.json` | 1.5 KB | 76 rates, base USD, dated 2025-01-15 |
| `assets/data/ir_codes.json` | 2.0 KB | 4 devices, 26 NEC codes |
| `Units.kt` (code, not asset) | — | 18 categories, 220 units |
| `ConversionEngine.kt` | — | 83 curated time zones |

---

## Code map, for a rewrite

| Module | Lines | Responsibility | Portable as-is? |
|---|---|---|---|
| `core/common` | JSON, number formatting | pure Kotlin | ✅ yes |
| `core/conversion` | units, engine, time zones | pure Kotlin | ✅ yes |
| `core/calc` | expression parser, calculators | pure Kotlin | ✅ yes |
| `core/data` | store, settings, file provider | Android | ⚠️ mostly reusable |
| `core/hardware` | capability probe, torch, IR, GNSS, mic | Android | ⚠️ reusable, but thin |
| `core/design` | colours, dimens, styles, drawables | resources | ✅ tokens yes, rest is taste |
| `app/ui` | 25 files, 21 screens | Android Views | ❌ this is what you'd rebuild |
| `third_party/zxing` | decoding | vendored Apache 2.0 | ✅ yes |

The pure modules (`common`, `conversion`, `calc`) plus `tools/selftest` are the part with
real test coverage — **183 checks**, no device needed. That is the cheapest thing to keep.

## Known gaps worth knowing before you restart

* The **UI has never been executed** — no device or emulator was available while it was
  built. Layouts are hand-checked against the 320 × 427 dp budget, not measured.
* **No AndroidX, no Material** — plain framework `Theme.Material.NoActionBar`, hand-rolled
  app bar, cards and keypad. Deliberate (bundle size, offline), but it means all component
  behaviour is custom code you would inherit.
* **Dark theme only**, one language (English), no `values-night`, no translations.
* **Static data**: currency rates are from January 2025 and the IR list is 4 generic NEC
  remotes — not a real code database.
* Navigation is a hand-written `Screen` stack in a single activity; no back-stack library,
  no deep links, no state restoration beyond what the stack holds.
* `History` is append-only from the tools' point of view; there is no per-entry delete.
