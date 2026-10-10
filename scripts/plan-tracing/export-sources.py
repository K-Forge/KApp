#!/usr/bin/env python3
"""
Copies the survey photos into docs/map/fuentes/ small enough to keep in git, and with nothing in
them but the picture.

    scripts/plan-tracing/export-sources.py [survey folder] [out folder]

The survey folder is $KAPP_SURVEY, by default ~/Desktop/map; the out folder, docs/map/fuentes.
Each photo lands in its building's folder (ec, cpc-1, jaab, bi, mu, ea - the names of
docs/map/levantamiento/) as NAME.jpg:

 - turned upright, since the camera only records which way it was held;
 - at most 2000 pixels on its long side, re-encoded until it is about 400 KB;
 - with no EXIF, XMP or IPTC. A phone writes where the photo was taken (GPS), the camera and the
   time into every photo; only the colour profile is kept.

Photos that are not of a plan - the views, VISTA in their names - are skipped: they are screenshots
of Street View and of the university's own photos and renders, which are not ours to publish.

The tracing scripts keep reading the full-size originals; these copies are what the repository
keeps of them.
"""
import os
import pathlib
import struct
import subprocess
import sys
import tempfile

FOLDERS = {"Edificio Central": "ec", "CPC 1": "cpc-1", "Centro de Investigaciones": "jaab",
           "Bienestar Institucional": "bi", "Casa Medio Universitario": "mu",
           "Edificio Administrativo": "ea"}
LONG_SIDE = 2000
TARGET = 420_000
# EXIF orientation -> the clockwise turn that makes the photo upright.
TURN = {3: 180, 6: 90, 8: 270}


def segments(jpeg):
    """The JPEG's header segments as (marker, bytes) up to the start of the image data, then the
    rest as one piece."""
    assert jpeg[:2] == b"\xff\xd8", "not a JPEG"
    i, out = 2, []
    while True:
        marker = jpeg[i + 1]
        if marker == 0xDA:  # start of scan: the image data follows
            out.append((None, jpeg[i:]))
            return out
        length = struct.unpack(">H", jpeg[i + 2:i + 4])[0]
        out.append((marker, jpeg[i:i + 2 + length]))
        i += 2 + length


def orientation(jpeg):
    """The EXIF orientation tag (1 when there is none)."""
    for marker, seg in segments(jpeg):
        if marker == 0xE1 and seg[4:10] == b"Exif\x00\x00":
            tiff = seg[10:]
            end = "<" if tiff[:2] == b"II" else ">"
            ifd = struct.unpack(end + "I", tiff[4:8])[0]
            for n in range(struct.unpack(end + "H", tiff[ifd:ifd + 2])[0]):
                entry = tiff[ifd + 2 + 12 * n:ifd + 14 + 12 * n]
                if struct.unpack(end + "H", entry[:2])[0] == 0x0112:
                    return struct.unpack(end + "H", entry[8:10])[0]
    return 1


def strip(jpeg):
    """The JPEG without its APP1 (EXIF, XMP) and APP13 (IPTC) segments."""
    return b"\xff\xd8" + b"".join(seg for marker, seg in segments(jpeg) if marker not in (0xE1, 0xED))


def sips(photo, out, quality, turn=0):
    args = ["sips", "-s", "format", "jpeg", "-s", "formatOptions", str(quality), "-Z", str(LONG_SIDE)]
    if turn:
        args += ["-r", str(turn)]
    subprocess.run(args + [str(photo), "--out", str(out)], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return out.read_bytes()


def export(photo, dest, tmp):
    turn = TURN.get(orientation(sips(photo, tmp / "probe.jpg", 10)), 0)
    for quality in range(80, 50, -5):
        jpeg = strip(sips(photo, tmp / "out.jpg", quality, turn))
        if len(jpeg) <= TARGET:
            break
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(jpeg)
    return quality, len(jpeg)


def main():
    root = pathlib.Path(__file__).resolve().parents[2]
    survey = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else
                          os.path.expanduser(os.environ.get("KAPP_SURVEY", "~/Desktop/map")))
    out = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else root / "docs/map/fuentes"
    total = 0
    with tempfile.TemporaryDirectory() as tmp:
        for photo in sorted(survey.rglob("*")):
            rel = photo.relative_to(survey)
            if photo.suffix.lower() not in (".heic", ".jpeg", ".jpg") or "VISTA" in photo.stem.upper():
                continue
            if len(rel.parts) != 2 or rel.parts[0] not in FOLDERS:
                print(f"skipped {rel}: not in a building's folder", file=sys.stderr)
                continue
            dest = out / FOLDERS[rel.parts[0]] / (photo.stem.upper().replace(" ", "-") + ".jpg")
            quality, size = export(photo, dest, pathlib.Path(tmp))
            total += size
            print(f"{dest.relative_to(out)}\tq{quality}\t{size // 1024} KB")
    print(f"total {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
