#!/usr/bin/env python3
"""
Traces an evacuation plan photo (PE) into the rooms, doors and stairs of a floor.

    scripts/plan-tracing/trace.py <photo> <spec.json> <out prefix>

The evacuation plans on the walls are the most recent drawing of each floor. They paint every room
orange, and draw stairs as rows of treads; the sanitary route plan of the same floor draws the doors. This finds all
three in a photo, so the map looks like the plan people see instead of boxes drawn by eye.

spec: {"corners": [[x, y] top left, top right, bottom right, bottom left of the plan, in photo
                   pixels],
       "plaque":  [w, h] the plan's proportions,
       "crop":    [u0, v0, u1, v1] the part of the rectified plan to keep, as fractions,
       "scale":   pixels of output per unit of "plaque"}
scripts/plan-tracing/deskew.py writes a first spec from a rough box around the plan.

Steps:
 1. Rectify: a homography from the four corners, so walls come out straight.
 2. Classify each pixel: room fill (orange), wall (dark lines, and the light blue of glass in
    them), mark (red and green lines, signs), or background (corridors, outside).
 3. Labels - dark marks a line of text tall with fill all round them - count as marks. Marks are
    drawn on top of the plan, not part of it: the room's fill is grown into them, so the red line
    of "usted está aquí" or a label no longer cuts a room in two.
 4. Rooms are the connected regions of fill. A bridge thinner than a wall's gap - a mark that
    crossed a wall - is cut by eroding, labelling what is left and growing each label back.
 5. Each room's own orange is closed over its labels and lines, and its outline is fitted with
    straight edges: an edge within 12 degrees of level or plumb is squared, a real diagonal wall
    stays diagonal, and steps shorter than a wall is thick are photo wobble and go.
 6. Doors: the evacuation plans do not draw them. The sanitary route plan (RS) of the floor
    does, as teal swings; with "doors" in the spec, that photo is laid over this plan by four
    points both show, and each swing that lands on a room's wall is a door on it.
 7. Stairs are ladders: five or more parallel treads at an even pitch. Flights next to each other
    are one staircase.

Writes <prefix>.json:
    {"width", "height",
     "rooms":  [{"shape": [[x, y], ...], "doors": [[[x, y], [x, y]], ...]}, ...],
     "stairs": [{"shape": [[x, y], ...]}, ...]}
in output pixels, plus <prefix>.rect.bmp/.rect.jpg (the rectified plan) and <prefix>.svg: the
photo with everything traced drawn on it and numbered, and the clean drawing below at the same
scale.

Standard library only; sips (macOS) converts the photo when it is not a BMP already.
"""
import base64
import json
import math
import os
import struct
import subprocess
import sys
from collections import deque

BG, FILL, WALL, MARK, DOOR = 0, 1, 2, 3, 4


# ── Image in and out ─────────────────────────────────────────────────────────────────────────

def read_bmp(path):
    data = open(path, "rb").read()
    offset = struct.unpack_from("<I", data, 10)[0]
    width, height = struct.unpack_from("<ii", data, 18)
    step = struct.unpack_from("<H", data, 28)[0] // 8
    row = (width * step + 3) & ~3
    flip = height > 0
    height = abs(height)

    def rgb(x, y):
        x = min(max(int(x), 0), width - 1)
        y = min(max(int(y), 0), height - 1)
        i = offset + (height - 1 - y if flip else y) * row + x * step
        return data[i + 2], data[i + 1], data[i]
    return width, height, rgb


def write_bmp(path, pixels, width, height):
    row = (width * 3 + 3) & ~3
    out = bytearray(b"BM" + struct.pack("<IHHI", 54 + row * height, 0, 0, 54)
                    + struct.pack("<IiiHHIIiiII", 40, width, -height, 1, 24, 0, row * height, 2835, 2835, 0, 0))
    pad = b"\0" * (row - width * 3)
    for j in range(height):
        line = bytearray()
        for r, g, b in pixels[j * width:(j + 1) * width]:
            line += bytes((b, g, r))
        out += line + pad
    open(path, "wb").write(out)


def survey_file(spec_path, name):
    """A photo the spec names: next to the spec, or in the survey folder - $KAPP_SURVEY, by default
    ~/Desktop/map, where the survey photos were taken to. They stay out of the repository."""
    beside = os.path.join(os.path.dirname(os.path.abspath(spec_path)), name)
    if os.path.exists(beside):
        return beside
    return os.path.join(os.path.expanduser(os.environ.get("KAPP_SURVEY", "~/Desktop/map")), name)


def as_bmp(photo, prefix, width=2800):
    """The photo as a BMP `width` pixels wide - 2800 unless the spec says "resample" - which is
    the frame every coordinate in a spec is measured in."""
    if photo.lower().endswith(".bmp"):
        return photo
    out = f"{prefix}.photo-{width}.bmp"
    if not os.path.exists(out) or os.path.getmtime(out) < os.path.getmtime(photo):
        subprocess.run(["sips", "--resampleWidth", str(width), "-s", "format", "bmp", photo, "--out", out],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return out


# ── Rectification ────────────────────────────────────────────────────────────────────────────

def solve(a, b):
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        for r in range(n):
            if r != c:
                f = m[r][c] / m[c][c]
                for k in range(c, n + 1):
                    m[r][k] -= f * m[c][k]
    return [m[i][n] / m[i][i] for i in range(n)]


def homography(src, dst):
    """The 3x3 matrix taking each src point to its dst point."""
    a, b = [], []
    for (x, y), (u, v) in zip(src, dst):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    h = solve(a, b)
    return [[h[0], h[1], h[2]], [h[3], h[4], h[5]], [h[6], h[7], 1.0]]


# ── Pixels ───────────────────────────────────────────────────────────────────────────────────

def balanced(img):
    """The photo with its paper made white: each channel scaled so its bright end - the paper,
    most of any plan - sits at 225. A dim or yellowish photo then reads like a bright one."""
    gains = []
    for channel in range(3):
        values = sorted(p[channel] for p in img[::7])
        bright = max(1, values[int(len(values) * 0.9)])
        gains.append(min(3.0, 225 / bright))
    return [tuple(min(255, int(v * k)) for v, k in zip(p, gains)) for p in img]


def klass(r, g, b, proportional=False, bounds=((0.52, 0.76), 0.40), red=MARK):
    """Colours measured on the CPC 1 plans: orange (190, 130, 82), red (147, 0, 34), green
    (0, 112, 58), walls (49, 37, 64), glass (104, 138, 129) to (152, 177, 171). In a balanced
    dim photo orange is told by its proportions instead: green a bit over half the red, blue well
    under it - the Edificio Central's plans, orange (130, 80, 15) before balancing. Red is drawn
    over the plan, except where a plan draws its partitions in red: then `red` is WALL."""
    if proportional:
        # The thin lines between classrooms are only a little lighter - green 0.8 of the red
        # where the orange has 0.64 to 0.73 - so the bounds are tight.
        (g_low, g_high), b_high = bounds
        if r > 110 and g_low * r <= g <= g_high * r and b <= b_high * r:
            return FILL
    elif r > 140 and g > 80 and r - b > 60 and 15 < r - g < 72:
        return FILL                                 # past 72 it is the rim of a red line
    if r > 100 and r - g > 70 and r - b > 40:
        return red                                  # red: routes, "usted está aquí", fire gear
    if g > r + 40 and g > b + 20:
        return MARK                                 # green: exit arrows, first aid
    if g > r + 12 and b > r + 8 and r + g + b < 560:
        return DOOR                                 # light blue on a wall
    if r + g + b < 360 and max(r, g, b) - min(r, g, b) < 90:
        return WALL
    if b > r + 10 and r + g + b < 450:
        return WALL                                 # blue and purple lines
    return BG


def components(mask, w, h, value, eight=False):
    """Connected runs of mask == value, as lists of flat indices."""
    seen = bytearray(w * h)
    steps = [1, -1, w, -w] + ([w + 1, w - 1, -w + 1, -w - 1] if eight else [])
    out = []
    for start in range(w * h):
        if mask[start] != value or seen[start]:
            continue
        seen[start] = 1
        q = [start]
        k = 0
        while k < len(q):
            p = q[k]; k += 1
            x = p % w
            for s in steps:
                n = p + s
                if n < 0 or n >= w * h or mask[n] != value or seen[n]:
                    continue
                if abs(n % w - x) > 1:
                    continue                        # wrapped around a row end
                seen[n] = 1
                q.append(n)
        out.append(q)
    return out


def distance_to(mask, w, h, target, outside_is_target):
    """Chessboard distance from every pixel to the nearest one where mask == target."""
    big = w + h
    d = [0 if m == target else big for m in mask]
    for j in range(h):
        for i in range(w):
            p = j * w + i
            v = d[p]
            if not v:
                continue
            if outside_is_target and (i == 0 or j == 0):
                v = 1
            if i > 0:
                v = min(v, d[p - 1] + 1)
            if j > 0:
                v = min(v, d[p - w] + 1)
                if i > 0:
                    v = min(v, d[p - w - 1] + 1)
                if i < w - 1:
                    v = min(v, d[p - w + 1] + 1)
            d[p] = v
    for j in range(h - 1, -1, -1):
        for i in range(w - 1, -1, -1):
            p = j * w + i
            v = d[p]
            if not v:
                continue
            if outside_is_target and (i == w - 1 or j == h - 1):
                v = 1
            if i < w - 1:
                v = min(v, d[p + 1] + 1)
            if j < h - 1:
                v = min(v, d[p + w] + 1)
                if i < w - 1:
                    v = min(v, d[p + w + 1] + 1)
                if i > 0:
                    v = min(v, d[p + w - 1] + 1)
            d[p] = v
    return d


def dilate(a, w, h, r):
    d = distance_to(a, w, h, 1, False)
    return bytearray(1 if v <= r else 0 for v in d)


def erode(a, w, h, r):
    d = distance_to(a, w, h, 0, True)
    return bytearray(1 if v > r else 0 for v in d)


def labels(mask, w, h, tall, around):
    """Dark marks no taller than a line of text with fill all round them: "CAFETERÍA", a room
    number. A wall is part of the web of walls, or has a corridor beside it. Letters that touch a
    wall - "ASCENSOR" in a lift drawn just big enough for it - stay walls, and the spec merges the
    two halves of that room instead."""
    found = []
    for piece in components(mask, w, h, WALL, eight=True):
        xs = [p % w for p in piece]; ys = [p // w for p in piece]
        if max(ys) - min(ys) > tall and max(xs) - min(xs) > tall:
            continue
        inside = set(piece)
        ring = [n for p in piece for n in (p - 1, p + 1, p - w, p + w) if 0 <= n < w * h and n not in inside]
        if sum(1 for n in ring if mask[n] in (FILL, MARK)) >= around * len(ring):
            found.extend(piece)
    return found


# ── Outlines ─────────────────────────────────────────────────────────────────────────────────

def outline(cells):
    """The outer boundary of a set of cells, as a closed list of lattice points, clockwise."""
    s = set(cells)
    edges = {}
    for x, y in cells:
        if (x, y - 1) not in s: edges[(x, y)] = (x + 1, y)
        if (x + 1, y) not in s: edges[(x + 1, y)] = (x + 1, y + 1)
        if (x, y + 1) not in s: edges[(x + 1, y + 1)] = (x, y + 1)
        if (x - 1, y) not in s: edges[(x, y + 1)] = (x, y)
    loops, seen = [], set()
    for start in list(edges):
        if start in seen:
            continue
        loop, p = [], start
        while p not in seen and p in edges:
            seen.add(p); loop.append(p); p = edges[p]
        loops.append(loop)
    return max(loops, key=len)


def rdp(points, eps):
    """Ramer-Douglas-Peucker on an open polyline; returns the indices kept."""
    keep = {0, len(points) - 1}
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (x0, y0), (x1, y1) = points[a], points[b]
        dx, dy = x1 - x0, y1 - y0
        norm = math.hypot(dx, dy) or 1.0
        best, far = -1.0, -1
        for i in range(a + 1, b):
            x, y = points[i]
            d = abs(dy * (x - x0) - dx * (y - y0)) / norm
            if d > best:
                best, far = d, i
        if best > eps:
            keep.add(far)
            stack.append((a, far)); stack.append((far, b))
    return sorted(keep)


AXIS = math.tan(math.radians(12))
DIAGONAL = 15            # shorter than this, a slanted edge is a rounded corner, not a wall


class Line:
    """One edge of a polygon being fitted: level (H, y = c), plumb (V, x = c) or diagonal (D,
    through two points). `w` is how much outline supports it; `d` the direction it is walked."""

    def __init__(self, kind, c=None, p=None, q=None, w=1.0, d=(0, 0)):
        self.kind, self.c, self.p, self.q, self.w, self.d = kind, c, p, q, w, d

    def coeffs(self):
        if self.kind == "H":
            return 0.0, 1.0, self.c
        if self.kind == "V":
            return 1.0, 0.0, self.c
        (x0, y0), (x1, y1) = self.p, self.q
        a, b = y1 - y0, x0 - x1
        return a, b, a * x0 + b * y0

    def angle(self):
        return math.atan2(self.d[1], self.d[0])


def fit(points, diagonal):
    (x0, y0), (x1, y1) = points[0], points[-1]
    dx, dy = x1 - x0, y1 - y0
    w = float(len(points))
    if math.hypot(dx, dy) < diagonal:
        dy, dx = (0, dx) if abs(dx) >= abs(dy) else (dy, 0)
    if abs(dy) <= AXIS * abs(dx):
        return Line("H", c=sorted(p[1] for p in points)[len(points) // 2], w=w, d=(1 if dx > 0 else -1, 0))
    if abs(dx) <= AXIS * abs(dy):
        return Line("V", c=sorted(p[0] for p in points)[len(points) // 2], w=w, d=(0, 1 if dy > 0 else -1))
    return Line("D", p=(x0, y0), q=(x1, y1), w=w, d=(dx, dy))


def parallel(a, b):
    if a.kind != b.kind:
        return False
    if a.kind != "D":
        return True
    diff = abs(a.angle() - b.angle()) % (2 * math.pi)
    return min(diff, 2 * math.pi - diff) < math.radians(10)


def merge(a, b):
    w = a.w + b.w
    if a.kind in "HV":
        return Line(a.kind, c=(a.c * a.w + b.c * b.w) / w, w=w, d=a.d)
    return Line("D", p=a.p, q=b.q, w=w, d=(b.q[0] - a.p[0], b.q[1] - a.p[1]))


def meet(a, b):
    a1, b1, c1 = a.coeffs()
    a2, b2, c2 = b.coeffs()
    det = a1 * b2 - a2 * b1
    if abs(det) < 1e-9:
        return None
    return (c1 * b2 - c2 * b1) / det, (a1 * c2 - a2 * c1) / det


def fit_polygon(loop, eps, shortest, diagonal):
    """Straight edges along a traced outline. Diagonals stay; near-level and near-plumb edges are
    squared; an edge shorter than `shortest` goes - between two parallel edges it was a step, and
    they become one at their weighted position; otherwise its neighbours meet at a corner."""
    n = len(loop)
    far = max(range(n), key=lambda i: (loop[i][0] - loop[0][0]) ** 2 + (loop[i][1] - loop[0][1]) ** 2)
    first = loop[:far + 1]
    second = loop[far:] + [loop[0]]
    corners = [i for i in rdp(first, eps)][:-1] + [far + i for i in rdp(second, eps)][:-1]
    lines = []
    for k, start in enumerate(corners):
        end = corners[(k + 1) % len(corners)]
        span = loop[start:end + 1] if end > start else loop[start:] + loop[:end + 1]
        lines.append(fit(span, diagonal))

    for _ in range(4 * len(lines) + 10):
        i = 0
        while len(lines) > 3 and i < len(lines):
            j = (i + 1) % len(lines)
            if parallel(lines[i], lines[j]):
                lines[i] = merge(lines[i], lines[j])
                del lines[j]
                if j < i:
                    i -= 1
            else:
                i += 1
        if len(lines) < 3:
            return None
        verts = [meet(lines[k - 1], lines[k]) for k in range(len(lines))]
        if any(v is None for v in verts):
            return None
        worst, worst_len = None, None
        for k in range(len(lines)):
            a, b = verts[k], verts[(k + 1) % len(lines)]
            ex, ey = b[0] - a[0], b[1] - a[1]
            length = math.hypot(ex, ey)
            backwards = ex * lines[k].d[0] + ey * lines[k].d[1] < 0
            if backwards:
                length = -1
            if length < shortest and (worst_len is None or length < worst_len):
                worst, worst_len = k, length
        if worst is None:
            return [(round(x), round(y)) for x, y in verts]
        del lines[worst]
        if len(lines) < 3:
            return None
    return None


def simple(poly):
    """No two edges that are not neighbours touch."""
    n = len(poly)

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    def touch(p1, p2, p3, p4):
        d1, d2 = cross(p3, p4, p1), cross(p3, p4, p2)
        d3, d4 = cross(p1, p2, p3), cross(p1, p2, p4)
        return ((d1 > 0) != (d2 > 0) and d1 != 0 and d2 != 0) and ((d3 > 0) != (d4 > 0) and d3 != 0 and d4 != 0)
    for i in range(n):
        for j in range(i + 2, n):
            if i == 0 and j == n - 1:
                continue
            if touch(poly[i], poly[(i + 1) % n], poly[j], poly[(j + 1) % n]):
                return False
    return True


def area(poly):
    return abs(sum(poly[i - 1][0] * poly[i][1] - poly[i][0] * poly[i - 1][1] for i in range(len(poly)))) / 2


def room_outline(pixels, orange, w, close, smooth, eps, shortest, diagonal):
    """The outline of one room, fitted with straight edges. Its own orange says how far the room
    reaches; within that box, the marks drawn on it count too - a line running down the inside of
    a wall covers the room, not a corridor - and outside it they do not, so a line leaving through
    a wall or a red bar outside a door adds nothing. Closed over what is left of labels and glare,
    opened to drop any tail."""
    xs = [p % w for p in pixels]; ys = [p // w for p in pixels]
    pad = close + smooth + 2
    ox, oy = min(xs) - pad, min(ys) - pad
    lw, lh = max(xs) - ox + pad + 1, max(ys) - oy + pad + 1
    own = bytearray(lw * lh)
    for p in pixels:
        if orange[p]:
            own[(p // w - oy) * lw + p % w - ox] = 1
    own = dilate(erode(own, lw, lh, 2), lw, lh, 2)          # specks: the rim of a red line
    lit = [i for i, v in enumerate(own) if v]
    if not lit:
        return None
    bx0, bx1 = min(i % lw for i in lit), max(i % lw for i in lit)
    by0, by1 = min(i // lw for i in lit), max(i // lw for i in lit)
    for p in pixels:
        x, y = p % w - ox, p // w - oy
        if bx0 <= x <= bx1 and by0 <= y <= by1:
            own[y * lw + x] = 1
    shape = erode(dilate(own, lw, lh, close), lw, lh, close)
    shape = dilate(erode(shape, lw, lh, smooth), lw, lh, smooth)
    pieces = components(shape, lw, lh, 1)
    if not pieces:
        return None
    best = max(pieces, key=len)
    loop = outline([(p % lw, p // lw) for p in best])
    poly = fit_polygon(loop, eps, shortest, diagonal)
    if not poly or len(poly) < 3 or not simple(poly) or area(poly) < 0.7 * len(best):
        return None
    return [(x + ox, y + oy) for x, y in poly], len(best)


# ── Doors and stairs ─────────────────────────────────────────────────────────────────────────

def segment_distance(p, a, b):
    ax, ay = a; bx, by = b
    dx, dy = bx - ax, by - ay
    t = 0.0 if dx == dy == 0 else max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(p[0] - ax - t * dx, p[1] - ay - t * dy), t


def inside(poly, x, y):
    hit = False
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i - 1], poly[i]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            hit = not hit
    return hit


def teal(r, g, b, tint=(35, 25)):
    """The door swings of the sanitary route plans (RS): (90-110, 150-160, 140-150) in good light,
    where `tint` - how much greener and bluer than red - is (35, 25); (60-80, 100-110, 90-110) in
    a dimmer photo, where (20, 15) finds them. Not the green of the organic waste route, nor the
    blue of windows and the recycling route, nor the grey of walls."""
    return g > r + tint[0] and b > r + tint[1] and abs(g - b) < 25


def on_outline(wall, poly, reach):
    """Where a door's wall side lands on a room's outline: (distance, [[x, y], [x, y]]) for the
    nearest edge running the same way within `reach` and along half the door or more, else None."""
    (ax, ay), (bx, by) = wall
    horizontal = abs(ay - by) < abs(ax - bx)
    lo, hi = sorted((ax, bx)) if horizontal else sorted((ay, by))
    at = (ay + by) / 2 if horizontal else (ax + bx) / 2
    best = None
    for e in range(len(poly)):
        p, q = poly[e], poly[(e + 1) % len(poly)]
        if horizontal != (abs(p[1] - q[1]) < abs(p[0] - q[0])):
            continue
        elo, ehi = sorted((p[0], q[0])) if horizontal else sorted((p[1], q[1]))
        a, b = max(lo, elo), min(hi, ehi)
        if b - a < 0.5 * (hi - lo):
            continue
        span = (q[0] - p[0]) if horizontal else (q[1] - p[1])
        f = ((a + b) / 2 - (p[0] if horizontal else p[1])) / span if span else 0.0
        on = (p[1] + f * (q[1] - p[1])) if horizontal else (p[0] + f * (q[0] - p[0]))
        d = abs(on - at)
        if d <= reach and (best is None or d < best[0]):
            door = [[round(a), round(on)], [round(b), round(on)]] if horizontal else [[round(on), round(a)], [round(on), round(b)]]
            best = (d, door)
    return best


def doors_from(source, prefix, pairs, rooms, reach, smallest, largest, tint=(35, 25)):
    """Doors, from a plan that draws them. The evacuation plans do not - their light blue is glass
    - but the sanitary route plan of the same floor draws every door as its swing, in teal.
    `pairs` are the same four points on both plans (in that photo's pixels, and in this plan's),
    which lay that photo over this one. Each swing is a square the size of its door with the wall
    along one side; the side that runs along a room's outline, within `reach`, is the door, on
    that outline. A swing on no wall of this plan is a door the building no longer has."""
    _, _, rgb = read_bmp(as_bmp(source, prefix + ".doors"))
    sw, sh = max(p[0][0] for p in pairs), max(p[0][1] for p in pairs)
    lo_x, lo_y = min(p[0][0] for p in pairs), min(p[0][1] for p in pairs)
    hm = homography([p[0] for p in pairs], [p[1] for p in pairs])

    def to_plan(x, y):
        z = hm[2][0] * x + hm[2][1] * y + hm[2][2]
        return ((hm[0][0] * x + hm[0][1] * y + hm[0][2]) / z, (hm[1][0] * x + hm[1][1] * y + hm[1][2]) / z)

    margin = 20
    x0, y0, x1, y1 = lo_x - margin, lo_y - margin, sw + margin, sh + margin
    bw, bh = x1 - x0, y1 - y0
    mask = bytearray(1 if teal(*rgb(x0 + i, y0 + j), tint) else 0 for j in range(bh) for i in range(bw))
    joined = dilate(mask, bw, bh, 2)
    pieces = []
    for piece in components(joined, bw, bh, 1, eight=True):
        xs = [p % bw for p in piece]; ys = [p // bw for p in piece]
        box = (min(xs) + 2, min(ys) + 2, max(xs) - 2, max(ys) - 2)
        if max(box[2] - box[0], box[3] - box[1]) >= smallest * 0.6:
            pieces.append(box)

    def square(box):
        bw_, bh_ = box[2] - box[0], box[3] - box[1]
        return smallest <= max(bw_, bh_) <= largest and abs(bw_ - bh_) <= 0.3 * max(bw_, bh_)

    # A swing is often two pieces: the leaf, a straight bar, and the arc, whose end by the hinge
    # the photo barely shows. Each piece that is not a swing on its own goes with the one that
    # makes the most door-like square with it.
    boxes, loose = [b_ for b_ in pieces if square(b_)], [b_ for b_ in pieces if not square(b_)]
    while loose:
        piece = loose.pop()
        best = None
        for other in loose:
            union = (min(piece[0], other[0]), min(piece[1], other[1]), max(piece[2], other[2]), max(piece[3], other[3]))
            area_ = (union[2] - union[0]) * (union[3] - union[1])
            longest = max(piece[2] - piece[0], piece[3] - piece[1], other[2] - other[0], other[3] - other[1])
            if square(union) and max(union[2] - union[0], union[3] - union[1]) <= 1.2 * longest \
                    and (best is None or area_ < best[0]):
                best = (area_, other, union)
        if best:
            loose.remove(best[1])
            boxes.append(best[2])

    def ink(a, c):
        cells = [(i, j) for i in range(a[0], c[0] + 1) for j in range(a[1], c[1] + 1)]
        return sum(mask[j * bw + i] for i, j in cells) / max(1, len(cells))

    # The swing's four sides. The leaf is the one drawn whole; the wall is one of the two across
    # it - the one lying on a wall of this plan.
    candidates = []
    for l, t, r, b in boxes:
        sides = [((l, t), (r, t), ((l, t), (r, t + 3))), ((r, t), (r, b), ((r - 3, t), (r, b))),
                 ((l, b), (r, b), ((l, b - 3), (r, b))), ((l, t), (l, b), ((l, t), (l + 3, b)))]
        leaf = max(range(4), key=lambda k: ink(*sides[k][2]))
        candidates.append(((l, t, r, b), [((x0 + sides[k][0][0], y0 + sides[k][0][1]), (x0 + sides[k][1][0], y0 + sides[k][1][1]))
                                          for k in ((leaf + 1) % 4, (leaf + 3) % 4)]))

    def place(mapping):
        found, level, plumb = [[] for _ in rooms], [], []
        for _, walls in candidates:
            best = None
            for a, c in walls:
                wall = (mapping(*a), mapping(*c))
                for poly in rooms:
                    hit = on_outline(wall, poly, reach)
                    if hit and (best is None or hit[0] < best[0]):
                        best = (hit[0], wall, hit[1])
            if best is None:
                continue                                # a door this building no longer has
            _, wall, door = best
            for k, poly in enumerate(rooms):
                hit = on_outline(wall, poly, reach)
                if hit:
                    found[k].append(hit[1])
            mid = ((wall[0][0] + wall[1][0]) / 2, (wall[0][1] + wall[1][1]) / 2)
            if door[0][1] == door[1][1]:
                level.append((mid, door[0][1]))
            else:
                plumb.append((mid, door[0][0]))
        return found, level, plumb

    def fitted(pairs_, axis):
        """The line a*x + b*y + c that best sends the mapped points to where their doors landed;
        with too few, or all in a row, only the shift."""
        if not pairs_:
            return (1.0, 0.0, 0.0) if axis == 0 else (0.0, 1.0, 0.0)
        xs_ = [m[0] for m, _ in pairs_]; ys_ = [m[1] for m, _ in pairs_]
        if len(pairs_) >= 4 and max(xs_) - min(xs_) > 200 and max(ys_) - min(ys_) > 60:
            rows = [[m[0], m[1], 1.0] for m, _ in pairs_]
            ata = [[sum(r_[i] * r_[j] for r_ in rows) for j in range(3)] for i in range(3)]
            atb = [sum(r_[i] * v for r_, (_, v) in zip(rows, pairs_)) for i in range(3)]
            return tuple(solve(ata, atb))
        shift = sum(v - m[axis] for m, v in pairs_) / len(pairs_)
        return (1.0, 0.0, shift) if axis == 0 else (0.0, 1.0, shift)

    # Four points lay the photos over each other to within a wall's width or so; the doors that
    # land show where it is off, and the rest land better on a second pass.
    found, level, plumb = place(to_plan)
    fx, fy = fitted(plumb, 0), fitted(level, 1)

    def corrected(x, y):
        px, py = to_plan(x, y)
        return fx[0] * px + fx[1] * py + fx[2], fy[0] * px + fy[1] * py + fy[2]
    found, _, _ = place(corrected)
    swings = []
    for (l, t, r, b), _ in candidates:
        qx, qy = zip(*(corrected(x0 + x, y0 + y) for x, y in ((l, t), (r, b))))
        swings.append((min(qx), min(qy), max(qx), max(qy)))
    return found, swings


def ladders(mask, w, h, tread, pitch, least):
    """Flights of stairs: `least` or more parallel dark strokes at least `tread` long, each
    `pitch` = (min, max) pixels after the one before, lined up with it."""
    flights = []
    for vertical in (False, True):
        outer, inner = (w, h) if vertical else (h, w)
        strokes = []                                    # (position, start, end)
        for o in range(outer):
            i = 0
            while i < inner:
                p = i * w + o if vertical else o * w + i
                if mask[p] != WALL:
                    i += 1
                    continue
                k = i
                while k < inner and mask[k * w + o if vertical else o * w + k] == WALL:
                    k += 1
                if tread <= k - i <= inner // 3:
                    strokes.append((o, i, k))
                i = k
        # A tread two or three pixels thick is one line, not three.
        by_position = {}
        for o, a, b in strokes:
            by_position.setdefault(o, []).append((a, b))
        lines, active = [], []
        for o in range(outer):
            nxt = []
            for a, b in by_position.get(o, ()):
                for line in active:
                    if abs(line[2] - a) <= 3 and abs(line[3] - b) <= 3:
                        line[1] = o
                        nxt.append(line)
                        break
                else:
                    line = [o, o, a, b]
                    lines.append(line)
                    nxt.append(line)
            active = nxt
        lines = [((l[0] + l[1]) / 2, l[2], l[3]) for l in lines]
        lines.sort()
        used = set()
        for s, first in enumerate(lines):
            if s in used:
                continue
            run = [s]
            last = first
            for t in range(s + 1, len(lines)):
                if t in used:
                    continue
                o, a, b = lines[t]
                gap = o - last[0]
                if gap > pitch[1]:
                    if o - last[0] > pitch[1] * 2:
                        break
                    continue
                overlap = min(b, last[2]) - max(a, last[1])
                if gap >= pitch[0] and overlap >= 0.7 * min(b - a, last[2] - last[1]):
                    run.append(t)
                    last = lines[t]
            gaps = sorted(lines[run[i + 1]][0] - lines[run[i]][0] for i in range(len(run) - 1))
            regular = gaps and gaps[-1] <= 1.5 * gaps[len(gaps) // 2] and gaps[0] >= 0.6 * gaps[len(gaps) // 2]
            if len(run) >= least and regular:
                used.update(run)
                os_ = [lines[t][0] for t in run]
                a = min(lines[t][1] for t in run); b = max(lines[t][2] for t in run)
                o0, o1 = min(os_), max(os_)
                flights.append((a, o0, b, o1) if not vertical else (o0, a, o1, b))
    # Flights that touch or nearly do are one staircase.
    boxes = [list(f) for f in flights]
    merged = True
    while merged:
        merged = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                a, b = boxes[i], boxes[j]
                if a[0] - pitch[1] <= b[2] and b[0] - pitch[1] <= a[2] and a[1] - pitch[1] <= b[3] and b[1] - pitch[1] <= a[3]:
                    boxes[i] = [min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3])]
                    del boxes[j]
                    merged = True
                    break
            if merged:
                break
    return [snap_to_walls(mask, w, h, box, inward=pitch[1], outward=2 * pitch[1]) for box in boxes]


def snap_to_walls(mask, w, h, box, inward, outward):
    """Treads stop short of the walls round a staircase, and a landing has none: each side of the
    box moves to a wall line - half or more of it dark - right at its edge, else out to the
    nearest within `outward`, else in to one within `inward`."""
    x0, y0, x1, y1 = box

    def dark(cells):
        return bool(cells) and sum(1 for p in cells if mask[p] == WALL) >= 0.5 * len(cells)

    def column(x, a, b):
        return [j * w + x for j in range(max(0, a), min(h, b))] if 0 <= x < w else []

    def row(y, a, b):
        return [y * w + i for i in range(max(0, a), min(w, b))] if 0 <= y < h else []

    def nearest(edge, out, probe):
        # Outward first: inside the box, every tread is a dark line too.
        for d in [-d for d in range(0, 5)] + list(range(1, outward + 1)) + [-d for d in range(5, inward + 1)]:
            if probe(round(edge + out * d)):
                return edge + out * d
        return edge

    my, mx = round((y1 - y0) / 10), round((x1 - x0) / 10)
    x0 = nearest(x0, -1, lambda x: dark(column(x, round(y0) + my, round(y1) - my)))
    x1 = nearest(x1, +1, lambda x: dark(column(x, round(y0) + my, round(y1) - my)))
    y0 = nearest(y0, -1, lambda y: dark(row(y, round(x0) + mx, round(x1) - mx)))
    y1 = nearest(y1, +1, lambda y: dark(row(y, round(x0) + mx, round(x1) - mx)))
    return [(round(x0), round(y0)), (round(x1), round(y0)), (round(x1), round(y1)), (round(x0), round(y1))]


# ── The whole plan ───────────────────────────────────────────────────────────────────────────

def main():
    photo, spec_path, prefix = sys.argv[1:4]
    spec = json.load(open(spec_path))
    _, _, rgb = read_bmp(as_bmp(photo, prefix, spec.get("resample", 2800)))

    pw, ph = spec["plaque"]
    scale = spec["scale"]
    u0, v0, u1, v1 = spec["crop"]
    w, h = int((u1 - u0) * pw * scale), int((v1 - v0) * ph * scale)
    hm = homography([(0, 0), (pw, 0), (pw, ph), (0, ph)], spec["corners"])
    unit = w / 1640                                     # the sizes below were set on a 1640 wide plan

    def size(name, default):
        return spec.get(name, max(1, round(default * unit)))

    # 1. Rectify.
    img = []
    for j in range(h):
        v = v0 * ph + j / scale
        for i in range(w):
            u = u0 * pw + i / scale
            x = hm[0][0] * u + hm[0][1] * v + hm[0][2]
            y = hm[1][0] * u + hm[1][1] * v + hm[1][2]
            z = hm[2][0] * u + hm[2][1] * v + hm[2][2]
            img.append(rgb(x / z, y / z))
    write_bmp(prefix + ".rect.bmp", img, w, h)

    # 2. Classify. A plan photographed in a dim corridor ("light": "balanced") has its light
    #    evened out first, and its orange told by proportion rather than by brightness.
    dim = spec.get("light") == "balanced"
    if dim:
        img = balanced(img)
    # A washed-out photo - glare over the glass - may need looser bounds: "fill": [[g low, g high], b high].
    bounds = tuple(spec.get("fill", ((0.52, 0.76), 0.40)))
    # The Centro de Investigaciones draws the partitions between its offices in red ("red": "wall");
    # the red line of "usted está aquí" then cuts the room it crosses, and "merge" joins it again.
    red = WALL if spec.get("red") == "wall" else MARK
    mask = bytearray(klass(*c, proportional=dim, bounds=bounds, red=red) for c in img)

    # 7 first, while the treads are still walls: stairs.
    stairs = ladders(mask, w, h, tread=size("tread", 18), pitch=(size("pitch_min", 5), size("pitch_max", 22)),
                     least=spec.get("treads", 6))

    # 3. Letters are marks; the fill grows into every mark it touches, far enough to close a line
    #    crossing a room from both sides but not to run down a corridor.
    for p in labels(mask, w, h, tall=size("text", 23), around=0.6):
        mask[p] = MARK
    plan = bytearray(mask)
    orange = bytearray(1 if m == FILL else 0 for m in mask)
    frontier = [p for p in range(w * h) if mask[p] == FILL]
    for _ in range(size("grow", 10)):
        nxt = []
        for p in frontier:
            x = p % w
            for n in (p - 1 if x > 0 else -1, p + 1 if x < w - 1 else -1, p - w, p + w):
                if 0 <= n < w * h and mask[n] == MARK:
                    mask[n] = FILL
                    nxt.append(n)
        frontier = nxt

    # 4. Cut thin bridges: erode, label the cores, grow each core back over its own fill.
    fill = bytearray(1 if m == FILL else 0 for m in mask)
    core = erode(fill, w, h, size("cut", 6))
    label = [0] * (w * h)
    queue = deque()
    count = 0
    for piece in components(core, w, h, 1):
        if len(piece) < 30:
            continue
        count += 1
        for p in piece:
            label[p] = count
            queue.append(p)
    while queue:
        p = queue.popleft()
        x = p % w
        for n in (p - 1 if x > 0 else -1, p + 1 if x < w - 1 else -1, p - w, p + w):
            if 0 <= n < w * h and fill[n] and not label[n]:
                label[n] = label[p]
                queue.append(n)
    regions = [[] for _ in range(count + 1)]
    for p, lab in enumerate(label):
        if lab:
            regions[lab].append(p)

    # The spec's corrections, where a plan defeats the rules: rooms the plan's own label cuts in
    # two ("merge", a point in each part) and regions that are not rooms ("drop", a point in each).
    def region_at(x, y):
        return label[int(y) * w + int(x)]
    merged = set()
    for group in spec.get("merge", []):
        found = {region_at(x, y) for x, y in group}
        if 0 in found:
            print(f"  merge {group}: a point is on no room", file=sys.stderr)
        parts = sorted(found - {0})
        for other in parts[1:]:
            regions[parts[0]].extend(regions[other])
            regions[other] = []
        if parts:
            merged.add(parts[0])
    for x, y in spec.get("drop", []):
        if not region_at(x, y):
            print(f"  drop {[x, y]}: the point is on no room", file=sys.stderr)
        regions[region_at(x, y)] = []

    # 5. Outline each room.
    minimum = max(60, w * h // 800)
    narrowest = size("narrowest", 28)
    rooms = []
    for index, region in enumerate(regions):
        if index == 0 or len(region) < minimum or sum(orange[p] for p in region) < 0.6 * len(region):
            continue                        # a speck, or mostly a sign the fill grew into
        # Parts the spec joins are closed across what split them - a label a line of text tall.
        close = size("merge_close", 10) if index in merged else size("close", 4)
        traced = room_outline(region, orange, w, close=close, smooth=size("smooth", 5),
                              eps=spec.get("eps", 2.5 * unit), shortest=size("shortest", 12),
                              diagonal=size("diagonal", DIAGONAL))
        if not traced:
            print(f"  a region of {len(region)} px could not be outlined", file=sys.stderr)
            continue
        poly, _ = traced
        xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
        if min(max(xs) - min(xs), max(ys) - min(ys)) >= narrowest:
            rooms.append(poly)
    # Rooms the spec says are rectangles ("box", a point in each): the door swings a plan draws
    # inside them cut their corners, and the box around what was traced is the room.
    for x, y in spec.get("box", []):
        k = next((k for k, poly in enumerate(rooms) if inside(poly, x, y)), None)
        if k is None:
            print(f"  box {[x, y]}: the point is on no room", file=sys.stderr)
            continue
        xs = [q[0] for q in rooms[k]]; ys = [q[1] for q in rooms[k]]
        rooms[k] = [(min(xs), min(ys)), (max(xs), min(ys)), (max(xs), max(ys)), (min(xs), max(ys))]
    rooms.extend([tuple(q) for q in r["shape"]] for r in spec.get("rooms", []))
    rooms.sort(key=lambda p: (min(q[1] for q in p) // (4 * narrowest), min(q[0] for q in p)))
    # A staircase narrower than a flight is a label's letters, not stairs.
    flight = size("flight", 30)
    stairs = [s for s in stairs if not any(overlap_box(s, r) for r in rooms)
              and min(s[2][0] - s[0][0], s[2][1] - s[0][1]) >= flight]
    for drawn in spec.get("stairs", []):
        shape = [tuple(q) for q in drawn]
        stairs = [s for s in stairs if not overlap_box(s, shape)] + [shape]

    # 6. Doors, from the plan of the floor that draws them.
    doors, swings = [[] for _ in rooms], []
    if "doors" in spec:
        source = survey_file(spec_path, spec["doors"]["photo"])
        doors, swings = doors_from(source, prefix, spec["doors"]["pairs"], rooms, reach=size("reach", 16),
                                   smallest=spec["doors"].get("smallest", 25), largest=spec["doors"].get("largest", 140),
                                   tint=tuple(spec["doors"].get("tint", (35, 25))))

    json.dump({"width": w, "height": h,
               "rooms": [{"shape": [list(q) for q in poly], "doors": doors[k]} for k, poly in enumerate(rooms)],
               "stairs": [{"shape": [list(q) for q in s]} for s in stairs]},
              open(prefix + ".json", "w"))
    preview(prefix, w, h, rooms, doors, stairs, swings)
    print(f"{w}x{h}: {len(rooms)} rooms, {sum(len(d) for d in doors)} doors, {len(stairs)} stairs; "
          f"corners {[len(p) for p in rooms]}")


def overlap_box(box, poly):
    """Whether a staircase's box covers much of a room: then it is the room's pattern, not stairs."""
    bx0, by0 = box[0]; bx1, by1 = box[2]
    xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
    ix = min(bx1, max(xs)) - max(bx0, min(xs))
    iy = min(by1, max(ys)) - max(by0, min(ys))
    return ix > 0 and iy > 0 and ix * iy > 0.3 * (bx1 - bx0) * (by1 - by0)


def preview(prefix, w, h, rooms, doors, stairs, swings=()):
    """The check: the plan with everything traced drawn on it and numbered, the drawing below."""
    subprocess.run(["sips", "-s", "format", "jpeg", prefix + ".rect.bmp", "--out", prefix + ".rect.jpg"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    photo64 = base64.b64encode(open(prefix + ".rect.jpg", "rb").read()).decode()
    side = max(w, h * 2 + 20)
    colors = ["#2f6fdf", "#1f9d55", "#c2410c", "#7c3aed", "#b45309", "#be123c", "#0f766e"]
    fills = ["#cfe0ff", "#d6f0d8", "#f6e2c8", "#e6dcff", "#fff3a8", "#ffd9d4", "#dfe4ea"]
    top, bottom = [], []

    def label(poly, text, big):
        cx = sum(p[0] for p in poly) / len(poly); cy = sum(p[1] for p in poly) / len(poly)
        if big:
            return (f'<text x="{cx}" y="{cy}" font-size="18" font-weight="bold" fill="{big}" text-anchor="middle" '
                    f'font-family="Helvetica" stroke="#fff" stroke-width="4" paint-order="stroke">{text}</text>')
        return f'<text x="{cx}" y="{cy}" font-size="14" text-anchor="middle" font-family="Helvetica">{text}</text>'

    for k, poly in enumerate(rooms):
        d = " ".join(f"{x},{y}" for x, y in poly)
        c = colors[k % len(colors)]
        top.append(f'<polygon points="{d}" fill="{c}" fill-opacity="0.18" stroke="{c}" stroke-width="3"/>'
                   + label(poly, k, c))
        bottom.append(f'<polygon points="{d}" fill="{fills[k % len(fills)]}" stroke="#333" stroke-width="2"/>'
                      + label(poly, k, None))
        for (x0, y0), (x1, y1) in doors[k]:
            door = f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="#00a3c4" stroke-width="6" stroke-linecap="butt"/>'
            top.append(door)
            bottom.append(door)
    for x0, y0, x1, y1 in swings:
        top.append(f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="none" stroke="#0d9488" '
                   f'stroke-width="1.5" stroke-dasharray="3 2"/>')
    for k, s in enumerate(stairs):
        d = " ".join(f"{x},{y}" for x, y in s)
        top.append(f'<polygon points="{d}" fill="#475569" fill-opacity="0.25" stroke="#475569" stroke-width="3" '
                   f'stroke-dasharray="8 4"/>' + label(s, f"E{k}", "#475569"))
        bottom.append(f'<polygon points="{d}" fill="url(#treads)" stroke="#333" stroke-width="2"/>'
                      + label(s, f"E{k}", None))
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
           f'width="{side}" height="{side}">',
           '<defs><pattern id="treads" width="8" height="8" patternUnits="userSpaceOnUse">'
           '<rect width="8" height="8" fill="#e2e8f0"/><line x1="0" y1="0" x2="0" y2="8" stroke="#94a3b8" stroke-width="2"/>'
           '</pattern></defs>',
           f'<rect width="{side}" height="{side}" fill="#fff"/>',
           f'<image x="0" y="0" width="{w}" height="{h}" xlink:href="data:image/jpeg;base64,{photo64}"/>',
           *top,
           f'<g transform="translate(0,{h + 20})"><rect width="{w}" height="{h}" fill="#f4f6f5" stroke="#ccc"/>',
           *bottom, "</g></svg>"]
    open(prefix + ".svg", "w").write("\n".join(svg))


if __name__ == "__main__":
    main()
