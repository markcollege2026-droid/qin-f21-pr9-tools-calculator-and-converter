# Hardware support on the Qin F21 Pro

The toolbox never assumes that a piece of hardware exists. Every tool asks the runtime
capability registry (`core/hardware/Capabilities.kt`) first and shows an explicit
"not available on this device" state when the hardware is missing.

## Target profile

| Property | Value |
|----------|-------|
| Display | 2.8" IPS, 480 × 640 px, 4:3, capacitive (≈ 320 × 427 dp) |
| Android | 11 (API 30) |
| SoC | MediaTek Helio A22 (MT6761), quad Cortex-A53 @ 2.0 GHz, PowerVR GE8320 |
| Memory | 3 / 4 GB RAM, 32 / 64 GB storage, no microSD slot |
| Camera | 5 MP rear with AF + LED flash, 2 MP front |
| Sensors | light + proximity only (no accelerometer, no gyroscope documented) |
| Audio | two analogue microphones, no 3.5 mm jack, vibration motor |
| Connectivity | Wi-Fi, Bluetooth 5.0, GPS / A-GPS / BDS, 4G LTE, nano SIM |
| Keys | 21-key T9 keypad with D-pad and backlight |
| Battery | 2120 mAh |
| IR blaster | **unclear** – listed for some variants, absent on others → detected at runtime |

## Capability matrix

| Tool | Requirement | Behaviour when the hardware is missing |
|------|-------------|----------------------------------------|
| Unit converter, currency, time zones, date, calculators, geometry, stopwatch, timer, history, favourites, settings, about | software only | always available |
| Sound meter, voice recorder | `AudioRecord` + `RECORD_AUDIO` | the microphone capability is checked first; if the permission is missing the tool shows a card with a "Grant permission" button, and granting it rebuilds the screen |
| QR/barcode scanner, camera | `CameraManager` + `CAMERA` | the tool shows "camera not available"; every camera and flash is declared `not-required` in the manifest so the app still installs |
| Flashlight | camera with a flash unit (`FLASH_INFO_AVAILABLE`) | the button is disabled and the reason is shown |
| Infrared remote | `ConsumerIrManager.hasIrEmitter()` | the code list stays readable as a reference, the "send" buttons are replaced by an explanation |
| GPS | `LocationManager` GPS/network provider + location permission | the screen explains that a provider or the permission is missing |
| Accelerometer, gyroscope, magnetometer, barometer, step counter | – | **not implemented at all**: the sensors are not documented for this device, so no level, compass or pedometer tool exists and no accelerometer based value is ever faked |

## Detection details

* **Cameras** – `CameraManager.getCameraIdList()` with the lens facing and the flash
  availability per camera id; the flashlight is only offered when a camera reports
  `FLASH_INFO_AVAILABLE`.
* **Torch** – `CameraManager.setTorchMode()`; the controller keeps its own state
  because API 30 has no `getTorchMode()`.
* **Infrared** – `ConsumerIrManager.hasIrEmitter()`; the carrier range is read from
  `getCarrierFrequencies()`.
* **Location** – `LocationManager` with the GPS and the network provider, plus
  `GnssStatus.Callback` for the satellite count (in view / used in fix).
* **Microphone** – `AudioRecord.getMinBufferSize()` plus the runtime permission; the
  recorder writes 44.1 kHz mono PCM16 WAV files.
* **Sensors** – `SensorManager.getSensorList()`; the diagnostics screen lists every
  sensor the device reports with its vendor, power consumption and minimum delay.
* **Display** – `DisplayMetrics` for the resolution, the density and the refresh rate.
* **Memory and storage** – `ActivityManager.MemoryInfo`, `Runtime` for the heap and
  `StatFs` for the file system.
* **Battery** – the sticky `ACTION_BATTERY_CHANGED` intent and
  `BatteryManager.getIntProperty()`.

## Sound level honesty

Phone microphones are not calibrated and the automatic gain control makes an absolute
sound pressure level impossible to derive. The sound meter therefore shows:

* **dBFS** – the exact digital level of the signal (0 dBFS = full scale),
* **RMS** and **peak** in percent,
* an **estimated dB value** = dBFS + a calibration offset (default 94 dB) that the user
  can change in Settings.

The spectrum shows seven Goertzel bands (60 Hz … 10 kHz).

## Keypad behaviour

| Key | Action |
|-----|--------|
| D-pad | moves the focus, every focusable row shows the focus ring of the theme |
| Centre / Enter | activates the focused control |
| 0 – 9 | types into the value field of the converter, the currency screen and the calculator; on the home screen the digits 1 – 9 open the first nine tools |
| `*` | toggles the sign of the value |
| `#` | clears the value |
| Back | returns to the previous screen |

Every function can be reached without a touch screen: the tool list, the unit pickers,
the keypad, the lap list and the settings are all focusable and the numeric fields
accept the hardware keypad.

**Focus.** A pushed screen asks its content for the focus in the DOWN direction once it
has been laid out, unless something already holds it, so the arrow keys always have a
starting point. Scroll containers are focusable with
`descendantFocusability = afterDescendants`: their children win the focus, and the
container only becomes a stop on read-only screens such as Diagnostics and About, where
it then scrolls with the arrow keys. The header sits outside the content area, so the
first focus always lands on a control of the tool itself and Enter on a freshly opened
screen can never hit the back arrow.

## Limits that are not hardware dependent

* The countdown keeps running while the app is alive, including when the screen is off or
  another tool is open, and fires a vibration plus a notification. It does **not** survive
  the process being killed by the system: the toolbox deliberately has no foreground
  service, so there is no permanent notification and no second process. Treat a timer as
  reliable while the app is running, which is the normal case on a phone that stays awake.
