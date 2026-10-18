#!/usr/bin/env python3
"""Packages a signed-APK-ready archive from an aapt2 base APK.

The APK is assembled by hand (base resources from aapt2 + classes.dex from d8 +
bundled assets) so that the build works without the Android Gradle Plugin.
"""
import os
import sys
import zipfile


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: package_apk.py <base.apk> <classes.dex> <output.apk> [assets_dir]", file=sys.stderr)
        return 2
    base_apk, dex_file, out_apk = sys.argv[1], sys.argv[2], sys.argv[3]
    assets_dir = sys.argv[4] if len(sys.argv) > 4 else None

    if os.path.exists(out_apk):
        os.remove(out_apk)

    with zipfile.ZipFile(base_apk) as src, zipfile.ZipFile(out_apk, "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            # resources.arsc must stay uncompressed and page aligned on Android 11+.
            if info.filename.endswith(".arsc"):
                info.compress_type = zipfile.ZIP_STORED
            dst.writestr(info, data)
        dst.write(dex_file, "classes.dex", zipfile.ZIP_DEFLATED)

        if assets_dir and os.path.isdir(assets_dir):
            for root, _dirs, files in os.walk(assets_dir):
                for name in sorted(files):
                    full = os.path.join(root, name)
                    rel = os.path.relpath(full, assets_dir).replace(os.sep, "/")
                    dst.write(full, "assets/" + rel, zipfile.ZIP_DEFLATED)

    size = os.path.getsize(out_apk)
    print("packaged %s (%.2f MB)" % (out_apk, size / 1048576.0))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
