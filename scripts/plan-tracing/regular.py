"""A floor's rooms made regular: straight walls at right angles, no steps a pen or a raster left,
and one line for a wall two neighbours share.

Used by register.py after the fit, when the spec asks for it:

    "regular": {"jog": 30,       a step shorter than this, in drawing units, is wobble and goes
                "share": 8,      two parallel walls this close that run side by side become one
                "why": "..."}
    and, on a floor, "askew": [{"spaces": ["P1-AUD-*"], "top": [[x, y], [x, y]],
                                "bottom": [[x, y], [x, y]], "why": "..."}]
                                 a wing between two facades that are not parallel to the rest,
                                 squared in its own frame (Frame)

Every wall goes level or plumb. Stairs and lifts that climb the building are anchors: their walls
do not move, and a neighbour's wall close enough moves onto them. A room is worked as the lines of
its walls, not as its corners: each corner is where two consecutive walls meet, so a wall moved is
moved whole. Where two rooms come to overlap, one moves its wall onto the other's or the larger
gives way. A room that would fold, move further than `share` on average, or overlap a neighbour
anyway keeps the outline it was drawn with. Standard library only.
"""
import fnmatch
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trace  # noqa: E402  - the outline of a set of cells

AXES = (0.0, 90.0)


class Frame:
    """A wing that stands askew between two facades that are not parallel: x stays, and y is
    measured from the top facade towards the bottom one, so the wing becomes a rectangle and its
    rooms can be squared in it. Back on the floor, a level wall runs along the facades, fanning
    from one's angle to the other's, and a plumb wall stays plumb."""

    def __init__(self, top, bottom):
        self.top, self.bottom = top, bottom
        self.x0 = min(top[0][0], bottom[0][0])
        self.y0, self.h0 = self._top(self.x0), self._bottom(self.x0) - self._top(self.x0)

    @staticmethod
    def _at(line, x):
        (ax, ay), (bx, by) = line
        return ay + (x - ax) * (by - ay) / (bx - ax)

    def _top(self, x):
        return self._at(self.top, x)

    def _bottom(self, x):
        return self._at(self.bottom, x)

    def into(self, p):
        x, y = p
        return (x, self.y0 + (y - self._top(x)) / (self._bottom(x) - self._top(x)) * self.h0)

    def out_of(self, p):
        x, y = p
        return (x, self._top(x) + (y - self.y0) / self.h0 * (self._bottom(x) - self._top(x)))


def _unit(theta):
    r = math.radians(theta)
    return (math.cos(r), math.sin(r)), (-math.sin(r), math.cos(r))


def _gap(theta_a, theta_b):
    d = abs(theta_a - theta_b) % 180
    return min(d, 180 - d)


def _squared(pts, cell=4):
    """The room as the cells of `cell` units it covers, outlined: level and plumb edges only,
    whatever the pen did, so a room drawn with a loop that touches itself still has an outline."""
    x0, y0 = math.floor(min(p[0] for p in pts)), math.floor(min(p[1] for p in pts))
    x1, y1 = max(p[0] for p in pts), max(p[1] for p in pts)
    cells = [(i, j) for j in range(int((y1 - y0) / cell) + 1) for i in range(int((x1 - x0) / cell) + 1)
             if _inside(pts, x0 + (i + 0.5) * cell, y0 + (j + 0.5) * cell)]
    if not cells:
        return pts
    # Two cells that touch only at a corner pinch the outline there: the cell between them fills.
    have = set(cells)
    pinched = True
    while pinched:
        pinched = False
        for i, j in list(have):
            for di in (-1, 1):
                if (i + di, j + 1) in have and (i + di, j) not in have and (i, j + 1) not in have:
                    have.add((i, j + 1))
                    pinched = True
    return _tidy([(x0 + i * cell, y0 + j * cell) for i, j in trace.outline(have)])


class Wall:
    """One wall of a room: its direction (degrees), its offset along the normal, which way the
    outline runs along it, and how long the pen drew it."""

    def __init__(self, theta, d, sense, length):
        self.theta, self.d, self.sense, self.length = theta, d, sense, length

    def point(self, other):
        """Where this wall meets the next, or None when they run parallel."""
        (ux, uy), (nx, ny) = _unit(self.theta)
        (vx, vy), (mx, my) = _unit(other.theta)
        det = nx * my - ny * mx
        if abs(det) < 1e-9:
            return None
        return ((self.d * my - other.d * ny) / det, (nx * other.d - mx * self.d) / det)


def _walls(pts, jog=20):
    """The walls of a squared outline; consecutive walls that run parallel made one."""
    walls = []
    for a, b in zip(pts, pts[1:] + pts[:1]):
        length = math.dist(a, b)
        if length < 1e-9:
            continue
        theta = 0.0 if abs(b[0] - a[0]) >= abs(b[1] - a[1]) else 90.0
        (ux, uy), (nx, ny) = _unit(theta)
        sense = 1 if (b[0] - a[0]) * ux + (b[1] - a[1]) * uy >= 0 else -1
        walls.append(Wall(theta, nx * (a[0] + b[0]) / 2 + ny * (a[1] + b[1]) / 2, sense, length))
    return _merge(walls, jog)


def _exact(pts):
    """An anchor's walls as they are: (theta, d, sense, length) for each edge, level or plumb."""
    out = []
    for a, b in zip(pts, pts[1:] + pts[:1]):
        theta = 0.0 if a[1] == b[1] else 90.0
        (ux, uy), (nx, ny) = _unit(theta)
        sense = 1 if (b[0] - a[0]) * ux + (b[1] - a[1]) * uy >= 0 else -1
        out.append((theta, nx * a[0] + ny * a[1], sense, math.dist(a, b)))
    return out


def _merge(walls, spike=20):
    """Consecutive parallel walls become one, where the longer of them puts it."""
    changed = True
    while changed and len(walls) > 1:
        changed = False
        for i in range(len(walls)):
            a, b = walls[i - 1], walls[i]
            if a is b or _gap(a.theta, b.theta) > 1e-6:
                continue
            if abs(a.d - b.d) > spike:
                # Far apart, they are two walls with a corner between them that was lost: leave
                # them for the caller to refuse.
                continue
            if a.sense != b.sense:
                # A strip out and back, thinner than a step: it closes into one line, which runs
                # the way the longer side ran.
                a.d = (a.d * a.length + b.d * b.length) / (a.length + b.length)
                a.sense = a.sense if a.length >= b.length else b.sense
                a.length = abs(a.length - b.length) or 1.0
                walls.pop(i)
                changed = True
                break
            w = a.length + b.length
            a.d, a.length = (a.d * a.length + b.d * b.length) / w, w
            walls.pop(i)
            changed = True
            break
    return walls


def _corners(walls):
    pts = []
    for i in range(len(walls)):
        p = walls[i - 1].point(walls[i])
        if p is None:
            return None
        pts.append(p)
    return pts


def _run(walls, corners, i):
    """How far the outline runs along wall i, in its own sense: negative when it doubles back."""
    (ux, uy), _ = _unit(walls[i].theta)
    a, b = corners[i], corners[(i + 1) % len(walls)]
    return walls[i].sense * ((b[0] - a[0]) * ux + (b[1] - a[1]) * uy)


def _steps_out(walls, jog):
    """Takes out every wall shorter than `jog`, shortest first: its two neighbours meet, or, when
    they are parallel, become one wall."""
    stuck = set()
    while len(walls) > 4:
        corners = _corners(walls)
        if corners is None:
            return None
        runs = [(_run(walls, corners, i), i) for i in range(len(walls)) if id(walls[i]) not in stuck]
        short = [(r, i) for r, i in runs if r < jog]
        if not short:
            return walls
        _, i = min(short)
        trial = walls[:i] + walls[i + 1:]
        trial = _merge(trial, jog)
        after = _corners(trial)
        # Two neighbours that are not quite parallel meet far away: that step is a real corner.
        if after is None or any(min(math.dist(p, c) for c in corners) > 4 * jog for p in after):
            stuck.add(id(walls[i]))
            continue
        walls = trial
    return walls


def _extent(wall, a, b):
    (ux, uy), _ = _unit(wall.theta)
    s, t = a[0] * ux + a[1] * uy, b[0] * ux + b[1] * uy
    return min(s, t), max(s, t)


def _area(pts):
    return abs(sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(pts, pts[1:] + pts[:1]))) / 2


def _inside(poly, x, y):
    hit = False
    for i in range(len(poly)):
        (ax, ay), (bx, by) = poly[i - 1], poly[i]
        if (ay > y) != (by > y) and x < ax + (y - ay) * (bx - ax) / (by - ay):
            hit = not hit
    return hit


def _moved(a, b, step=2):
    """How far one outline lies from the other on average: the floor only one of them covers,
    sampled every `step` units, over the length of the first one's outline."""
    xs = [p[0] for p in a + b]
    ys = [p[1] for p in a + b]
    both = either = 0
    y = min(ys) + step / 2
    while y < max(ys):
        x = min(xs) + step / 2
        while x < max(xs):
            ia, ib = _inside(a, x, y), _inside(b, x, y)
            both += ia and ib
            either += ia or ib
            x += step
        y += step
    perimeter = sum(math.dist(p, q) for p, q in zip(a, a[1:] + a[:1]))
    return (either - both) * step * step / perimeter


def _simple(pts):
    n = len(pts)

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    def touch(a, b, c, d):
        d1, d2, d3, d4 = cross(c, d, a), cross(c, d, b), cross(a, b, c), cross(a, b, d)
        if ((d1 > 0 > d2) or (d1 < 0 < d2)) and ((d3 > 0 > d4) or (d3 < 0 < d4)):
            return True

        def on(p, q, r):
            return min(p[0], q[0]) <= r[0] <= max(p[0], q[0]) and min(p[1], q[1]) <= r[1] <= max(p[1], q[1])
        return (d1 == 0 and on(c, d, a)) or (d2 == 0 and on(c, d, b)) or (d3 == 0 and on(a, b, c)) \
            or (d4 == 0 and on(a, b, d))

    for i in range(n):
        for j in range(i + 1, n):
            if j == i + 1 or (i == 0 and j == n - 1):
                continue
            if touch(pts[i], pts[(i + 1) % n], pts[j], pts[(j + 1) % n]):
                return False
    return n >= 3


def _tidy(pts):
    out = []
    for x, y in pts:
        p = (round(x), round(y))
        if not out or out[-1] != p:
            out.append(p)
    if len(out) > 1 and out[0] == out[-1]:
        out.pop()
    changed = True
    while changed and len(out) > 3:
        changed = False
        for i in range(len(out)):
            a, b, c = out[i - 1], out[i], out[(i + 1) % len(out)]
            if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) == 0:
                out.pop(i)
                changed = True
                break
    return out


def regularize(spaces, settings, anchors, frames=()):
    """Makes the drawn spaces regular in place. `spaces` are the floor's dicts with a "shape";
    `anchors` the codes whose walls do not move; `frames` [(Frame, matches(code))] for the wings
    that stand askew. Returns notes for the report."""
    jog, share = settings.get("jog", 20), settings.get("share", 8)
    notes, rooms = [], []
    frame_of = {sp["code"]: next((f for f, m in frames if m(sp["code"])), None) for sp in spaces}
    for sp in spaces:
        pts = [(p["x"], p["y"]) for p in sp["shape"]]
        frame = frame_of[sp["code"]]
        if frame:
            pts = [frame.into(p) for p in pts]
        fixed = sp["code"] in anchors
        if fixed:
            walls = _merge([Wall(*w) for w in _exact(pts)], 0)
        else:
            walls = _steps_out(_walls(_squared(pts), jog), jog)
        if not walls or len(walls) < 3:
            notes.append(f"  {sp['code']} kept as it was: nothing regular is left of it")
            continue
        rooms.append((sp, walls, fixed, pts))

    # Walls that two rooms share: parallel, close, side by side. Closest pairs first, and a group
    # never spreads wider than `share`, so a row of rooms does not creep across a corridor.
    every = []
    for sp, walls, fixed, _ in rooms:
        corners = _corners(walls)
        if corners is None:
            continue
        for i, w in enumerate(walls):
            lo, hi = _extent(w, corners[i], corners[(i + 1) % len(walls)])
            every.append((w, lo, hi, fixed))
    group = {id(w): [k] for k, (w, _, _, _) in enumerate(every)}
    pairs = []
    for i in range(len(every)):
        wi, li, hi_, _ = every[i]
        for j in range(i + 1, len(every)):
            wj, lj, hj, _ = every[j]
            if _gap(wi.theta, wj.theta) > 1e-6 or abs(wi.d - wj.d) > share:
                continue
            if min(hi_, hj) - max(li, lj) < -share:
                continue
            pairs.append((abs(wi.d - wj.d), i, j))
    for _, i, j in sorted(pairs):
        gi, gj = group[id(every[i][0])], group[id(every[j][0])]
        if gi is gj:
            continue
        ds = [every[k][0].d for k in gi + gj]
        held = {round(every[k][0].d, 3) for k in gi + gj if every[k][3]}
        if max(ds) - min(ds) > share or len(held) > 1:
            continue
        merged = gi + gj
        for k in merged:
            group[id(every[k][0])] = merged
    seen = set()
    for members in group.values():
        if id(members) in seen:
            continue
        seen.add(id(members))
        held = [every[k][0] for k in members if every[k][3]]
        if held:
            d = held[0].d
        else:
            total = sum(every[k][0].length for k in members)
            d = sum(every[k][0].d * every[k][0].length for k in members) / total
            if every[members[0]][0].theta in AXES:
                d = round(d)
        for k in members:
            every[k][0].d = d

    # Each room's new outline, as long as it stays a simple outline near the one drawn.
    shapes, walls_of = {}, {}
    for sp, walls, fixed, before in rooms:
        if fixed:
            shapes[sp["code"]] = list(before)
            continue
        corners = _corners(walls)
        shape = _tidy(corners) if corners else None
        if not shape or not _simple(shape) or _moved(before, shape) > share:
            notes.append(f"  {sp['code']} kept as it was: regular, it would fold or move too far")
            shapes[sp["code"]] = list(before)
            continue
        shapes[sp["code"]] = shape
        walls_of[sp["code"]] = walls
    drawn = {sp["code"]: list(before) for sp, _, _, before in rooms}

    # Two rooms the regular walls make overlap: one puts its wall on the other's, within a step;
    # failing that, the larger gives way; failing that, the one that moved more goes back to how
    # it was drawn. A change is taken only when it meets no room the old outline did not.
    drawn_apart = set()
    for _ in range(4 * len(shapes)):
        clash = next(((a, b) for a in shapes for b in shapes
                      if a < b and (a, b) not in drawn_apart and _overlap(shapes[a], shapes[b])), None)
        if clash is None:
            break
        a, b = clash
        mended = False
        for mover, other in ((a, b), (b, a)):
            if mover not in walls_of:
                continue
            onto = _onto(walls_of[mover], shapes[other], jog)
            if onto is None:
                continue
            corners = _corners(onto)
            shape = _tidy(corners) if corners else None
            if shape and _simple(shape) and _moved(drawn[mover], shape) <= share and \
                    _clear(shape, mover, other, shapes):
                shapes[mover], walls_of[mover] = shape, onto
                mended = True
                break
        if mended:
            continue
        # The larger room gives way: the smaller one is the more precise drawing.
        big, small = sorted(clash, key=lambda c: -_area(shapes[c]))
        if big in walls_of:
            cut = _cut(shapes[big], shapes[small])
            if cut and _simple(cut) and _area(cut) >= 0.85 * _area(shapes[big]) and \
                    _clear(cut, big, small, shapes):
                shapes[big] = cut
                continue
        back = max((c for c in clash if c in walls_of), key=lambda c: _moved(drawn[c], shapes[c]), default=None)
        if back is None:
            # Both as drawn, so they only meet where a wing's frame and the floor's part by a
            # rounding: nothing to mend.
            drawn_apart.add(clash)
            continue
        shapes[back] = drawn[back]
        del walls_of[back]
        notes.append(f"  {back} kept as it was: regular, it would overlap a neighbour")

    for sp, _, _, before in rooms:
        if sp["code"] not in walls_of:
            continue
        frame = frame_of[sp["code"]]
        shape = _tidy([frame.out_of(p) for p in shapes[sp["code"]]] if frame else shapes[sp["code"]])
        sp["shape"] = [{"x": x, "y": y} for x, y in shape]
        notes.append(f"  {sp['code']:<24} regular: {len(before)} -> {len(shape)} corners")
    return notes


def _clear(shape, code, other, shapes):
    """Whether a room's new outline clears `other` and meets no room the old one did not."""
    if _overlap(shape, shapes[other]):
        return False
    return not any(_overlap(shape, shapes[c]) and not _overlap(shapes[code], shapes[c])
                   for c in shapes if c not in (code, other))


def _cut(a, b):
    """Room `a` less room `b`, both squared: the largest piece left, squared too, or None. Worked on
    the grid of both outlines' own coordinates, so a squared outline is cut exactly."""
    xs = sorted({p[0] for p in a + b})
    ys = sorted({p[1] for p in a + b})
    cells = [(i, j) for j in range(len(ys) - 1) for i in range(len(xs) - 1)
             if _inside(a, (xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2)
             and not _inside(b, (xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2)]
    if not cells:
        return None
    kept, pieces = set(cells), []
    while kept:
        stack = [kept.pop()]
        piece = set(stack)
        while stack:
            i, j = stack.pop()
            for q in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)):
                if q in kept:
                    kept.remove(q)
                    piece.add(q)
                    stack.append(q)
        pieces.append(piece)
    best = max(pieces, key=lambda piece: sum((xs[i + 1] - xs[i]) * (ys[j + 1] - ys[j]) for i, j in piece))
    return _tidy([(xs[i], ys[j]) for i, j in trace.outline(best)])


def _onto(walls, other, jog):
    """The room's walls with each one that runs beside a wall of `other`, within `jog`, put on it:
    None when no wall moves."""
    moved, out = False, []
    for w in walls:
        w2 = Wall(w.theta, w.d, w.sense, w.length)
        for p, q in zip(other, other[1:] + other[:1]):
            phi = math.degrees(math.atan2(q[1] - p[1], q[0] - p[0])) % 180
            if _gap(phi, w.theta) > 1e-6:
                continue
            _, (nx, ny) = _unit(w.theta)
            d = nx * p[0] + ny * p[1]
            if 0 < abs(d - w.d) <= jog:
                w2.d, moved = d, True
                break
        out.append(w2)
    return out if moved else None


def _overlap(a, b, step=1):
    """Whether two outlines share more than a square unit of floor, as the editor's rule counts."""
    x0, x1 = max(min(p[0] for p in a), min(p[0] for p in b)), min(max(p[0] for p in a), max(p[0] for p in b))
    y0, y1 = max(min(p[1] for p in a), min(p[1] for p in b)), min(max(p[1] for p in a), max(p[1] for p in b))
    if x1 - x0 <= 0 or y1 - y0 <= 0:
        return False
    shared = 0
    y = y0 + step / 2
    while y < y1:
        x = x0 + step / 2
        while x < x1:
            if _inside(a, x, y) and _inside(b, x, y):
                shared += step * step
                if shared > 1:
                    return True
            x += step
        y += step
    return False


def matcher(patterns):
    return lambda code: any(fnmatch.fnmatch(code, p) for p in patterns)
