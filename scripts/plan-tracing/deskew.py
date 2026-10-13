"""Finds how a plan photo is turned, from its own walls, and writes a pe2.py spec.

    python3 deskew.py <photo.bmp> <x0> <y0> <x1> <y1> <spec.json> [scale]

The box is a rough crop of the plan in BMP pixels. Wall and fill edges are sampled; the angle
that makes horizontal edges line up best (sharpest row histogram) is the rotation, and the one
that does the same for vertical edges gives the shear. The corners of the box, corrected by both,
go to the spec as a plaque of the box's size, cropped whole.
"""
import json
import math
import struct
import sys

photo = sys.argv[1]
x0, y0, x1, y1 = map(int, sys.argv[2:6])
out = sys.argv[6]
scale = float(sys.argv[7]) if len(sys.argv) > 7 else 1.0

data = open(photo, "rb").read()
off = struct.unpack_from("<I", data, 10)[0]
W, H = struct.unpack_from("<ii", data, 18)
step = struct.unpack_from("<H", data, 28)[0] // 8
row = (W * step + 3) & ~3
flip = H > 0
H = abs(H)


def lum(x, y):
    yy = H - 1 - y if flip else y
    i = off + yy * row + x * step
    return (data[i] + data[i + 1] + data[i + 2]) / 3


# Edge points: strong luminance change to the next pixel down (horizontal edges) or right.
hpts, vpts = [], []
for y in range(y0, y1 - 1, 2):
    for x in range(x0, x1 - 1, 2):
        c = lum(x, y)
        if abs(c - lum(x, y + 1)) > 45:
            hpts.append((x, y))
        if abs(c - lum(x + 1, y)) > 45:
            vpts.append((x, y))


def sharpness(pts, angle, horizontal):
    s, c = math.sin(angle), math.cos(angle)
    hist = {}
    for x, y in pts:
        k = int(round((y * c - x * s) if horizontal else (x * c + y * s)))
        hist[k] = hist.get(k, 0) + 1
    return sum(v * v for v in hist.values())


def best(pts, horizontal):
    angles = [math.radians(a / 10) for a in range(-40, 41)]
    return max(angles, key=lambda a: sharpness(pts, a, horizontal))


rot = best(hpts, True)       # rows of the plan run at this angle
vrot = best(vpts, False)     # columns run at this one
cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
w, h = x1 - x0, y1 - y0

# Axes of the plan in the photo: u along the rows, v along the columns.
ux, uy = math.cos(rot), math.sin(rot)
vx, vy = -math.sin(vrot), math.cos(vrot)
corners = []
for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
    corners.append([cx + su * w / 2 * ux + sv * h / 2 * vx, cy + su * w / 2 * uy + sv * h / 2 * vy])
json.dump({"corners": corners, "plaque": [w, h], "crop": [0, 0, 1, 1], "scale": scale}, open(out, "w"))
print(f"rotation {math.degrees(rot):.1f} deg, columns {math.degrees(vrot):.1f} deg, "
      f"{len(hpts)} horizontal and {len(vpts)} vertical edge samples")
