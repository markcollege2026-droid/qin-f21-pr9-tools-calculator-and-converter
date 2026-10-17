#!/usr/bin/env python3
"""Generates the vector drawable icon set (line-art style, 24dp grid)."""
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "."

STROKE = '#FFFFFFFF'

ICONS = {
    # --- navigation / utility -------------------------------------------------
    "ic_search": ['M10,4 A6,6 0 1 0 10,16 A6,6 0 1 0 10,4', 'M14.4,14.4 L20,20'],
    "ic_star": ['M12,3.2 L14.6,9.2 L21,9.8 L16.2,14 L17.6,20.4 L12,16.9 L6.4,20.4 L7.8,14 L3,9.8 L9.4,9.2 Z'],
    "ic_history": ['M3.6,12 A8.4,8.4 0 1 0 6.2,6.2', 'M3.2,3.6 L3.2,8.4 L8,8.4', 'M12,7.4 L12,12.2 L15.6,14.2'],
    "ic_settings": ['M12,8.2 A3.8,3.8 0 1 0 12,15.8 A3.8,3.8 0 1 0 12,8.2',
                    'M12,2.6 L12,5 M12,19 L12,21.4 M2.6,12 L5,12 M19,12 L21.4,12',
                    'M5.4,5.4 L7,7 M17,17 L18.6,18.6 M18.6,5.4 L17,7 M7,17 L5.4,18.6'],
    "ic_info": ['M12,3.6 A8.4,8.4 0 1 0 12,20.4 A8.4,8.4 0 1 0 12,3.6',
                'M12,11 L12,16.4', 'M12,7.4 L12,7.6'],
    "ic_back": ['M15,4.5 L7.5,12 L15,19.5'],
    "ic_close": ['M6,6 L18,18', 'M18,6 L6,18'],
    "ic_check": ['M5,12.5 L10,17.5 L19,6.5'],
    "ic_swap": ['M6,8.5 L18,8.5 M14.5,5 L18,8.5 L14.5,12',
                'M18,15.5 L6,15.5 M9.5,12 L6,15.5 L9.5,19'],
    "ic_plus": ['M12,5 L12,19', 'M5,12 L19,12'],
    "ic_minus": ['M5,12 L19,12'],
    "ic_more": ['M6,12 L6,12.1', 'M12,12 L12,12.1', 'M18,12 L18,12.1'],
    "ic_warning": ['M12,4 L21,19.5 L3,19.5 Z', 'M12,10 L12,14.4', 'M12,16.6 L12,16.8'],
    "ic_apps": ['M4,4 L10,4 L10,10 L4,10 Z', 'M14,4 L20,4 L20,10 L14,10 Z',
                'M4,14 L10,14 L10,20 L4,20 Z', 'M14,14 L20,14 L20,20 L14,20 Z'],

    # --- tools ----------------------------------------------------------------
    "ic_convert": ['M4,9 L20,9 M16.6,5.8 L20,9 L16.6,12.2',
                   'M20,15 L4,15 M7.4,11.8 L4,15 L7.4,18.2'],
    "ic_currency": ['M12,3.6 A8.4,8.4 0 1 0 12,20.4 A8.4,8.4 0 1 0 12,3.6',
                    'M12,6.4 L12,17.6',
                    'M14.6,9.4 C13.6,8.4 9.6,8.2 9.6,10.6 C9.6,12.8 14.4,12.2 14.4,14.6 C14.4,16.8 10.8,16.6 9.6,15.4'],
    "ic_timezone": ['M12,3.6 A8.4,8.4 0 1 0 12,20.4 A8.4,8.4 0 1 0 12,3.6',
                    'M3.6,12 L20.4,12',
                    'M12,3.6 C9.2,7 9.2,17 12,20.4 C14.8,17 14.8,7 12,3.6'],
    "ic_calendar": ['M4,6 L20,6 L20,20 L4,20 Z', 'M4,10.4 L20,10.4', 'M8,3.4 L8,7.4', 'M16,3.4 L16,7.4'],
    "ic_calculator": ['M5,3 L19,3 L19,21 L5,21 Z', 'M8,6.6 L16,6.6',
                      'M8.4,12 L8.4,12.1', 'M12,12 L12,12.1', 'M15.6,12 L15.6,12.1',
                      'M8.4,16 L8.4,16.1', 'M12,16 L12,16.1', 'M15.6,16 L15.6,16.1'],
    "ic_percent": ['M6.2,18.2 L17.8,5.8',
                   'M7.6,7.6 A2.2,2.2 0 1 0 7.6,12 A2.2,2.2 0 1 0 7.6,7.6',
                   'M16.4,12 A2.2,2.2 0 1 0 16.4,16.4 A2.2,2.2 0 1 0 16.4,12'],
    "ic_geometry": ['M3.6,19 L12,4.6 L20.4,19 Z', 'M7,14.6 L17,14.6'],
    "ic_stopwatch": ['M12,6 A7.2,7.2 0 1 0 12,20.4 A7.2,7.2 0 1 0 12,6',
                     'M12,13.2 L12,9.6', 'M9.4,3.4 L14.6,3.4', 'M12,6 L12,3.4'],
    "ic_timer": ['M12,4.4 A7.8,7.8 0 1 0 12,20 A7.8,7.8 0 1 0 12,4.4',
                 'M12,12 L12,8.2', 'M9.4,2.6 L14.6,2.6'],
    "ic_flashlight": ['M8,3 L16,3 L16,6.2 L8,6.2 Z', 'M9,6.2 L15,6.2 L13.8,21 L10.2,21 Z'],
    "ic_remote": ['M6.6,3 L17.4,3 L17.4,21 L6.6,21 Z', 'M9.4,6.8 L14.6,6.8',
                  'M12,11.2 L12,11.3', 'M9.6,15 L9.6,15.1', 'M14.4,15 L14.4,15.1'],
    "ic_mic": ['M9,3.4 L15,3.4 L15,12 A3,3 0 0 1 9,12 Z',
               'M5.8,11 A6.2,6.2 0 0 0 18.2,11', 'M12,17.2 L12,20.8', 'M8.6,20.8 L15.4,20.8'],
    "ic_camera": ['M3,8.4 L8,8.4 L9.8,5.4 L14.2,5.4 L16,8.4 L21,8.4 L21,19.4 L3,19.4 Z',
                  'M12,10.8 A3.4,3.4 0 1 0 12,17.6 A3.4,3.4 0 1 0 12,10.8'],
    "ic_scan": ['M4,9 L4,4 L9,4 M15,4 L20,4 L20,9 M20,15 L20,20 L15,20 M9,20 L4,20 L4,15',
                'M8,8 L11,8 L11,11 L8,11 Z', 'M13,13 L16,13 L16,16 L13,16 Z'],
    "ic_location": ['M12,21 C12,21 5.6,15 5.6,10.2 A6.4,6.4 0 0 1 18.4,10.2 C18.4,15 12,21 12,21 Z',
                    'M12,7.8 A2.4,2.4 0 1 0 12,12.6 A2.4,2.4 0 1 0 12,7.8'],
    "ic_diagnostics": ['M6.6,6.6 L17.4,6.6 L17.4,17.4 L6.6,17.4 Z',
                       'M3.6,10 L6.6,10 M3.6,14 L6.6,14 M17.4,10 L20.4,10 M17.4,14 L20.4,14',
                       'M9.8,3.6 L9.8,6.6 M14.2,3.6 L14.2,6.6 M9.8,17.4 L9.8,20.4 M14.2,17.4 L14.2,20.4'],
    "ic_export": ['M12,15.6 L12,3.6 M8,7.4 L12,3.6 L16,7.4',
                  'M4,14.6 L4,20.4 L20,20.4 L20,14.6'],
    "ic_import": ['M12,3.6 L12,15.6 M8,11.8 L12,15.6 L16,11.8',
                  'M4,14.6 L4,20.4 L20,20.4 L20,14.6'],
    "ic_file": ['M6,3 L14,3 L18,7 L18,21 L6,21 Z', 'M14,3 L14,7 L18,7'],
    "ic_trash": ['M5,7 L19,7', 'M9,7 L9,4.6 L15,4.6 L15,7',
                 'M6.6,7 L7.6,20.4 L16.4,20.4 L17.4,7'],
    "ic_download": ['M12,3.6 L12,14.4 M7.6,10 L12,14.4 L16.4,10', 'M4,18 L4,20.4 L20,20.4 L20,18'],
    "ic_gauge": ['M3.4,17.4 A9,9 0 0 1 20.6,17.4', 'M12,17.4 L16.4,10.4'],
    "ic_clock": ['M12,3.6 A8.4,8.4 0 1 0 12,20.4 A8.4,8.4 0 1 0 12,3.6',
                 'M12,7 L12,12.2 L15.8,14.2'],
    "ic_battery": ['M2.6,7.6 L17.4,7.6 L17.4,16.4 L2.6,16.4 Z', 'M19.4,10.4 L21.4,10.4 L21.4,13.6 L19.4,13.6'],
    "ic_network": ['M12,3.4 A8.6,8.6 0 1 0 12,20.6 A8.6,8.6 0 1 0 12,3.4',
                   'M3.4,12 L20.6,12', 'M12,3.4 A8.6,8.6 0 0 1 12,20.6'],
    "ic_memory": ['M3.4,6.6 L20.6,6.6 L20.6,17.4 L3.4,17.4 Z',
                  'M7,10 L10,10 L10,14 L7,14 Z', 'M14,10 L17,10 L17,14 L14,14 Z'],
    "ic_storage": ['M3.4,5 L20.6,5 L20.6,19 L3.4,19 Z', 'M3.4,9.4 L20.6,9.4',
                   'M6.4,14.4 L8.4,14.4 L8.4,16.4 L6.4,16.4 Z'],

    # --- unit categories ------------------------------------------------------
    "ic_ruler": ['M2.6,8.4 L21.4,8.4 L21.4,15.6 L2.6,15.6 Z',
                 'M6.6,8.4 L6.6,12 M10.6,8.4 L10.6,10.8 M14.6,8.4 L14.6,12 M18.6,8.4 L18.6,10.8'],
    "ic_scale": ['M6.6,19.4 L9.6,7.4 L14.4,7.4 L17.4,19.4 Z',
                 'M9.6,7.4 A3.4,3.4 0 0 1 14.4,7.4'],
    "ic_square": ['M4,4 L20,4 L20,20 L4,20 Z', 'M8,8 L16,8 L16,16 L8,16 Z'],
    "ic_cube": ['M12,3 L20,7.4 L20,16.6 L12,21 L4,16.6 L4,7.4 Z',
                'M4,7.4 L12,12 L20,7.4', 'M12,12 L12,21'],
    "ic_thermometer": ['M10,13.4 L10,5.4 A2,2 0 0 1 14,5.4 L14,13.4 A4,4 0 1 1 10,13.4 Z',
                       'M12,9 L12,16.4'],
    "ic_fuel": ['M4,20.4 L4,4.6 L12.6,4.6 L12.6,20.4 Z', 'M4,9.6 L12.6,9.6',
                'M12.6,8.4 L17,8.4 L17,16.6 A1.8,1.8 0 0 0 20.4,16.6 L20.4,6.6 L17,3.6'],
    "ic_bolt": ['M13.4,3 L5,13.6 L11,13.6 L10.4,21 L19,10.4 L13,10.4 Z'],
    "ic_power": ['M12,3.4 L12,11.4', 'M7.2,6.4 A7,7 0 1 0 16.8,6.4'],
    "ic_wave": ['M2.6,12 C5,6 8,18 10.6,12 C13.2,6 16.2,18 19,12'],
    "ic_angle": ['M4,19.4 L20,19.4 L4,5 Z', 'M9.4,19.4 A5.4,5.4 0 0 0 12.8,14.2'],
    "ic_density": ['M5,7 L19,7 L19,19 L5,19 Z', 'M8.4,7 L8.4,4.4 M12,7 L12,4.4 M15.6,7 L15.6,4.4',
                   'M5,11.4 L19,11.4 M5,15.2 L19,15.2'],
    "ic_flask": ['M9.4,3.4 L14.6,3.4 L14.6,9.4 L19,18.6 A2,2 0 0 1 17.2,21.4 L6.8,21.4 A2,2 0 0 1 5,18.6 L9.4,9.4 Z',
                 'M7.2,14.6 L16.8,14.6'],
    "ic_torque": ['M12,3.4 A8.6,8.6 0 1 0 12,20.6 A8.6,8.6 0 1 0 12,3.4',
                  'M12,7.6 L12,12 L15.6,14'],
    "ic_flow": ['M3,8 L9,8 M15,8 L21,8 M11,8 L13,8',
                'M12,8 C12,14 8,12 8,16 C8,20 12,18 12,21',
                'M9,4.4 L9,11.6 M15,4.4 L15,11.6'],
    "ic_speed": ['M12,19.4 A7.6,7.6 0 1 1 12,4.2 A7.6,7.6 0 1 1 12,19.4',
                 'M12,12 L15.8,8.4'],
    "ic_trend": ['M3.4,16.6 L9,11 L13,15 L20.6,7.4', 'M15.4,7.4 L20.6,7.4 L20.6,12.6'],
    "ic_health": ['M12,20.4 C6,16.2 3.4,13.2 3.4,9.8 A4.4,4.4 0 0 1 12,8 A4.4,4.4 0 0 1 20.6,9.8 C20.6,13.2 18,16.2 12,20.4 Z',
                  'M6.4,12.4 L9.6,12.4 M14.4,12.4 L17.6,12.4 M12,10 L12,15.6'],
    "ic_dice": ['M4.6,4.6 L19.4,4.6 L19.4,19.4 L4.6,19.4 Z',
                'M8.4,8.4 L8.4,8.5', 'M15.6,8.4 L15.6,8.5', 'M12,12 L12,12.1',
                'M8.4,15.6 L8.4,15.7', 'M15.6,15.6 L15.6,15.7'],
}

FILLED = {"ic_star_filled": ["M12,3.2 L14.6,9.2 L21,9.8 L16.2,14 L17.6,20.4 L12,16.9 L6.4,20.4 L7.8,14 L3,9.8 L9.4,9.2 Z"]}

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/text_primary">
{paths}
</vector>
"""

PATH = """    <path
        android:pathData="{data}"
        android:strokeColor="{stroke}"
        android:strokeWidth="1.7"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000" />"""

FILLED_PATH = """    <path
        android:pathData="{data}"
        android:fillColor="{fill}" />"""


def main():
    os.makedirs(OUT, exist_ok=True)
    count = 0
    for name, paths in ICONS.items():
        body = "\n".join(PATH.format(data=p, stroke=STROKE) for p in paths)
        with open(os.path.join(OUT, name + ".xml"), "w") as fh:
            fh.write(TEMPLATE.format(paths=body))
        count += 1
    for name, paths in FILLED.items():
        body = "\n".join(FILLED_PATH.format(data=p, fill=STROKE) for p in paths)
        with open(os.path.join(OUT, name + ".xml"), "w") as fh:
            fh.write(TEMPLATE.format(paths=body))
        count += 1

    # Launcher icon: a compact toolbox glyph.
    launcher = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M32,40 L40,40 L44,34 L64,34 L68,40 L76,40 A6,6 0 0 1 82,46 L82,76 A6,6 0 0 1 76,82 L32,82 A6,6 0 0 1 26,76 L26,46 A6,6 0 0 1 32,40 Z"
        android:fillColor="#2DD4BF" />
    <path
        android:pathData="M45,62 L63,62"
        android:strokeColor="#04211F"
        android:strokeWidth="5"
        android:strokeLineCap="round" />
</vector>
"""
    with open(os.path.join(OUT, "ic_launcher.xml"), "w") as fh:
        fh.write(launcher)
    count += 1
    print("generated %d drawables in %s" % (count, OUT))


if __name__ == "__main__":
    main()
