#!/usr/bin/env python3
"""
Reads what somebody drew by hand over a plan's photo: every room they outlined, as its outline in
the drawing's units.

    scripts/plan-tracing/strokes.py <photo> <spec.json>

An evacuation plan paints rooms it does not name and leaves out walls that are there. The person who
knows the place marks the photo up - a loop round each room, a colour for each kind, its name
written inside - and this turns the loops into rooms, so nobody copies them by eye.

A room is what its loop encloses: everything the photo's edge cannot reach without crossing the
loop's ink, less the ink of the loop itself. The name written inside in the same pen is then no
obstacle, and an arrow or a bracket hanging off the loop, thinner than the room, goes with the
ink. The room stops at the inner edge of the pen, so two rooms drawn side by side come out a wall
apart. A room closed by strokes of several loops - a stage between the hall's outline and the
line that parts it from the seats - is instead what a flood from a point inside it reaches
("as": "inside"), with the writing in it closed over. Its outline is
then drawn in the drawing's units with as few corners as follow the pen to within a hand's width:
a wall the pen meant level or plumb - within 9 degrees of the grid - is made so, a diagonal stays,
and the wobble of a hand goes.

spec: {"frame": "ec-au-p1-pe.json",       the trace spec of the same photo: its "corners", "plaque"
                                          and "resample" say where the drawing lies in the photo
       "out": "../traced/ec-p1-auditorio.json",
       "building": "EC", "floor": "P1", "why": "...",
       "rooms": [{
          "code": "P1-AUD-TAQUILLA",       the space; its other fields are copied to the output
          "name": "...", "typeCode": "...", "wing": "S", "aliases": [],
          "strokes": ["green"],            the colours of the strokes that close the room
          "at": [0.457, 0.170],            a point inside it, as fractions of the photo's width
                                           and height
          "as": "inside",                  for a room no single loop closes; "letters": 10 says
                                           how wide a stroke of writing to close over, in pixels
          "less": ["P1-AUD-ESCENARIO"],    rooms drawn inside this one, taken out of it a wall wide
          "within": [[0.43, 0.19], [0.48, 0.26]],
                                           the room is only what falls in this box of the photo:
                                           for a loop that another loop hangs off, like the
                                           arrows to a ticket office's windows
          "pen": 3,                        how wide this room's strokes are, in pixels (7)
          "level": 0,                      within how many degrees of the grid a wall is made
                                           level or plumb (9); 0 for a room whose walls slant or
                                           curve, which keeps them as the pen drew them
          "close": 3,                      how far apart two ends of a stroke may be and still
                                           close a loop, in pixels of a photo 2272 wide (3)
          "doors": [[0.42, 0.21]]          where "door" is written on its outline: a door there,
                                           on the nearest wall
       }],
       "given": {"file": "../traced/ec-p1.json", "codes": ["P1-AUD-ESCENARIO"]},
                                           rooms already traced from the plan itself, which the
                                           strokes do not redraw: they can be named under "less"
       "named": [{"code": "...", ...}]}    spaces the strokes do not redraw, with the fields the
                                           marks change: a name, other names; "shape": null for
                                           one the plan does not draw at all

Colours are the ink's, picked by hue: green, cyan, blue, navy, purple, orange, red, black.
Standard library only; sips (macOS) reads the photo.
"""
import colorsys
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trace as T  # noqa: E402

REFERENCE_WIDTH = 2272   # the photo the pixel sizes below were measured on
WALL = 4                 # pixels left between a room and one drawn inside it
DOOR = 34                # pixels a door is wide
PEN = 7                  # pixels a stroke is wide: how much of a loop's inside is its own ink
LETTER = 4               # half the width of a pen stroke of writing, in pixels: what is closed over
INKS = {
    "green": lambda h, s, v: 100 <= h < 150,
    "cyan": lambda h, s, v: 165 <= h < 195,
    "blue": lambda h, s, v: 196 <= h < 224,
    "navy": lambda h, s, v: 226 <= h < 255,
    "purple": lambda h, s, v: 270 <= h < 305,
    "orange": lambda h, s, v: 24 <= h < 52 and s > 0.8 and v > 0.8,
    "red": lambda h, s, v: (h < 8 or h > 352) and s > 0.85 and v > 0.8,
}


def ink(r, g, b):
    """Which pen a pixel is, or None: the printed plan's colours are duller than any of them."""
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    if v < 0.22 and max(r, g, b) - min(r, g, b) < 40:
        return "black"
    if s < 0.6 or v < 0.55:
        return None
    return next((name for name, is_it in INKS.items() if is_it(h * 360, s, v)), None)


def inside(mask, w, h, seed, code):
    """The pixels a flood from `seed` reaches without crossing the mask; the room's inside."""
    if mask[seed[1] * w + seed[0]]:
        sys.exit(f"{code}: its point {seed} is on a stroke, not inside the room")
    seen = bytearray(w * h)
    stack, cells = [seed], []
    while stack:
        x, y = stack.pop()
        i = y * w + x
        if seen[i] or mask[i]:
            continue
        if x in (0, w - 1) or y in (0, h - 1):
            sys.exit(f"{code}: its strokes do not close - the flood from {seed} reaches the photo's edge")
        seen[i] = 1
        cells.append((x, y))
        stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return cells


def enclosed(mask, w, h, seed, code):
    """What the loop through or round `seed` encloses, its own ink included: the piece, holding
    `seed`, of everything the photo's edge cannot reach without crossing the mask."""
    outside = bytearray(w * h)
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while stack:
        x, y = stack.pop()
        if x < 0 or x >= w or y < 0 or y >= h:
            continue
        i = y * w + x
        if outside[i] or mask[i]:
            continue
        outside[i] = 1
        stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    if outside[seed[1] * w + seed[0]]:
        sys.exit(f"{code}: its strokes do not close round {seed}")
    seen, stack, cells = set(), [seed], []
    while stack:
        x, y = stack.pop()
        if (x, y) in seen or outside[y * w + x]:
            continue
        seen.add((x, y))
        cells.append((x, y))
        stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return cells


def largest(cells):
    """The biggest connected piece: what is left of a room once another is taken out of it."""
    left, best = set(cells), []
    while left:
        stack, piece = [left.pop()], []
        while stack:
            x, y = stack.pop()
            piece.append((x, y))
            for q in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if q in left:
                    left.remove(q)
                    stack.append(q)
        if len(piece) > len(best):
            best = piece
    return best


def drawn(loop, eps=5.0, level=9.0, straight=10.0):
    """The outline with few corners: those that keep it within `eps` units of the pen, less the
    ones on a wall that runs on nearly `straight`, and with every wall within `level` degrees of
    the grid put on it."""
    n = len(loop)
    far = max(range(n), key=lambda i: (loop[i][0] - loop[0][0]) ** 2 + (loop[i][1] - loop[0][1]) ** 2)
    first, second = loop[:far + 1], loop[far:] + [loop[0]]
    pts = [first[i] for i in T.rdp(first, eps)][:-1] + [second[i] for i in T.rdp(second, eps)][:-1]

    def turn(a, b, c):
        u, v = (b[0] - a[0], b[1] - a[1]), (c[0] - b[0], c[1] - b[1])
        return abs(math.degrees(math.atan2(u[0] * v[1] - u[1] * v[0], u[0] * v[0] + u[1] * v[1])))
    changed = True
    while changed and len(pts) > 3:
        changed = False
        for i in range(len(pts)):
            if turn(pts[i - 1], pts[i], pts[(i + 1) % len(pts)]) < straight:
                del pts[i]
                changed = True
                break
    pts = [list(p) for p in pts]
    slope = math.tan(math.radians(level))
    for _ in range(3):
        for i in range(len(pts)):
            a, b = pts[i], pts[(i + 1) % len(pts)]
            dx, dy = b[0] - a[0], b[1] - a[1]
            if abs(dx) <= slope * abs(dy):
                a[0] = b[0] = (a[0] + b[0]) / 2
            elif abs(dy) <= slope * abs(dx):
                a[1] = b[1] = (a[1] + b[1]) / 2
    return [(p[0], p[1]) for p in pts]


def door_on(poly, at, width):
    """A door `width` long on the edge of `poly` nearest to `at`, centred where `at` falls on it."""
    best = None
    for i, a in enumerate(poly):
        b = poly[(i + 1) % len(poly)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy)
        if length < width:
            continue
        t = ((at[0] - a[0]) * dx + (at[1] - a[1]) * dy) / (length * length)
        t = max(width / 2 / length, min(1 - width / 2 / length, t))
        foot = (a[0] + t * dx, a[1] + t * dy)
        off = math.dist(at, foot)
        if best is None or off < best[0]:
            ux, uy = dx / length, dy / length
            best = (off, (foot[0] - ux * width / 2, foot[1] - uy * width / 2), (foot[0] + ux * width / 2, foot[1] + uy * width / 2))
    return best[1:] if best else None


def main():
    photo, spec_path = [a for a in sys.argv[1:] if not a.startswith('--')][:2]
    spec = json.load(open(spec_path))
    here = os.path.dirname(os.path.abspath(spec_path))
    frame = json.load(open(os.path.join(here, spec["frame"])))

    prefix = os.path.join(os.environ.get("TMPDIR", "/tmp"), "strokes-" + os.path.splitext(os.path.basename(spec_path))[0])
    w, h, rgb = T.read_bmp(T.as_bmp(photo, prefix, REFERENCE_WIDTH))
    k = w / REFERENCE_WIDTH
    # The frame's corners are where the drawing's four corners fall in the photo, resampled.
    pw, ph = frame["plaque"]
    to_drawing_matrix = T.homography(frame["corners"], [(0, 0), (pw, 0), (pw, ph), (0, ph)])
    zoom = frame.get("resample", 2800) / w

    def to_drawing(p):
        x, y = p[0] * zoom, p[1] * zoom
        m = to_drawing_matrix
        d = m[2][0] * x + m[2][1] * y + m[2][2]
        return ((m[0][0] * x + m[0][1] * y + m[0][2]) / d, (m[1][0] * x + m[1][1] * y + m[1][2]) / d)

    pens = {}
    for y in range(h):
        for x in range(w):
            name = ink(*rgb(x, y))
            if name:
                pens.setdefault(name, bytearray(w * h))[y * w + x] = 1

    regions = {}
    # Rooms the plan's own tracing gave, laid back on the photo, for others to make room for.
    given = spec.get("given")
    if given:
        to_photo_matrix = T.homography([(0, 0), (pw, 0), (pw, ph), (0, ph)], frame["corners"])

        def to_photo(p):
            m = to_photo_matrix
            d = m[2][0] * p[0] + m[2][1] * p[1] + m[2][2]
            return ((m[0][0] * p[0] + m[0][1] * p[1] + m[0][2]) / d / zoom, (m[1][0] * p[0] + m[1][1] * p[1] + m[1][2]) / d / zoom)
        traced = {sp["code"]: sp for sp in json.load(open(os.path.join(here, given["file"])))["spaces"]}
        for code in given["codes"]:
            poly = [to_photo((p["x"], p["y"])) for p in traced[code]["shape"]]
            ys = [p[1] for p in poly]
            cells = []
            for y in range(max(0, int(min(ys))), min(h, int(max(ys)) + 1)):
                xs = sorted(a[0] + (y + 0.5 - a[1]) * (b[0] - a[0]) / (b[1] - a[1])
                            for a, b in zip(poly, poly[1:] + poly[:1]) if (a[1] > y + 0.5) != (b[1] > y + 0.5))
                for x0, x1 in zip(xs[0::2], xs[1::2]):
                    cells += [(x, y) for x in range(max(0, math.ceil(x0 - 0.5)), min(w, math.floor(x1 - 0.5) + 1))]
            regions[code] = cells
    for room in spec["rooms"]:
        mask = bytearray(w * h)
        for colour in room["strokes"]:
            if colour not in pens:
                sys.exit(f"{room['code']}: nothing on the photo is drawn in {colour}")
            for i, v in enumerate(pens[colour]):
                if v:
                    mask[i] = 1
        close = max(1, round(room.get("close", 3) * k))
        mask = T.dilate(mask, w, h, close)
        seed = (round(room["at"][0] * w), round(room["at"][1] * h))
        whole = bytearray(w * h)
        if room.get("as") == "inside":
            for x, y in inside(mask, w, h, seed, room["code"]):
                whole[y * w + x] = 1
            # The writing inside is ink too: what it cut out of the room is closed over.
            reach = max(2, round(room.get("letters", LETTER) * k))
            whole = T.erode(T.dilate(whole, w, h, reach), w, h, reach)
        else:
            for x, y in enclosed(mask, w, h, seed, room["code"]):
                whole[y * w + x] = 1
            # Less the loop's own ink, and with it whatever thinner than the pen hangs off it.
            whole = T.erode(whole, w, h, close + max(1, round(room.get("pen", PEN) * k)))
        cells = [(i % w, i // w) for i, v in enumerate(whole) if v]
        if "within" in room:
            (u0, v0), (u1, v1) = room["within"]
            cells = [(x, y) for x, y in cells if u0 * w <= x <= u1 * w and v0 * h <= y <= v1 * h]
        if not cells:
            sys.exit(f"{room['code']}: nothing is left inside its strokes")
        regions[room["code"]] = largest(cells)

    spaces = []
    for room in spec["rooms"]:
        cells = regions[room["code"]]
        if room.get("less"):
            gone = bytearray(w * h)
            for other in room["less"]:
                for x, y in regions[other]:
                    gone[y * w + x] = 1
            gap = max(1, round(WALL * k))
            gone = T.dilate(gone, w, h, gap)
            left = bytearray(w * h)
            for x, y in cells:
                if not gone[y * w + x]:
                    left[y * w + x] = 1
            # What is left along the edges of the room taken out, thinner than a wall, goes too.
            left = T.dilate(T.erode(left, w, h, gap), w, h, gap)
            cells = largest([(i % w, i // w) for i, v in enumerate(left) if v and not gone[i]])
        fitted = drawn([to_drawing(p) for p in T.outline(cells)], level=room.get("level", 9.0))
        if len(fitted) < 3 or not T.simple(fitted):
            if "--draft" not in sys.argv:
                sys.exit(f"{room['code']}: its outline crosses itself once drawn with few corners")
            print(f"  {room['code']}: crosses itself")
        shape = []
        for x, y in fitted:
            p = {"x": round(x), "y": round(y)}
            if not shape or shape[-1] != p:
                shape.append(p)
        space = {key: room[key] for key in ("code", "wing", "name", "typeCode", "aliases") if key in room}
        space["shape"] = shape
        doors = []
        for at in room.get("doors", []):
            door = door_on([(p["x"], p["y"]) for p in shape],
                           to_drawing((at[0] * w, at[1] * h)),
                           math.dist(to_drawing((0, 0)), to_drawing((DOOR * k, 0))))
            if door is None:
                sys.exit(f"{room['code']}: no wall long enough for the door at {at}")
            doors.append({"from": {"x": round(door[0][0]), "y": round(door[0][1])}, "to": {"x": round(door[1][0]), "y": round(door[1][1])}})
        if doors:
            space["doors"] = doors
        spaces.append(space)
        xs = [p["x"] for p in shape]
        ys = [p["y"] for p in shape]
        print(f"  {room['code']:<24} {len(shape):>2} corners  x {min(xs):>5}-{max(xs):<5} y {min(ys):>5}-{max(ys):<5}  {len(doors)} door(s)")

    spaces += spec.get("named", [])
    out = os.path.normpath(os.path.join(here, spec["out"]))
    lines = ["{", f' "building": {json.dumps(spec["building"])},', f' "floor": {json.dumps(spec["floor"])},',
             f' "why": {json.dumps(spec["why"], ensure_ascii=False)},', ' "spaces": [']
    lines.append(",\n".join("  " + json.dumps(space, ensure_ascii=False) for space in spaces))
    lines += [" ]", "}"]
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {len(spaces)} space(s) to {out}")


if __name__ == "__main__":
    main()
