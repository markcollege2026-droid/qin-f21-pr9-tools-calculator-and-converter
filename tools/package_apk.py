#!/usr/bin/env python3
"""Packages a signed-APK-ready archive from an aapt2 base APK.

The APK is assembled by hand (base resources from aapt2 + classes.dex from d8 +
bundled assets) so that the build works without the Android Gradle Plugin.

Every stored (uncompressed) entry is written so that its file data starts at an
offset that is a multiple of 4. The padding goes into the local header's
"extra" field, which is exactly how the official zipalign tool aligns APKs.
This is not an optimisation: the installer of Android 11 (API 30, "R") and
newer rejects an APK whose resources.arsc is not stored uncompressed and
aligned on the 4-byte boundary ("INSTALL_PARSE_FAILED_UNCOMPRESSIBLE_RESOURCES").
"""
import copy
import os
import sys
import zipfile

LOCAL_HEADER_SIZE = 30  # fixed part of the zip local file header
ALIGNMENT = 4


def write_entry(dst: zipfile.ZipFile, pos: int, src_info, name: str, data: bytes, compress_type: int) -> int:
    """Writes one entry and returns the new file position after it.

    `data` must be bytes (writestr has no file-path form). If `src_info` is
    given its metadata (date, attributes) is kept. For STORED entries the
    local header's extra field is padded with zero bytes so the file data
    starts on a 4-byte boundary.
    """
    info = copy.copy(src_info) if src_info is not None else zipfile.ZipInfo(name)
    info.compress_type = compress_type
    if compress_type == zipfile.ZIP_STORED:
        name_bytes = len(name.encode("utf-8"))
        data_offset = pos + LOCAL_HEADER_SIZE + name_bytes + len(info.extra)
        info.extra += b"\x00" * ((ALIGNMENT - data_offset % ALIGNMENT) % ALIGNMENT)
    dst.writestr(info, data)
    size = len(data) if compress_type == zipfile.ZIP_STORED else info.compress_size
    return pos + LOCAL_HEADER_SIZE + len(name.encode("utf-8")) + len(info.extra) + size


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: package_apk.py <base.apk> <classes.dex> <output.apk> [assets_dir]", file=sys.stderr)
        return 2
    base_apk, dex_file, out_apk = sys.argv[1], sys.argv[2], sys.argv[3]
    assets_dir = sys.argv[4] if len(sys.argv) > 4 else None

    if os.path.exists(out_apk):
        os.remove(out_apk)

    with zipfile.ZipFile(base_apk) as src, zipfile.ZipFile(out_apk, "w", zipfile.ZIP_DEFLATED) as dst:
        pos = 0
        for info in src.infolist():
            data = src.read(info.filename)
            # resources.arsc must stay uncompressed and 4-byte aligned on Android 11+.
            compress = zipfile.ZIP_STORED if info.filename.endswith(".arsc") else zipfile.ZIP_DEFLATED
            pos = write_entry(dst, pos, info, info.filename, data, compress)
        with open(dex_file, "rb") as dex:
            pos = write_entry(dst, pos, None, "classes.dex", dex.read(), zipfile.ZIP_DEFLATED)

        if assets_dir and os.path.isdir(assets_dir):
            for root, _dirs, files in os.walk(assets_dir):
                for name in sorted(files):
                    full = os.path.join(root, name)
                    rel = os.path.relpath(full, assets_dir).replace(os.sep, "/")
                    with open(full, "rb") as asset:
                        pos = write_entry(dst, pos, None, "assets/" + rel, asset.read(), zipfile.ZIP_DEFLATED)

    size = os.path.getsize(out_apk)
    print("packaged %s (%.2f MB)" % (out_apk, size / 1048576.0))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
