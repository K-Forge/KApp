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
ink. A room closed by strokes of several loops - a stage between the hall's outline and the
line that parts it from the seats - is instead what a flood from a point inside it reaches
("as": "inside"), with the writing in it closed over.

The rooms are then put on one sheet. Each reaches the middle of its own pen, and two rooms drawn
side by side - one wall, drawn once for each - are grown until they meet in the middle of it.
A wall between two rooms is drawn once, with as few corners as follow the pen to within a hand's
width, and both rooms take it: they share it corner for corner, as two rooms share a wall. A wall
the pen meant level or plumb - within 9 degrees of the grid - is made so for every room on it.

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
          "parts": [{"strokes": [...], "at": [...]}, ...], "join": 16,
                                           a room marked as several loops that are one room:
                                           each part read as a room is, then joined across gaps
                                           up to twice "join" pixels wide
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
GROW = 6                 # pixels from the inner edge of a loop to the middle of its pen
MEET = 14                # how far a room is grown to meet one drawn beside it: half the widest wall
EPS = 3.0                # pixels an outline may stray from the pen once drawn with few corners
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
    def region_of(room, code):
        """What one loop, or one flood, gives: the cells of the photo that are the room."""
        mask = bytearray(w * h)
        for colour in room["strokes"]:
            if colour not in pens:
                sys.exit(f"{code}: nothing on the photo is drawn in {colour}")
            for i, v in enumerate(pens[colour]):
                if v:
                    mask[i] = 1
        close = max(1, round(room.get("close", 3) * k))
        mask = T.dilate(mask, w, h, close)
        seed = (round(room["at"][0] * w), round(room["at"][1] * h))
        whole = bytearray(w * h)
        if room.get("as") == "inside":
            for x, y in inside(mask, w, h, seed, code):
                whole[y * w + x] = 1
            # The writing inside is ink too: what it cut out of the room is closed over.
            reach = max(2, round(room.get("letters", LETTER) * k))
            whole = T.erode(T.dilate(whole, w, h, reach), w, h, reach)
        else:
            for x, y in enclosed(mask, w, h, seed, code):
                whole[y * w + x] = 1
            # Less the loop's own ink, and with it whatever thinner than the pen hangs off it.
            whole = T.erode(whole, w, h, close + max(1, round(room.get("pen", PEN) * k)))
        cells = [(i % w, i // w) for i, v in enumerate(whole) if v]
        if "within" in room:
            (u0, v0), (u1, v1) = room["within"]
            cells = [(x, y) for x, y in cells if u0 * w <= x <= u1 * w and v0 * h <= y <= v1 * h]
        if not cells:
            sys.exit(f"{code}: nothing is left inside its strokes")
        return largest(cells)

    for room in spec["rooms"]:
        if "parts" not in room:
            regions[room["code"]] = region_of(room, room["code"])
            continue
        # A room marked as several loops that are one room: joined across what parts them.
        whole = bytearray(w * h)
        for part in room["parts"]:
            for x, y in region_of(part, room["code"]):
                whole[y * w + x] = 1
        reach = max(2, round(room.get("join", 12) * k))
        whole = T.erode(T.dilate(whole, w, h, reach), w, h, reach)
        regions[room["code"]] = largest([(i % w, i // w) for i, v in enumerate(whole) if v])

    for room in spec["rooms"]:
        if not room.get("less"):
            continue
        gone = bytearray(w * h)
        for other in room["less"]:
            for x, y in regions[other]:
                gone[y * w + x] = 1
        gap = max(1, round(WALL * k))
        gone = T.dilate(gone, w, h, gap)
        left = bytearray(w * h)
        for x, y in regions[room["code"]]:
            if not gone[y * w + x]:
                left[y * w + x] = 1
        # What is left along the edges of the room taken out, thinner than a wall, goes too.
        left = T.erode(left, w, h, gap)
        left = T.dilate(left, w, h, gap)
        regions[room["code"]] = largest([(i % w, i // w) for i, v in enumerate(left) if v and not gone[i]])

    # Every room on one sheet. Each is grown to the middle of its own pen, and two rooms whose
    # strokes run side by side - one wall, drawn twice - are grown on until they meet in its middle:
    # they share that wall then, corner for corner, instead of standing a gap apart.
    order = list(regions)
    lab = [0] * (w * h)
    for number, code in enumerate(order, 1):
        for x, y in regions[code]:
            if not lab[y * w + x]:
                lab[y * w + x] = number
    own = max(1, round(GROW * k))
    reach = max(own, round(MEET * k))
    near, far = lab[:], bytearray(w * h)
    cover = bytearray(w * h)
    members = {}
    for i, v in enumerate(lab):
        if v:
            members.setdefault(v, []).append(i)
    for number in range(1, len(order) + 1):
        front = members.get(number, [])
        seen = set(front)
        for step in range(reach):
            grown = []
            for i in front:
                x, y = i % w, i // w
                for j in (i - 1, i + 1, i - w, i + w, i - w - 1, i - w + 1, i + w - 1, i + w + 1):
                    if 0 <= j < w * h and abs(j % w - x) <= 1 and j not in seen and lab[j] != number:
                        seen.add(j)
                        grown.append(j)
                        cover[j] += 1
                        if not lab[j] and (not near[j] or step + 1 < far[j]):
                            near[j], far[j] = number, step + 1
            front = grown
    for i in range(w * h):
        if not lab[i] and near[i] and (far[i] <= own or cover[i] >= 2):
            lab[i] = near[i]
    cells_of = {}
    for i, v in enumerate(lab):
        if v:
            cells_of.setdefault(v, []).append((i % w, i // w))

    def at_corner(x, y):
        """How many rooms, the outside counted, meet at a corner of the lattice."""
        around = {lab[yy * w + xx] if 0 <= xx < w and 0 <= yy < h else 0
                  for xx, yy in ((x - 1, y - 1), (x, y - 1), (x - 1, y), (x, y))}
        return len(around)

    # A wall between two rooms is drawn once, between the corners where a third room or the
    # outside begins, and both rooms take it: so no two rooms can come to overlap by a sliver.
    walls, corners = {}, {}
    outlines = {}
    for number, code in enumerate(order, 1):
        loop = T.outline(largest(cells_of[number]))
        stops = [i for i, p in enumerate(loop) if at_corner(*p) >= 3]
        if not stops:
            far_i = max(range(len(loop)), key=lambda i: (loop[i][0] - loop[0][0]) ** 2 + (loop[i][1] - loop[0][1]) ** 2)
            first, second = loop[:far_i + 1], loop[far_i:] + [loop[0]]
            outlines[code] = [first[i] for i in T.rdp(first, EPS * k)][:-1] + [second[i] for i in T.rdp(second, EPS * k)][:-1]
            continue
        points = []
        for a, b in zip(stops, stops[1:] + [stops[0] + len(loop)]):
            chain = tuple(loop[i % len(loop)] for i in range(a, b + 1))
            key = min(chain, chain[::-1])
            if key not in walls:
                walls[key] = [key[i] for i in T.rdp(list(key), EPS * k)]
            points += (walls[key] if chain == key else walls[key][::-1])[:-1]
        outlines[code] = points
    for points in outlines.values():
        for p in points:
            corners.setdefault(p, list(to_drawing(p)))

    # Walls meant level or plumb are made so, for every room that shares them at once.
    rooms_by_code = {room["code"]: room for room in spec["rooms"]}
    parent = {}

    def find(v):
        while parent.setdefault(v, v) != v:
            parent[v] = parent[parent[v]]
            v = parent[v]
        return v
    for code, points in outlines.items():
        level = rooms_by_code.get(code, {}).get("level", 9.0)
        if not level:
            continue
        slope = math.tan(math.radians(level))
        for p, q in zip(points, points[1:] + points[:1]):
            dx, dy = corners[q][0] - corners[p][0], corners[q][1] - corners[p][1]
            if abs(dx) <= slope * abs(dy):
                parent[find(("x", p))] = find(("x", q))
            elif abs(dy) <= slope * abs(dx):
                parent[find(("y", p))] = find(("y", q))
    lines = {}
    for axis, p in list(parent):
        lines.setdefault(find((axis, p)), []).append(p)
    for (axis, _), members in lines.items():
        c = 0 if axis == "x" else 1
        mean = sum(corners[p][c] for p in members) / len(members)
        for p in members:
            corners[p][c] = mean

    spaces = []
    for code in order:
        room = rooms_by_code.get(code, {"code": code})
        shape = []
        for p in outlines[code]:
            q = {"x": round(corners[p][0]), "y": round(corners[p][1])}
            if not shape or shape[-1] != q:
                shape.append(q)
        if len(shape) > 1 and shape[0] == shape[-1]:
            shape.pop()
        # Where two walls met raggedly the outline can run out along one and straight back: a spur
        # with nothing inside it, which goes.
        spur = True
        while spur and len(shape) > 3:
            spur = False
            for i in range(len(shape)):
                a, b, c = shape[i - 1], shape[i], shape[(i + 1) % len(shape)]
                u, v = (a["x"] - b["x"], a["y"] - b["y"]), (c["x"] - b["x"], c["y"] - b["y"])
                if u == (0, 0) or v == (0, 0) or abs(math.degrees(math.atan2(u[0] * v[1] - u[1] * v[0], u[0] * v[0] + u[1] * v[1]))) < 12:
                    del shape[i]
                    spur = True
                    break
        if len(shape) < 3 or not T.simple([(q["x"], q["y"]) for q in shape]):
            if "--draft" not in sys.argv:
                sys.exit(f"{code}: its outline crosses itself once drawn with few corners")
            print(f"  {code}: crosses itself")
        space = {key: room[key] for key in ("code", "wing", "name", "typeCode", "aliases") if key in room}
        space["shape"] = shape
        doors = []
        for at in room.get("doors", []):
            door = door_on([(p["x"], p["y"]) for p in shape],
                           to_drawing((at[0] * w, at[1] * h)),
                           math.dist(to_drawing((0, 0)), to_drawing((DOOR * k, 0))))
            if door is None:
                sys.exit(f"{code}: no wall long enough for the door at {at}")
            doors.append({"from": {"x": round(door[0][0]), "y": round(door[0][1])}, "to": {"x": round(door[1][0]), "y": round(door[1][1])}})
        if doors:
            space["doors"] = doors
        spaces.append(space)
        xs = [p["x"] for p in shape]
        ys = [p["y"] for p in shape]
        print(f"  {code:<24} {len(shape):>2} corners  x {min(xs):>5}-{max(xs):<5} y {min(ys):>5}-{max(ys):<5}  {len(doors)} door(s)")

    # What the marks say of a room they also outline goes with its outline.
    drawn_codes = {space["code"]: space for space in spaces}
    for named in spec.get("named", []):
        if named["code"] in drawn_codes:
            drawn_codes[named["code"]].update({key: v for key, v in named.items() if key != "shape"})
        else:
            spaces.append(named)
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
