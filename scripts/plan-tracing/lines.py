#!/usr/bin/env python3
"""
Draws the rooms of a line plan on its own walls: the sanitary route plans (RS), printed in black
like the architect's drawing, with no fill for trace.py to follow.

    scripts/plan-tracing/lines.py <spec.json> <out prefix>            snap the spec's rooms
    scripts/plan-tracing/lines.py <spec.json> <out prefix> propose    suggest rooms to start from

The spec is trace.py's - "photo", "resample", and "corners" with "plaque" (or "pairs", four or more
[[x, y] in the photo, [x, y] on the plan], with "size": [w, h]) - and the rooms it knows roughly:

    "rooms":  [{"shape": [[x, y], ...], "doors": [[[x, y], [x, y]], ...]}, ...]
    "stairs": [[[x, y], ...], ...]
    "reach":  how far an edge may move to find its wall (default 14)
    "door":   [narrowest, widest] opening that is a door (default [22, 70])
    "place":  [dx, dy, width, height]: where the plan's frame lies on a bigger floor, for the output
    "ink":    how dark, against its paper, a line must be to count (default 0.72)

A room may say "fixed" (drawn as given, not snapped) or "closed" (no doors looked for), and brings
the doors a plan draws as a leaf in the wall line rather than as a gap.

Each level or plumb edge of a room moves onto the face of the wall beside it: the line nearest the
room among those that run most of the edge's length. Then each gap in that line a door wide is a
door, besides the doors the spec gives. A line plan's walls are drawn twice, one line per face, so
two rooms sharing a wall stay a wall's thickness apart, as they are on the plan.

Writes:
    <prefix>.rect.bmp          the plan, rectified
    <prefix>.json              rooms with their doors, and stairs, as trace.py writes them: build-floor.py
                               reads it
    <prefix>.check.jpg         the check, on the plan itself: every outline green where a wall lies
                               under it and red where none does, doors cyan, stairs grey, rooms numbered
and prints, for every room, how much of its outline lies on a wall, doors aside. "propose" writes
<prefix>.proposed.json and .proposed.check.jpg instead: the white areas the walls close, gaps a door
wide sealed, as boxes to start "rooms" from. "level" measures the four wall lines the spec lists and
prints the corners that make them level and plumb.

Standard library only, like trace.py, whose rectification it uses.
"""
import importlib.util
import json
import os
import subprocess
import sys

# The tracer next to this file, by path: the standard library has a module called "trace" too.
_spec = importlib.util.spec_from_file_location("plan_trace", os.path.join(os.path.dirname(os.path.abspath(__file__)), "trace.py"))
trace = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(trace)


# ── The plan ─────────────────────────────────────────────────────────────────────────────────

def to_photo(spec):
    """The homography from the plan's units to the photo's pixels, and the plan's size.

    "corners" are a rectangle of the building - four wall lines, measured - and "plaque" its size in
    plan units; "frame": [u0, v0, u1, v1] is the part of the plan to keep, in the same units, and may
    reach past that rectangle: the plan's (0, 0) is then (u0, v0). "pairs" instead lay four points of
    the photo on points of a plan already drawn, of "size" [w, h]: how the floors of a building that
    share walls are put in one frame."""
    if "pairs" in spec:
        pairs = spec["pairs"][:4]
        return trace.homography([p[1] for p in pairs], [p[0] for p in pairs]), tuple(spec["size"])
    pw, ph = spec["plaque"]
    u0, v0, u1, v1 = spec.get("frame", [0, 0, pw, ph])
    hm = trace.homography([(-u0, -v0), (pw - u0, -v0), (pw - u0, ph - v0), (-u0, ph - v0)], spec["corners"])
    return hm, (int(u1 - u0), int(v1 - v0))


def rectify(spec, spec_path, prefix):
    """The plan as the spec frames it, as rows of (r, g, b)."""
    photo = trace.survey_file(spec_path, spec["photo"])
    _, _, rgb = trace.read_bmp(trace.as_bmp(photo, prefix, spec.get("resample", 2800)))
    hm, (w, h) = to_photo(spec)
    img = []
    for v in range(h):
        for u in range(w):
            z = hm[2][0] * u + hm[2][1] * v + hm[2][2]
            img.append(rgb((hm[0][0] * u + hm[0][1] * v + hm[0][2]) / z,
                           (hm[1][0] * u + hm[1][1] * v + hm[1][2]) / z))
    trace.write_bmp(prefix + ".rect.bmp", img, w, h)
    return img, w, h


def ink(img, w, h, block=24, dark=0.72):
    """The lines of the drawing: pixels much darker than the paper around them. The paper is measured
    block by block, since a photographed plan is never evenly lit. Black and grey lines count, and
    red - some plans draw new walls red; the blue and green of the waste routes do not."""
    bw, bh = (w + block - 1) // block, (h + block - 1) // block
    paper = []
    for by in range(bh):
        for bx in range(bw):
            values = sorted(sum(img[y * w + x]) / 3 for y in range(by * block, min(h, (by + 1) * block), 3)
                            for x in range(bx * block, min(w, (bx + 1) * block), 3))
            paper.append(values[int(len(values) * 0.85)] if values else 255)
    # A block full of lines - stair treads, a label - takes the brightest paper next to it.
    around = [max(paper[j * bw + i] for j in range(max(0, by - 1), min(bh, by + 2))
                  for i in range(max(0, bx - 1), min(bw, bx + 2)))
              for by in range(bh) for bx in range(bw)]
    mask = bytearray(w * h)
    for y in range(h):
        row = (y // block) * bw
        for x in range(w):
            r, g, b = img[y * w + x]
            if (r + g + b) / 3 < around[row + x // block] * dark:
                if r > g + 30 and r > b + 30:       # a dim red is still red, not grey
                    mask[y * w + x] = RED
                elif max(r, g, b) - min(r, g, b) < 70:
                    mask[y * w + x] = 1
    return mask


RED = 2                                         # ink that is red: door swings, labels, some new walls


# ── Snapping ─────────────────────────────────────────────────────────────────────────────────

def covered(mask, w, h, axis, at, lo, hi, width=1, grey=False):
    """For each step along a level (axis 'y') or plumb (axis 'x') line from lo to hi: is there ink
    within `width` of it - only black or grey ink, with `grey`."""
    out = []
    for t in range(int(lo), int(hi) + 1):
        hit = 0
        for d in range(-width, width + 1):
            x, y = (t, at + d) if axis == "y" else (at + d, t)
            if 0 <= x < w and 0 <= y < h and mask[int(y) * w + int(x)] and \
                    not (grey and mask[int(y) * w + int(x)] == RED):
                hit = 1
                break
        out.append(hit)
    return out


def snap_edge(mask, w, h, axis, at, lo, hi, inward, reach):
    """Where a level or plumb edge meets its wall: among the lines within `reach` that run most of
    its length, the one nearest the room. `inward` is +1 when the room lies at greater coordinates."""
    margin = max(3, (hi - lo) // 10)                # the corners belong to the walls across
    scores = {}
    for d in range(-reach, reach + 1):
        line = covered(mask, w, h, axis, at + d, lo + margin, hi - margin, width=0)
        scores[d] = sum(line) / max(1, len(line))
    best = max(scores.values())
    if best < 0.35:
        return at, best                             # no wall here: the edge stays
    good = [d for d, s in scores.items() if s >= max(0.35, 0.7 * best)
            and s >= scores.get(d - 1, 0) and s >= scores.get(d + 1, 0)]
    d = max(good, key=lambda d: d * inward)         # the one nearest the room
    return at + d, scores[d]


def snap(mask, w, h, poly, reach):
    """The room with each level or plumb edge on its wall; a slanted edge stays where it is."""
    n = len(poly)
    lines = []
    for i in range(n):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % n]
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        if y0 == y1:
            inward = 1 if trace.inside(poly, mx, my + 1.5) else -1
            at, _ = snap_edge(mask, w, h, "y", y0, min(x0, x1), max(x0, x1), inward, reach)
            lines.append(("y", at))
        elif x0 == x1:
            inward = 1 if trace.inside(poly, mx + 1.5, my) else -1
            at, _ = snap_edge(mask, w, h, "x", x0, min(y0, y1), max(y0, y1), inward, reach)
            lines.append(("x", at))
        else:
            lines.append(None)
    out = []
    for i in range(n):
        before, after = lines[i - 1], lines[i]
        x, y = poly[i]
        for line in (before, after):
            if line and line[0] == "x":
                x = line[1]
            elif line:
                y = line[1]
        out.append([x, y])
    return out


def doors_on(mask, w, h, poly, narrowest, widest):
    """Openings in the walls under a room's outline, a door wide, as [[x, y], [x, y]] on it."""
    found = []
    n = len(poly)
    for i in range(n):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % n]
        if x0 != x1 and y0 != y1:
            continue
        axis, at, lo, hi = ("y", y0, min(x0, x1), max(x0, x1)) if y0 == y1 else ("x", x0, min(y0, y1), max(y0, y1))
        line = covered(mask, w, h, axis, at, lo, hi, width=1)
        t = 0
        while t < len(line):
            if line[t]:
                t += 1
                continue
            start = t
            while t < len(line) and not line[t]:
                t += 1
            if narrowest <= t - start <= widest:
                a, b = lo + start, lo + t - 1
                if not boxed(mask, w, h, poly, axis, at, a, b):
                    found.append([[a, at], [b, at]] if axis == "y" else [[at, a], [at, b]])
    return found


def boxed(mask, w, h, poly, axis, at, a, b, depth=22):
    """Whether a gap in a wall is the inside of a column or a duct drawn in the wall - closed, a
    wall's thickness beyond the room, by a line as long as the gap - rather than a way through. An
    open door's leaf beside the gap runs across it, not along it, and its swing is red: neither
    closes it."""
    probe = ((a + b) / 2, at + 1.5) if axis == "y" else (at + 1.5, (a + b) / 2)
    out = -1 if trace.inside(poly, *probe) else 1
    for d in range(4, depth + 1):
        line = covered(mask, w, h, axis, at + out * d, a + 2, b - 2, width=0, grey=True)
        if sum(line) >= 0.8 * len(line):
            return True
    return False


def onto(poly, door):
    """A door the spec gives, moved onto the nearest edge of the room as snapped: the wall it was
    drawn on may have moved a few units to meet its line."""
    mid = ((door[0][0] + door[1][0]) / 2, (door[0][1] + door[1][1]) / 2)
    n = len(poly)
    a, b = min(((poly[i], poly[(i + 1) % n]) for i in range(n)),
               key=lambda e: trace.segment_distance(mid, e[0], e[1])[0])
    out = []
    for p in door:
        _, t = trace.segment_distance(p, a, b)
        out.append([round(a[0] + t * (b[0] - a[0])), round(a[1] + t * (b[1] - a[1]))])
    return out


def support(mask, w, h, poly, doors):
    """How much of a room's outline lies on ink, the doors left out; and the stretches that do not."""
    on = total = 0
    bare = []
    n = len(poly)
    for i in range(n):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % n]
        length = max(abs(x1 - x0), abs(y1 - y0))
        run = None
        for t in range(int(length) + 1):
            x = x0 + (x1 - x0) * t / max(1, length)
            y = y0 + (y1 - y0) * t / max(1, length)
            if any(trace.segment_distance((x, y), a, b)[0] <= 1.5 for a, b in doors):
                continue
            total += 1
            hit = any(0 <= int(x) + dx < w and 0 <= int(y) + dy < h and mask[(int(y) + dy) * w + int(x) + dx]
                      for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            on += hit
            if not hit:
                run = run or [x, y]
                bare.append((x, y))
    return on / max(1, total), bare


# ── Proposals ────────────────────────────────────────────────────────────────────────────────

def propose(mask, w, h, seal, smallest):
    """The white areas the walls close, with gaps up to `seal` twice over closed first, as boxes."""
    closed = trace.erode(trace.dilate(mask, w, h, seal), w, h, seal)
    rooms = []
    for piece in trace.components(closed, w, h, 0):
        if len(piece) < smallest:
            continue
        xs = [p % w for p in piece]; ys = [p // w for p in piece]
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        if x0 == 0 or y0 == 0 or x1 == w - 1 or y1 == h - 1:
            continue                                # the paper around the building
        fill = len(piece) / ((x1 - x0 + 1) * (y1 - y0 + 1))
        rooms.append({"shape": [[x0, y0], [x1, y0], [x1, y1], [x0, y1]], "fill": round(fill, 2)})
    rooms.sort(key=lambda r: (r["shape"][0][1] // 40, r["shape"][0][0]))
    return rooms


# ── Pictures ─────────────────────────────────────────────────────────────────────────────────

DIGITS = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
          "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001001001001",
          "8": "111101111101111", "9": "111101111001111", "E": "111100111100111"}


def picture(prefix, img, w, h, mask, rooms, stairs):
    """The check, drawn on the plan itself: each outline green where a wall is under it and red where
    none is, doors cyan, stairs grey, each room numbered as the report numbers it."""
    out = list(img)
    green, red, cyan, grey = (22, 163, 74), (220, 38, 38), (6, 182, 212), (71, 85, 105)

    def put(x, y, colour):
        if 0 <= x < w and 0 <= y < h:
            out[y * w + x] = colour

    def on_ink(x, y):
        return any(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx]
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1))

    def segment(a, b, colour, thick, dashed=False):
        length = max(abs(b[0] - a[0]), abs(b[1] - a[1]), 1)
        for t in range(int(length) + 1):
            if dashed and (t // 6) % 2:
                continue
            x = round(a[0] + (b[0] - a[0]) * t / length); y = round(a[1] + (b[1] - a[1]) * t / length)
            c = colour or (green if on_ink(x, y) else red)
            for d in range(thick):                  # thickened across the line, never along it
                if a[1] == b[1]:
                    put(x, y + d, c)
                else:
                    put(x + d, y, c)

    def label(text, cx, cy):
        size = 3
        width = len(text) * 4 * size
        for y in range(int(cy) - 3 * size, int(cy) + 3 * size + 1):
            for x in range(int(cx) - width // 2 - size, int(cx) + width // 2 + 1):
                put(x, y, (255, 255, 255))
        for k, ch in enumerate(text):
            glyph = DIGITS.get(ch, DIGITS["0"])
            for j in range(5):
                for i in range(3):
                    if glyph[j * 3 + i] == "1":
                        for dy in range(size):
                            for dx in range(size):
                                put(int(cx) - width // 2 + k * 4 * size + i * size + dx,
                                    int(cy) - 2 * size + j * size + dy, (0, 0, 0))

    for k, room in enumerate(rooms):
        poly = room["shape"]
        for i in range(len(poly)):
            segment(poly[i], poly[(i + 1) % len(poly)], None, 2)
        for a, b in room.get("doors", []):
            segment(a, b, cyan, 4)
    for s in stairs:
        for i in range(len(s)):
            segment(s[i], s[(i + 1) % len(s)], grey, 2, dashed=True)
    for k, room in enumerate(rooms):
        xs = [p[0] for p in room["shape"]]; ys = [p[1] for p in room["shape"]]
        label(str(k), (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    for k, s in enumerate(stairs):
        xs = [p[0] for p in s]; ys = [p[1] for p in s]
        label(f"E{k}", (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    trace.write_bmp(prefix + ".check.bmp", out, w, h)
    subprocess.run(["sips", "-s", "format", "jpeg", prefix + ".check.bmp", "--out", prefix + ".check.jpg"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    os.remove(prefix + ".check.bmp")


def fit_line(mask, w, h, axis, at, lo, hi, band):
    """The ink line running near `at` from lo to hi, as (position at lo, position at hi): the ink
    nearest the guess at every few steps, fitted, the strays dropped and fitted again."""
    def sample(guess, reach):
        found = []
        for t in range(int(lo), int(hi) + 1, 3):
            centre = guess(t)
            for d in sorted(range(-reach, reach + 1), key=abs):
                x, y = (t, round(centre + d)) if axis == "y" else (round(centre + d), t)
                if 0 <= x < w and 0 <= y < h and mask[y * w + x]:
                    found.append((t, centre + d))
                    break
        return found

    def regress(samples):
        for _ in range(3):
            n = len(samples)
            mt = sum(t for t, _ in samples) / n; ma = sum(a for _, a in samples) / n
            slope = sum((t - mt) * (a - ma) for t, a in samples) / max(1e-9, sum((t - mt) ** 2 for t, _ in samples))
            kept = [(t, a) for t, a in samples if abs(a - (ma + slope * (t - mt))) <= 3]
            samples = kept if len(kept) > n // 3 else samples
        return lambda t: ma + slope * (t - mt)

    line = regress(sample(lambda t: at, band))
    for _ in range(2):                              # again, close to the line found, for one face
        line = regress(sample(line, 5))
    return line(lo), line(hi)


def level(spec, mask, w, h):
    """The corners, measured again: "level" lists the rectangle's four wall lines as they lie on the
    plan now - top, right, bottom, left, each [at, lo, hi, band] - and each is fitted to its ink and
    carried back to the photo. Where they cross are the corners that make them level and plumb."""
    hm, _ = to_photo(spec)

    def photo(u, v):
        z = hm[2][0] * u + hm[2][1] * v + hm[2][2]
        return ((hm[0][0] * u + hm[0][1] * v + hm[0][2]) / z, (hm[1][0] * u + hm[1][1] * v + hm[1][2]) / z)

    lines = []
    for axis, (at, lo, hi, band) in zip("yxyx", spec["level"]):
        a, b = fit_line(mask, w, h, axis, at, lo, hi, band)
        ends = [(lo, a), (hi, b)] if axis == "y" else [(a, lo), (b, hi)]
        print(f"  {axis} = {a:.1f} .. {b:.1f}  ({'tilted' if abs(a - b) > 1 else 'straight'})")
        lines.append([photo(*p) for p in ends])

    def cross(l1, l2):
        (x1, y1), (x2, y2) = l1
        (x3, y3), (x4, y4) = l2
        d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d
        return [round(x1 + t * (x2 - x1), 1), round(y1 + t * (y2 - y1), 1)]

    top, right, bottom, left = lines
    corners = [cross(top, left), cross(top, right), cross(bottom, right), cross(bottom, left)]
    print(f'  "corners": {json.dumps(corners)}')


def main():
    spec_path, prefix = sys.argv[1:3]
    spec = json.load(open(spec_path))
    img, w, h = rectify(spec, spec_path, prefix)
    # A plan printed in pale grey - floor 6 of the Edificio Central - has walls only a fifth darker
    # than its paper: "ink" sets how much darker a line must be (0.72 of the paper by default).
    mask = ink(img, w, h, dark=spec.get("ink", 0.72))

    if len(sys.argv) > 3 and sys.argv[3] == "level":
        level(spec, mask, w, h)
        return

    if len(sys.argv) > 3 and sys.argv[3] == "propose":
        rooms = propose(mask, w, h, spec.get("seal", 18), spec.get("smallest", 1200))
        json.dump(rooms, open(prefix + ".proposed.json", "w"))
        picture(prefix + ".proposed", img, w, h, mask, rooms, [])
        print(f"{w}x{h}: {len(rooms)} white areas closed by walls")
        return

    reach = spec.get("reach", 14)
    narrowest, widest = spec.get("door", [22, 70])
    rooms = []
    print(f"{w}x{h}")
    for k, room in enumerate(spec.get("rooms", [])):
        shape = [list(p) for p in room["shape"]]
        if not room.get("fixed"):
            shape = snap(mask, w, h, shape, reach)
        found = [] if room.get("closed") else doors_on(mask, w, h, shape, narrowest, widest)
        doors = [onto(shape, d) for d in room.get("doors", [])] + found
        share, _ = support(mask, w, h, shape, doors)
        rooms.append({"shape": shape, "doors": doors})
        flag = "" if share >= 0.9 else "   <- check"
        print(f"  {k:3d} {share:5.0%} on walls, {len(doors)} door(s){flag}")
    stairs = [[list(p) for p in s] for s in spec.get("stairs", [])]
    # "place": [dx, dy, width, height] puts what was drawn on a floor bigger than the photo shows:
    # the Edificio Central's plans draw its central wing, and its other wings go around it later.
    dx, dy, fw, fh = spec.get("place", [0, 0, w, h])

    def moved(poly):
        return [[x + dx, y + dy] for x, y in poly]
    json.dump({"width": fw, "height": fh,
               "rooms": [{"shape": moved(r["shape"]), "doors": [moved(d) for d in r["doors"]]} for r in rooms],
               "stairs": [{"shape": moved(s)} for s in stairs]},
              open(prefix + ".json", "w"))
    json.dump({"width": w, "height": h, "rooms": rooms, "stairs": [{"shape": s} for s in stairs]},
              open(prefix + ".local.json", "w"))            # in the plan's own frame, for checking
    picture(prefix, img, w, h, mask, rooms, stairs)


if __name__ == "__main__":
    main()
