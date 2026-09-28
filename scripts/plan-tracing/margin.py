"""A building's margin on one of its floors, and the rooms fitted inside it.

Used by register.py. The margin is what the portal's Margin layer draws: the parts of the building
that rise to the floor (its footprint), less the sidewalks on a floor at the street. The rooms go a
wall's thickness inside it. Everything is worked on a raster of GRID units a cell, in the floor's
drawing units, and a room that has to change is traced back into straight edges with trace.py's
fitting, so a wall along a slanted facade comes out slanted and straight.

Standard library only.
"""
import glob
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trace  # noqa: E402  - the outline fitting the tracer already uses

GRID = 2          # units a cell
WALL = 10         # units a wall is thick: how far inside the margin the rooms stop
M_LAT = 110574.0  # the contract's metres per degree, as the portal lays a drawing on the ground
M_LON = 111320.0


def reaches(part, level):
    """As the portal says it: a part reaches the floor at `level` if it rises that high, or, below
    the street, goes that deep."""
    if level >= 0:
        return part["floors"] >= max(1, math.ceil(level))
    return part["basements"] >= -math.floor(level)


def to_drawing(placement):
    """Ground [lon, lat] to the drawing's units, through the building's placement."""
    o = placement["origin"]
    b = math.radians(placement["bearing"])
    m = placement["metresPerUnit"]
    cos_lat = math.cos(math.radians(o["lat"]))

    def convert(c):
        east = (c[0] - o["lon"]) * M_LON * cos_lat
        north = (c[1] - o["lat"]) * M_LAT
        return ((east * math.cos(b) - north * math.sin(b)) / m, (-east * math.sin(b) - north * math.cos(b)) / m)
    return convert


def ground_for(campus, root):
    """The campus's streets, from db/ground, or None."""
    wanted = campus.lower()
    for path in glob.glob(os.path.join(root, "*.json")):
        data = json.load(open(path))
        if data.get("campus", "").lower() == wanted:
            return data
    return None


class Raster:
    """A set of cells over the floor, GRID units a cell, with a margin of cells round it."""

    def __init__(self, width, height, pad=40):
        self.ox, self.oy = -pad, -pad
        self.w = int((width + 2 * pad) / GRID) + 1
        self.h = int((height + 2 * pad) / GRID) + 1
        self.cells = bytearray(self.w * self.h)

    def paint(self, poly, value=1):
        """Fills a polygon (drawing units) with `value`, cell centres by the even-odd rule."""
        ys = [p[1] for p in poly]
        j0 = max(0, int((min(ys) - self.oy) / GRID))
        j1 = min(self.h - 1, int((max(ys) - self.oy) / GRID) + 1)
        n = len(poly)
        for j in range(j0, j1 + 1):
            y = self.oy + (j + 0.5) * GRID
            xs = []
            for k in range(n):
                (ax, ay), (bx, by) = poly[k], poly[(k + 1) % n]
                if (ay > y) != (by > y):
                    xs.append(ax + (y - ay) * (bx - ax) / (by - ay))
            xs.sort()
            row = j * self.w
            for a, b in zip(xs[0::2], xs[1::2]):
                i0 = max(0, math.ceil((a - self.ox) / GRID - 0.5))
                i1 = min(self.w - 1, math.floor((b - self.ox) / GRID - 0.5))
                for i in range(i0, i1 + 1):
                    self.cells[row + i] = value

    def eroded(self, r):
        """The cells whose whole (2r+1)-cell square is set: the set shrunk by r cells all round."""
        w, h = self.w, self.h
        # Summed-area table of the cells that are not set.
        sat = [0] * ((w + 1) * (h + 1))
        for j in range(h):
            run = 0
            row, prev, cur = j * w, j * (w + 1), (j + 1) * (w + 1)
            for i in range(w):
                run += 0 if self.cells[row + i] else 1
                sat[cur + i + 1] = sat[prev + i + 1] + run
        out = Raster.__new__(Raster)
        out.ox, out.oy, out.w, out.h = self.ox, self.oy, w, h
        out.cells = bytearray(w * h)
        for j in range(h):
            j0, j1 = max(0, j - r), min(h, j + r + 1)
            for i in range(w):
                if not self.cells[j * w + i]:
                    continue
                i0, i1 = max(0, i - r), min(w, i + r + 1)
                if sat[j1 * (w + 1) + i1] - sat[j0 * (w + 1) + i1] - sat[j1 * (w + 1) + i0] + sat[j0 * (w + 1) + i0] == 0:
                    out.cells[j * w + i] = 1
        return out

    def get(self, i, j):
        return 0 <= i < self.w and 0 <= j < self.h and self.cells[j * self.w + i]


def floor_margin(building, width, height, level, ground=None):
    """The floor's margin as a raster, and shrunk by a wall: (inside, loose, tight). `loose` is
    shrunk a cell less, to tell a room that fits from one that only rounds outside."""
    convert = to_drawing(building["placement"])
    r = Raster(width, height)
    for part in building.get("footprint") or []:
        if reaches(part, level):
            r.paint([convert(c) for c in part["ring"][:-1]])
    if ground and level <= 1:
        for walk in ground.get("sidewalks", []):
            r.paint([convert(c) for c in walk], 0)
    steps = round(WALL / GRID)
    return r, r.eroded(steps - 1), r.eroded(steps)


def cells_of(poly, raster):
    """The cells a polygon covers, as (i, j) on `raster`'s grid."""
    own = Raster.__new__(Raster)
    own.ox, own.oy, own.w, own.h = raster.ox, raster.oy, raster.w, raster.h
    own.cells = bytearray(raster.w * raster.h)
    own.paint(poly)
    xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
    i0, i1 = max(0, int((min(xs) - raster.ox) / GRID) - 1), min(raster.w - 1, int((max(xs) - raster.ox) / GRID) + 1)
    j0, j1 = max(0, int((min(ys) - raster.oy) / GRID) - 1), min(raster.h - 1, int((max(ys) - raster.oy) / GRID) + 1)
    return [(i, j) for j in range(j0, j1 + 1) for i in range(i0, i1 + 1) if own.cells[j * own.w + i]]


def blank(like):
    """An empty raster on the same grid as `like`."""
    out = Raster.__new__(Raster)
    out.ox, out.oy, out.w, out.h = like.ox, like.oy, like.w, like.h
    out.cells = bytearray(like.w * like.h)
    return out


def clip(poly, loose, tight, blocked):
    """The room cut to the margin, a wall inside, and off the cells set in `blocked`.

    Returns the polygon unchanged when it already fits (to within a cell), a new one fitted with
    straight edges when it had to be cut, or None when little of it is left inside."""
    cells = cells_of(poly, tight)
    if not cells:
        return None
    if all(loose.get(i, j) and not blocked.get(i, j) for i, j in cells):
        return poly
    kept = {(i, j) for i, j in cells if tight.get(i, j) and not blocked.get(i, j)}
    if len(kept) < 0.2 * len(cells):
        return None
    # The largest piece: a room cut in two by the facade keeps the side that is inside.
    pieces, seen = [], set()
    for start in kept:
        if start in seen:
            continue
        stack, piece = [start], []
        seen.add(start)
        while stack:
            i, j = stack.pop()
            piece.append((i, j))
            for q in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)):
                if q in kept and q not in seen:
                    seen.add(q)
                    stack.append(q)
        pieces.append(piece)
    best = max(pieces, key=len)
    fitted = trace.fit_polygon(trace.outline(best), 1.0, 3, 8)
    if not fitted or len(fitted) < 3 or not trace.simple(fitted):
        return None
    return [(tight.ox + x * GRID, tight.oy + y * GRID) for x, y in fitted]


def extend_left(poly, tight, blocked):
    """Moves the room's leftmost wall out to the margin, a wall inside, where nothing is in the
    way: a row of rooms the plan draws short of the facade reaches it."""
    left = min(p[0] for p in poly)
    edge = [p for p in poly if abs(p[0] - left) < 0.5]
    y0, y1 = min(p[1] for p in edge), max(p[1] for p in edge)
    target = left
    first = True
    for j in range(int((y0 - tight.oy) / GRID) + 1, int((y1 - tight.oy) / GRID)):
        i = int((left - tight.ox) / GRID) - 1
        while tight.get(i, j) and not blocked.get(i, j):
            i -= 1
        stop = tight.ox + (i + 1) * GRID
        target = stop if first else max(target, stop)
        first = False
    if target >= left:
        return poly
    return [(target, p[1]) if abs(p[0] - left) < 0.5 else p for p in poly]
