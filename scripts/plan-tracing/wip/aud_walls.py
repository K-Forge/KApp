#!/usr/bin/env python3
"""EC P1's auditorium wing built from straight lines: the surveyed walls and the walls the
auditorium's plan prints, each room an intersection of those lines (#218).

    python3 -I scripts/plan-tracing/wip/aud_walls.py > /tmp/drawn.json

Prints the rooms, in the building's units, for the "drawn" block of P1 in specs/ec-surveyed.json,
and on stderr the checks: seats outside the hall (must be 0), overlaps, short sides, corners per
room, and how far each wall's corners lie from its line.

The lines:
 - north wall, chamfer, the front's step and grey stretch, and the Calle 62 wall: the floor's
   outline (the tape's), a wall (10 units) inside;
 - the front of the ticket office and the north anteroom: the lobby's, y = 678;
 - west wall: plumb where the chamfer meets that front, x = 1642, as both plans draw it;
 - south wall: one line, fitted on the straight back of the stage's orange in the auditorium's
   plan (EC-AU-P1-PE), laid on the floor by the similarity onto the four surveyed corners of
   ec-surveyed.json's auditorium group (scale, turn and shift, so the plan keeps its angles);
 - the hall's east wall, the stage's sides and its arc (a circle), the tops of P1-20 and P1-23
   and P1-23's east side: fitted on the plan's lines the same way;
 - the rooms along the north wall: rectangles square to it and triangles, where the plan draws
   them, held clear of the plan's seats (its grey rows, read off the photo).
The hall is what is left between them, so two neighbours always share their wall.

Not standard library: numpy, opencv-python-headless, shapely and pillow. A one-off kept so the wing
can be drawn again the same way; see README.md.
"""
import json
import math
import os
import sys

import cv2
import numpy as np
from PIL import Image, ImageFilter
import itertools

from shapely import contains_xy
from shapely.geometry import Point, Polygon

HERE = os.path.dirname(os.path.abspath(__file__))
TRACING = os.path.dirname(HERE)
sys.path.insert(0, TRACING)
from trace import homography, survey_folder  # noqa: E402

K = 2                                  # raster pixels a floor unit
X0, Y0, X1, Y1 = 1550, 560, 2400, 1220  # the wing's box on the floor
WALL = 10                              # units a wall is thick: rooms stop this far inside the outline
INSIDE = (1900, 850)                   # a point inside the wing, to tell a wall's inner side


def largest(g):
    return max(getattr(g, "geoms", [g]), key=lambda q: q.area)


def similarity(spec_au, onto):
    """Photo pixels (the original's) -> floor units, fitted on the auditorium group's four pairs."""
    pw, ph = spec_au["plaque"]
    hm = np.array(homography([(0, 0), (pw, 0), (pw, ph), (0, ph)], spec_au["corners"]))
    scale = 8064 / spec_au["resample"]          # the spec's photo was resampled from the original

    def drawing_to_photo(p):
        v = hm @ [p[0], p[1], 1]
        return complex(v[0] / v[2] * scale, v[1] / v[2] * scale)
    s = np.array([drawing_to_photo(p) for p in onto["from"]])
    d = np.array([complex(*p) for p in onto["to"]])
    (a, b), *_ = np.linalg.lstsq(np.stack([s, np.ones(len(s))], 1), d, rcond=None)
    residuals = np.abs(a * s + b - d)
    print(f"similarity: scale {abs(a):.4f}, turn {math.degrees(np.angle(a)):.2f} deg, "
          f"residuals {np.round(residuals, 1).tolist()} units", file=sys.stderr)
    return a, b, drawing_to_photo


def warped(mask, a, b):
    """A photo mask laid on the floor's wing box, K pixels a unit."""
    ia = 1 / a
    t = ia * (complex(X0, Y0) - b)
    w, h = int((X1 - X0) * K), int((Y1 - Y0) * K)
    img = Image.fromarray((mask * 255).astype("uint8")).transform(
        (w, h), Image.AFFINE, (ia.real / K, -ia.imag / K, t.real, ia.imag / K, ia.real / K, t.imag),
        resample=Image.BILINEAR)
    return np.asarray(img) > 110


def seat_mask(photo, a, b):
    """The plan's seat rows: dark, neutral grey lines, in big clusters inside the hall's box."""
    rgb = np.asarray(photo).astype(int)
    lum = np.asarray(photo.convert("L")).astype(int)
    paper = np.asarray(photo.convert("L").filter(ImageFilter.GaussianBlur(20))).astype(int)
    r, g, b_ = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    grey = ((paper - lum) > 14) & (rgb.max(2) - rgb.min(2) < 16) & (r >= b_ - 1) & (r >= g - 3) & (rgb.sum(2) > 260)
    m = warped(grey, a, b)
    n, lab, st, _ = cv2.connectedComponentsWithStats(cv2.dilate(m.astype(np.uint8), np.ones((5, 5), np.uint8)), 8)
    keep = np.zeros(n, bool)
    for i in range(1, n):
        x, y, w, h, _ = st[i]
        keep[i] = ((m & (lab == i)).sum() > 300 and X0 + x / K > 1620 and X0 + (x + w) / K < 2250
                   and Y0 + y / K > 590 and Y0 + (y + h) / K < 1000)
    m = m & keep[lab]
    m[:, : int((1652 - X0) * K)] = False      # the west wall's grey line, not a seat
    return m


def fit_line(mask, p, q, w):
    """A straight wall: total least squares through the mask's pixels within w of p-q."""
    ys, xs = np.nonzero(mask)
    pts = np.stack([xs, ys], 1).astype(float)
    p, q = np.array(p, float), np.array(q, float)
    d = (q - p) / np.linalg.norm(q - p)
    n = np.array([-d[1], d[0]])
    t, s = (pts - p) @ d, (pts - p) @ n
    sel = pts[(t > 0) & (t < np.linalg.norm(q - p)) & (np.abs(s) < w)]
    for _ in range(4):
        c = sel.mean(0)
        dd = np.linalg.svd(sel - c, full_matrices=False)[2][0]
        r = (sel - c) @ np.array([-dd[1], dd[0]])
        sel = sel[np.abs(r) < max(2 * r.std(), 1.5)]
    c = sel.mean(0)
    dd = np.linalg.svd(sel - c, full_matrices=False)[2][0]
    return np.array([X0 + c[0] / K, Y0 + c[1] / K]), dd


def stage_fit(photo, a, b):
    """The stage's orange in the plan: the straight back (the south wall), its two sides and the
    circle of its front, each fitted on the orange's edge."""
    rgb = np.asarray(photo).astype(int)
    r, g, b_ = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    o = cv2.morphologyEx(warped((r - b_ > 55) & (r - g > 25) & (g - b_ > 15), a, b).astype(np.uint8),
                         cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(o, 8)
    o = lab == 1 + np.argmax(st[1:, cv2.CC_STAT_AREA])
    cols = np.nonzero(o.any(0))[0]
    span = cols.max() - cols.min()
    floor = lambda c, y: (X0 + c / K, Y0 + y / K)
    mid = (cols > cols.min() + 0.1 * span) & (cols < cols.min() + 0.9 * span)
    back = np.array([floor(c, np.nonzero(o[:, c])[0].max()) for c in cols[mid]])
    slope, c0 = np.polyfit(back[:, 0], back[:, 1], 1)
    inner = (cols > cols.min() + 0.06 * span) & (cols < cols.min() + 0.94 * span)
    top = np.array([floor(c, np.nonzero(o[:, c])[0].min()) for c in cols[inner]])
    (cx, cy, k), *_ = np.linalg.lstsq(np.c_[2 * top, np.ones(len(top))], (top ** 2).sum(1), rcond=None)
    radius = math.sqrt(k + cx * cx + cy * cy)
    rows = np.nonzero(o.any(1))[0]
    sides = []
    for pick, (y0, y1) in ((np.min, (960, 1000)), (np.max, (985, 1060))):
        pts = np.array([floor(pick(np.nonzero(o[r_])[0]), r_) for r_ in rows])
        pts = pts[(pts[:, 1] > y0) & (pts[:, 1] < y1)]
        mx, cx_ = np.polyfit(pts[:, 1], pts[:, 0], 1)
        sides.append(line((cx_, 0), (mx, 1)))
    print(f"stage: back at {math.degrees(math.atan(slope)):.2f} deg, front a circle of radius {radius:.1f} "
          f"(spread {np.std(np.hypot(top[:, 0] - cx, top[:, 1] - cy) - radius):.2f})", file=sys.stderr)
    return (slope, c0), (cx, cy, radius), sides


def offset_fit(mask, slope, x0, x1, c_guess, w=6):
    """A wall parallel to the south wall: its offset, the median of the plan's line pixels near
    y = slope x + c_guess between x0 and x1."""
    ys, xs = np.nonzero(mask)
    x, y = X0 + xs / K, Y0 + ys / K
    c = y - slope * x
    sel = (x > x0) & (x < x1) & (np.abs(c - c_guess) < w)
    return float(np.median(c[sel]))


def line(p, d):
    d = np.array(d, float)
    return np.array(p, float), d / np.linalg.norm(d)


def meet(l1, l2):
    (p, d), (q, e) = l1, l2
    t = np.linalg.solve(np.array([d, -e]).T, q - p)
    return p + t[0] * d


def inside_of(p, q, w=WALL):
    """The line p-q moved w into the building."""
    p, q = np.array(p, float), np.array(q, float)
    d = (q - p) / np.linalg.norm(q - p)
    n = np.array([-d[1], d[0]])
    if np.dot(np.array(INSIDE) - p, n) < 0:
        n = -n
    return p + w * n, d


def on_circle(l, cx, cy, radius):
    """Where a line meets the circle, the upper of the two points."""
    p, d = l
    f = p - [cx, cy]
    b_ = 2 * np.dot(f, d)
    disc = math.sqrt(b_ * b_ - 4 * (np.dot(f, f) - radius * radius))
    return min((p + t * d for t in ((-b_ - disc) / 2, (-b_ + disc) / 2)), key=lambda q: q[1])


# Along the north wall, in units from the chamfer's top corner: where the plan draws each room,
# and how deep, held clear of the seats.
NORTH = {"p1-27 from": 130, "booth from": 196, "booth to": 318, "p1-28 to": 430,
         "p1-27 depth": 40, "booth depth": 70, "p1-28 depth": 30, "p1-26 chamfer y": 640, "p1-29 down to": 813}
STAIR = (2265, 868, 2311, 1097)          # the stair, as before: the plan's box of treads
BOOTH = (1350, 1993, 1402, 2051)         # the guard's booth at the gate (survey-plan.py BOOTH, a-20 to a-22)


def built(outline, line_mask, stage):
    (slope, c0), (cx, cy, radius), (side_w, side_e) = stage
    o = {k: tuple(v) for k, v in (("chamfer", ((1637, 668), (1699, 581))), ("north", ((1699, 581), (2252, 740))),
                                  ("step", ((2252, 740), (2241, 779))), ("grey", ((2241, 779), (2353, 812))),
                                  ("east", ((2353, 812), (2355, 1186))), ("lot", ((1506, 1125), (2350, 1317))))}
    for a_, b_ in o.values():          # each one an edge of the outline
        assert any(tuple(outline[i]) == a_ and tuple(outline[i - 1]) == b_ or
                   tuple(outline[i]) == b_ and tuple(outline[i - 1]) == a_ for i in range(len(outline))), (a_, b_)
    N, C, STEP, G, E, LOT = (inside_of(*o[k]) for k in ("north", "chamfer", "step", "grey", "east", "lot"))
    front = line((0, 678), (1, 0))
    south = line((0, c0), (1, slope))
    wf = meet(C, front)
    W = line((round(wf[0]), 0), (0, 1))
    e1 = fit_line(line_mask, (1322, 470), (1328, 630), 4)
    e2 = fit_line(line_mask, (1316, 690), (1236, 850), 4)
    t20 = line((0, offset_fit(line_mask, slope, 1650, 1730, 503.0)), (1, slope))
    t23 = line((0, offset_fit(line_mask, slope, 2030, 2190, 499.5)), (1, slope))
    east23 = fit_line(line_mask, (1300, 1015), (1270, 1125), 4)
    level = lambda y: line((0, y), (1, 0))
    plumb = lambda x: line((x, 0), (0, 1))
    P = {"WF": (round(wf[0]), 678), "CN": meet(C, N), "NE1": meet(N, e1), "NS": meet(N, STEP), "SG": meet(STEP, G),
         "E1E2": meet(e1, e2), "E2T23": meet(e2, t23), "W20": meet(W, t20), "WS": meet(W, south),
         "S20": meet(side_w, south), "T20W": meet(side_w, t20), "ARCW": on_circle(side_w, cx, cy, radius),
         "ARCE": on_circle(side_e, cx, cy, radius), "S23": meet(side_e, south), "T23W": meet(side_e, t23),
         "T23E": meet(east23, t23), "U_NW": meet(east23, level(STAIR[3])), "S23E": meet(east23, south),
         "U_NE": meet(E, level(STAIR[3])), "ES": meet(E, south), "ELOT": meet(E, LOT),
         "ST_NW": (STAIR[0], STAIR[1]), "ST_NE": (STAIR[2], STAIR[1]), "ST_SE": (STAIR[2], STAIR[3]),
         "ST_SW": (STAIR[0], STAIR[3]), "GST": meet(G, plumb(STAIR[2])),
         "AW": (1538, 678), "AS": (1538, round(meet(W, south)[1])), "LOTW": meet(LOT, plumb(1538)),
         "TQW": (1461, 678), "TQSW": (1461, 781), "TQSE": (1538, 781), "LB": (1538, 1010)}
    cn = np.array(P["CN"])
    nd = N[1]
    nn = np.array([-nd[1], nd[0]]) * (1 if nd[0] > 0 else -1)
    nn = nn if nn[1] > 0 else -nn
    north = lambda u, v=0: cn + u * nd + v * nn
    q = NORTH
    P.update({"26C": meet(C, level(q["p1-26 chamfer y"])), "26N": north(q["p1-27 from"]),
              "27SW": north(q["p1-27 from"], q["p1-27 depth"]), "27SE": north(q["booth from"], q["p1-27 depth"]),
              "AVNW": north(q["booth from"]), "AVNE": north(q["booth to"]),
              "AVSW": north(q["booth from"], q["booth depth"]), "AVSE": north(q["booth to"], q["booth depth"]),
              "28E": north(q["p1-28 to"]), "28A": north((q["booth to"] + q["p1-28 to"]) / 2, q["p1-28 depth"]),
              "29S": meet(e1, level(q["p1-29 down to"]))})
    # the front's arc: ten points, its two ends included, evenly round the circle
    a0 = math.atan2(P["ARCW"][1] - cy, P["ARCW"][0] - cx)
    a1 = math.atan2(P["ARCE"][1] - cy, P["ARCE"][0] - cx)
    arc = [(cx + radius * math.cos(a0 + (a1 - a0) * i / 9), cy + radius * math.sin(a0 + (a1 - a0) * i / 9))
           for i in range(1, 9)]
    R = {k: (round(float(v[0])), round(float(v[1]))) for k, v in P.items()}
    arc = [(round(x), round(y)) for x, y in arc]
    r = lambda *ks: [R[k] for k in ks]
    rooms = {
        "P1-AUD-ESCENARIO": r("S20", "T20W", "ARCW") + arc + r("ARCE", "T23W", "S23"),
        "P1-20": r("WS", "W20", "T20W", "S20"),
        "P1-23": r("S23", "T23W", "E2T23", "T23E", "U_NW", "S23E"),
        "P1-26": r("26C", "CN", "26N"),
        "P1-27": r("26N", "AVNW", "27SE", "27SW"),
        "P1-AUD-AUDIOVISUALES": r("AVNW", "AVNE", "AVSE", "AVSW"),
        "P1-28": r("AVNE", "28E", "28A"),
        "P1-29": r("28E", "NE1", "29S"),
        "P1-AUD-ESCALERA": r("ST_NW", "ST_NE", "ST_SE", "ST_SW"),
        "P1-AUD-UTILERIA": r("U_NW", "ST_SW", "ST_SE", "U_NE", "ES", "S23E"),
        "P1-AUD-SALIDA-SUR": r("NE1", "NS", "SG", "GST", "ST_NE", "ST_NW", "ST_SW", "U_NW", "T23E", "E2T23", "E1E2", "29S"),
        "P1-AUDITORIO-SONIA": r("W20", "WF", "26C", "26N", "27SW", "27SE", "AVSW", "AVSE", "AVNE", "28A", "28E", "29S",
                                "E1E2", "E2T23", "T23W", "ARCE") + arc[::-1] + r("ARCW", "T20W"),
        "P1-AUD-TAQUILLA": r("TQW", "AW", "TQSE", "TQSW"),
        "P1-AUD-ANTESALA-N": r("AW", "WF", "W20", "WS", "AS"),
        "P1-AUD-VESTIBULO": [(1257, 678)] + r("TQW", "TQSW", "TQSE", "LB") + [(1257, 1010)],
        "P1-PATIO-CUBIERTO": r("AS", "WS", "S20", "S23", "S23E", "ES", "ELOT", "LOTW"),
        "P1-PORTERIA": [(BOOTH[0], BOOTH[1]), (BOOTH[2], BOOTH[1]), (BOOTH[2], BOOTH[3]), (BOOTH[0], BOOTH[3])],
    }
    # Doors. The hall's west door beside P1-20, 1.5 m, as the plan draws it; its east one at the
    # foot of the diagonal wall; Punto 66 from the step in the front, 1.35 m (survey bis-34).
    along = lambda p, d, s: (round(p[0] + s * d[0]), round(p[1] + s * d[1]))
    e2d = -e2[1] if e2[1][1] > 0 else e2[1]
    west_door = [(R["W20"][0], R["W20"][1] - 55), (R["W20"][0], R["W20"][1] - 3)]
    east_door = [along(R["E2T23"], e2d, 12), along(R["E2T23"], e2d, 44)]
    exit_door = [R["SG"], along(R["SG"], G[1], 46)]
    lobby_door = [(1538, 790), (1538, 830)]
    doors = {
        "P1-AUDITORIO-SONIA": [west_door, east_door],
        "P1-AUD-ANTESALA-N": [lobby_door, west_door, [(1585, R["AS"][1]), (1616, R["AS"][1])]],
        "P1-AUD-SALIDA-SUR": [east_door, exit_door],
        "P1-AUD-UTILERIA": [[(round(x), round(slope * x + c0)) for x in (2254, 2285)]],
        "P1-AUD-VESTIBULO": [[(1257, 740), (1257, 780)], [(1317, 678), (1397, 678)], [(1337, 1010), (1377, 1010)],
                             lobby_door],
    }
    lines = {"north": N, "south": south, "west": W}
    return rooms, doors, lines


def checks(rooms, doors, lines, seats, outline):
    """What Core asked to keep checked (#218), printed; False when one fails."""
    ok = True
    shapes = {c: Polygon(s) for c, s in rooms.items()}
    hall = shapes["P1-AUDITORIO-SONIA"]
    out = int((~contains_xy(hall, seats[:, 0], seats[:, 1])).sum())
    near = {c: int(contains_xy(g.buffer(1.5), seats[:, 0], seats[:, 1]).sum()) for c, g in shapes.items()
            if c != "P1-AUDITORIO-SONIA"}
    print(f"seat pixels: {len(seats)}, outside the hall: {out}, within 1.5 units of another room: "
          f"{sum(near.values())}", file=sys.stderr)
    ok &= out == 0 and not any(near.values())
    for (a, g), (b, h) in itertools.combinations(shapes.items(), 2):
        if g.intersection(h).area > 0.5:
            print(f"overlap {a} {b} {g.intersection(h).area:.1f}", file=sys.stderr)
            ok = False
    ring = Polygon(outline)
    for c, g in shapes.items():
        if not g.is_valid or g.difference(ring).area > 0.5:
            print(f"{c}: invalid or outside the outline", file=sys.stderr)
            ok = False
    for c, ds in doors.items():
        for d in ds:
            if max(shapes[c].exterior.distance(Point(e)) for e in d) > 1:
                print(f"{c}: door {d} off its wall", file=sys.stderr)
                ok = False
    caps = {"P1-AUDITORIO-SONIA": 16, "P1-AUD-ESCENARIO": 14, "P1-PATIO-CUBIERTO": 6}
    arc = set(rooms["P1-AUD-ESCENARIO"]) & set(rooms["P1-AUDITORIO-SONIA"])
    for c, s in rooms.items():
        sides = [math.dist(s[i], s[i - 1]) for i in range(len(s))]
        corners = len(s) - (len(arc) - 2 if c == "P1-AUDITORIO-SONIA" else 0)
        print(f"  {c:<22} {len(s):>2} corners, shortest side {min(sides):.0f}"
              + (f" ({corners} not counting the arc it shares with the stage)" if c == "P1-AUDITORIO-SONIA" else ""),
              file=sys.stderr)
    for name, (p, d) in lines.items():
        n = np.array([-d[1], d[0]])
        far = max((abs(np.dot(np.array(v) - p, n)) for s in rooms.values() for v in s
                   if abs(np.dot(np.array(v) - p, n)) < 12), default=0)
        print(f"  corners on the {name} wall: at most {far:.1f} units off its line", file=sys.stderr)
    return ok


def main():
    spec = json.load(open(os.path.join(TRACING, "specs", "ec-surveyed.json")))
    rules = spec["floors"]["P1"]
    spec_au = json.load(open(os.path.join(TRACING, "specs", "ec-au-p1-pe.json")))
    photo = Image.open(os.path.join(survey_folder(), "Edificio Central", "Originales", "EC-AU-P1-PE.jpeg")).convert("RGB")
    a, b, _ = similarity(spec_au, rules["groups"][0]["onto"])
    lum = photo.convert("L")
    lines = ((np.asarray(lum.filter(ImageFilter.GaussianBlur(20))).astype(int) - np.asarray(lum).astype(int)) > 16)
    line_mask = warped(lines, a, b)
    seat_px = seat_mask(photo, a, b)
    ys, xs = np.nonzero(seat_px)
    seats = np.stack([X0 + (xs + 0.5) / K, Y0 + (ys + 0.5) / K], 1)
    rooms, doors, wall_lines = built([tuple(p) for p in rules["outline"]], line_mask, stage_fit(photo, a, b))
    ok = checks(rooms, doors, wall_lines, seats, rules["outline"])
    json.dump({c: {"shape": [list(p) for p in s], "doors": [[list(e) for e in d] for d in doors.get(c, [])]}
               for c, s in rooms.items()}, sys.stdout, indent=1)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
