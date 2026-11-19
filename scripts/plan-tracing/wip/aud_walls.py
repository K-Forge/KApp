#!/usr/bin/env python3
"""EC P1's auditorium wing built from straight lines: the surveyed walls and the walls the
auditorium's plan prints, each room an intersection of those lines (#218).

    python3 -I scripts/plan-tracing/wip/aud_walls.py > /tmp/drawn.json

Prints the rooms, in the building's units, for the "drawn" block of P1 in specs/ec-surveyed.json,
and on stderr the checks: seats outside the hall (must be 0), overlaps, short sides, corners per
room, and how far each wall's corners lie from its line.

The lines:
 - the north wall, the grey stretch of the front and the Calle 62 wall: the floor's outline (the
   tape's), a wall (10 units) inside; the east wall's inner face where the stair's side is;
 - the front of the ticket office and the north anteroom: the lobby's, y = 678;
 - west wall: plumb, x = 1642, as both plans draw it;
 - south wall: one line, fitted on the straight back of the stage's orange in the auditorium's
   plan (EC-AU-P1-PE), laid on the floor by the similarity onto the four surveyed corners of
   ec-surveyed.json's auditorium group (scale, turn and shift, so the plan keeps its angles); the
   covered patio starts a wall's thickness beyond it;
 - the hall's east walls, the stage's sides and its arc (a circle), the tops of P1-20 and P1-23
   and P1-23's east side: fitted on the plan's lines the same way;
 - the hall's north side, the Audiovisuales booth, the lobby and the doors: lines fitted on
   Brian's marks over the plan (BRIAN below). Where he painted solid concrete nothing is drawn;
 - the way down to the Carrera 9A gate: provisional, from his drawing, where survey-plan.py puts it.
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
    (px, py), a = BRIAN["chamfer"]             # and the chamfer's: the wall Brian's hall runs inside
    ys, xs = np.mgrid[0:m.shape[0], 0:m.shape[1]]
    d = (X0 + xs / K - px) * math.sin(math.radians(a)) - (Y0 + ys / K - py) * math.cos(math.radians(a))
    m[(d > 2) & (ys < (760 - Y0) * K)] = False
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


# Brian's marks over the plan (2026-11-17, #218), laid on the floor by matching that picture to the
# plan's photo (scale 2/3 unit a pixel, no turn, under 1 unit off): each line fitted on his strokes,
# as (a point it passes through, its angle in degrees, y down). Light blue is the hall as a visitor
# sees it; purple, the Audiovisuales booth; black, solid concrete, so nothing is drawn there.
BRIAN = {
    "chamfer": ((1662.6, 719.7), -60.86),   # the hall's corner by the north anteroom
    "nw": ((1754.8, 662.9), -13.49),        # the hall's back wall, under the first solid block
    "notch down": ((1821.5, 667.6), 54.65),
    "notch": ((1860.1, 681.7), 0.0),        # along the second block, to the booth
    "av west": ((1894.0, 704.6), 90.0),     # the booth's side on the hall, plumb as he drew it
    "av south": ((1957.0, 747.7), 10.76),
    "av east": ((2000.3, 727.9), -60.29),
    "wedge a": ((2042.6, 725.0), 24.67),    # the small block east of the booth
    "wedge b": ((2083.4, 731.2), -25.08),
    "ne a": ((2133.7, 739.9), 33.57),       # the hall's corner round its last rows, in two lines
    "ne b": ((2185.7, 784.7), 48.02),
    "ne face": ((2226.9, 829.3), 99.56),    # the far side of the block in the north-east corner
}
EAST_IN = 2311      # the east wall's inner face: the stair's side, where his black starts
SOUTH_WALL = 10     # the south wall's thickness, between the rooms and the covered patio
STAIR = (2265, 868, 2311, 1097)          # the stair, as before: the plan's box of treads
# The guard's booth: the tape's size (a-20, a-22) and front line (a-21), its corridor-side edge on the
# ramp's, under it, as Brian drew it on 2026-11-17 (the tape put it against the central wing).
BOOTH = (1316, 1993, 1368, 2051)
VESTIBULE = (1257, 678, 1538, 1010)      # the lobby, the ticket office in its corner and a solid block below it
TICKETS_DOWN, BLOCK_FROM = 781, 848


def exit_rooms():
    """The way down to the gate on the Carrera 9A, from Brian's drawing of 2026-11-17 and where
    survey-plan.py puts it until he measures in-09 to in-19: a level corridor along the central
    wing, a ramp in the middle and wide stairs along the casa's wall. Provisional."""
    fx = lambda wy: round(1350 + (wy - 39.30) * 34.35)   # survey metres across -> floor x (the booth's corner)
    fy = lambda wx: round(2051 - (wx + 59.70) * 34.35)   # along the passage -> floor y (the booth's front)
    casa = lambda y: math.floor(1486 + (y - 1118) * 9 / 933)    # the outline's edge on the casa's wall
    ramp_in, stairs_in, ramp_foot, stairs_foot, top = 38.30, 40.00, -56.20, -55.60, -46.40
    x0, x1, x2 = 1251, fx(ramp_in), fx(stairs_in)          # the corridor starts on the patio's wall
    corridor_top, ramp_top, stairs_top = fy(-45.0), fy(top + 0.6), fy(top)
    ramp_bottom, stairs_bottom = fy(ramp_foot), fy(stairs_foot)
    return {
        "P1-9A-PASILLO": [(x0, corridor_top), (x1, corridor_top), (x1, BOOTH[3]), (x0, BOOTH[3])],
        "P1-9A-RAMPA": [(x1, ramp_top), (x2, ramp_top), (x2, ramp_bottom), (x1, ramp_bottom)],
        "P1-9A-ESCALERA": [(x2, stairs_top), (casa(stairs_top), stairs_top),
                                  (casa(stairs_bottom), stairs_bottom), (x2, stairs_bottom)],
    }


def built(outline, line_mask, stage):
    (slope, c0), (cx, cy, radius), (side_w, side_e) = stage
    o = {k: tuple(v) for k, v in (("chamfer", ((1637, 668), (1699, 581))), ("north", ((1699, 581), (2252, 740))),
                                  ("step", ((2252, 740), (2241, 779))), ("grey", ((2241, 779), (2353, 812))),
                                  ("east", ((2353, 812), (2355, 1186))), ("lot", ((1506, 1125), (2350, 1317))))}
    for a_, b_ in o.values():          # each one an edge of the outline
        assert any(tuple(outline[i]) == a_ and tuple(outline[i - 1]) == b_ or
                   tuple(outline[i]) == b_ and tuple(outline[i - 1]) == a_ for i in range(len(outline))), (a_, b_)
    N, G, E, LOT = (inside_of(*o[k]) for k in ("north", "grey", "east", "lot"))
    B = {k: line(p, (math.cos(math.radians(a)), math.sin(math.radians(a)))) for k, (p, a) in BRIAN.items()}
    south = line((0, c0), (1, slope))                       # the rooms' side of the south wall
    south_out = line((0, c0 + SOUTH_WALL * math.hypot(1, slope)), (1, slope))   # the covered patio's side
    W = line((1642, 0), (0, 1))
    e1 = fit_line(line_mask, (1322, 470), (1328, 630), 4)
    e2 = fit_line(line_mask, (1316, 690), (1236, 850), 4)
    t20 = line((0, offset_fit(line_mask, slope, 1650, 1730, 503.0)), (1, slope))
    t23 = line((0, offset_fit(line_mask, slope, 2030, 2190, 499.5)), (1, slope))
    east23 = fit_line(line_mask, (1300, 1015), (1270, 1125), 4)
    level = lambda y: line((0, y), (1, 0))
    plumb = lambda x: line((x, 0), (0, 1))
    EI, SX = plumb(EAST_IN), plumb(STAIR[0])
    P = {"W20": meet(W, t20), "WS": meet(W, south), "WO": meet(W, south_out),
         "WC": meet(W, B["chamfer"]), "C_NW": meet(B["chamfer"], B["nw"]), "NW_D": meet(B["nw"], B["notch down"]),
         "D_N": meet(B["notch down"], B["notch"]), "N_AV": meet(B["notch"], B["av west"]),
         "AVNW": meet(B["av west"], N), "AVNE": meet(B["av east"], N),
         "AVSW": meet(B["av west"], B["av south"]), "AVSE": meet(B["av south"], B["av east"]),
         "AV_A": meet(B["av east"], B["wedge a"]), "A_B": meet(B["wedge a"], B["wedge b"]),
         "B_NE": meet(B["wedge b"], B["ne a"]), "NE_AB": meet(B["ne a"], B["ne b"]), "NE_E1": meet(B["ne b"], e1),
         "E1E2": meet(e1, e2), "E2T23": meet(e2, t23), "E2F": meet(e2, B["ne face"]), "FG": meet(B["ne face"], G),
         "GE": meet(G, EI), "ST_NE": (STAIR[2], STAIR[1]), "ST_NW": (STAIR[0], STAIR[1]),
         "ST_SW": (STAIR[0], STAIR[3]), "ST_SE": (STAIR[2], STAIR[3]), "T23S": meet(t23, SX),
         "S20": meet(side_w, south), "T20W": meet(side_w, t20), "ARCW": on_circle(side_w, cx, cy, radius),
         "ARCE": on_circle(side_e, cx, cy, radius), "S23": meet(side_e, south), "T23W": meet(side_e, t23),
         "T23E": meet(east23, t23), "S23E": meet(east23, south), "ES": meet(EI, south),
         "EO": meet(E, south_out), "ELOT": meet(E, LOT),
         "AW": (VESTIBULE[2], 678), "LOTW": meet(LOT, plumb(VESTIBULE[2]))}
    P["AS"] = (VESTIBULE[2], P["WO"][1])
    a0 = math.atan2(P["ARCW"][1] - cy, P["ARCW"][0] - cx)
    a1 = math.atan2(P["ARCE"][1] - cy, P["ARCE"][0] - cx)
    arc = [(cx + radius * math.cos(a0 + (a1 - a0) * i / 9), cy + radius * math.sin(a0 + (a1 - a0) * i / 9))
           for i in range(1, 9)]
    R = {k: (round(float(v[0])), round(float(v[1]))) for k, v in P.items()}
    arc = [(round(x), round(y)) for x, y in arc]
    r = lambda *ks: [R[k] for k in ks]
    x0, y0, x1, y1 = VESTIBULE
    tq = (1461, TICKETS_DOWN)
    rooms = {
        "P1-AUD-ESCENARIO": r("S20", "T20W", "ARCW") + arc + r("ARCE", "T23W", "S23"),
        "P1-20": r("WS", "W20", "T20W", "S20"),
        "P1-23": r("S23", "T23W", "E2T23", "T23E", "S23E"),
        "P1-AUD-AUDIOVISUALES": r("AVNW", "AVNE", "AV_A", "AVSE", "AVSW"),
        "P1-AUD-ESCALERA": r("ST_NW", "ST_NE", "ST_SE", "ST_SW"),
        "P1-AUD-UTILERIA": r("T23E", "T23S", "ST_SW", "ST_SE", "ES", "S23E"),
        "P1-AUD-SALIDA-SUR": r("FG", "GE", "ST_NE", "ST_NW", "T23S", "E2T23", "E2F"),
        "P1-AUDITORIO-SONIA": r("W20", "WC", "C_NW", "NW_D", "D_N", "N_AV", "AVSW", "AVSE", "AV_A", "A_B", "B_NE",
                                "NE_AB", "NE_E1", "E1E2", "E2T23", "T23W", "ARCE") + arc[::-1] + r("ARCW", "T20W"),
        "P1-AUD-TAQUILLA": [(tq[0], y0), (x1, y0), (x1, tq[1]), tq],
        "P1-AUD-ANTESALA-N": [(x1, y0), (1642, y0)] + r("WO", "AS"),
        "P1-AUD-VESTIBULO": [(x0, y0), (tq[0], y0), tq, (x1, tq[1]), (x1, BLOCK_FROM), (tq[0], BLOCK_FROM),
                             (tq[0], y1), (x0, y1)],
        "P1-PATIO-CUBIERTO": r("AS", "WO", "EO", "ELOT", "LOTW"),
        "P1-PORTERIA": [(BOOTH[0], BOOTH[1]), (BOOTH[2], BOOTH[1]), (BOOTH[2], BOOTH[3]), (BOOTH[0], BOOTH[3])],
    }
    rooms.update(exit_rooms())
    # Doors where Brian marked them in green; Punto 66 from the step, 1.35 m (survey bis-34).
    along = lambda p, d, s: (round(p[0] + s * d[0]), round(p[1] + s * d[1]))
    on = lambda l, x: (x, round(float(l[0][1] + (x - l[0][0]) * l[1][1] / l[1][0])))
    e2d = -e2[1] if e2[1][1] > 0 else e2[1]
    west_door = [(1642, 863), (1642, 915)]
    av_door = [(R["AVSW"][0], 690), (R["AVSW"][0], 719)]
    east_door = [along(R["E2T23"], e2d, 12), along(R["E2T23"], e2d, 44)]
    exit_door = [R["FG"], along(R["FG"], G[1], 46)]
    lobby_door = [(x1, 785), (x1, 839)]
    south_door = [(1568, R["AS"][1]), (1632, R["AS"][1])]
    props_door = [on(south, 2261), on(south, 2301)]
    doors = {
        "P1-AUDITORIO-SONIA": [west_door, av_door, east_door],
        "P1-AUD-AUDIOVISUALES": [av_door],
        "P1-AUD-ANTESALA-N": [lobby_door, west_door, south_door],
        "P1-AUD-SALIDA-SUR": [east_door, exit_door],
        "P1-AUD-UTILERIA": [props_door],
        "P1-AUD-VESTIBULO": [[(x0, 721), (x0, 810)], [(1313, y0), (1403, y0)], [(1310, y1), (1411, y1)], lobby_door],
        "P1-PATIO-CUBIERTO": [south_door, [on(south_out, 2261), on(south_out, 2301)], [(x1, 1022), (x1, 1108)]],
    }
    lines = {"north": N, "south": south, "covered patio": south_out, "west": W, "east": EI}
    return rooms, doors, lines


# Rooms that keep more corners than Core's caps, and why (#218).
CORNERS = {"P1-AUDITORIO-SONIA": 17, "P1-AUD-ESCENARIO": 14, "P1-PATIO-CUBIERTO": 6,
           "P1-AUD-UTILERIA": 6, "P1-AUD-SALIDA-SUR": 7, "P1-AUD-VESTIBULO": 8}
# Short sides Brian's marks draw: the step of the props room under the stair.
SHORT = {("P1-AUD-UTILERIA", 2)}


def checks(rooms, doors, lines, seats, outline):
    """What Core asked to keep checked (#218), printed; False when one fails."""
    ok = True
    shapes = {c: Polygon(s) for c, s in rooms.items()}
    hall = shapes["P1-AUDITORIO-SONIA"]
    out = int((~contains_xy(hall, seats[:, 0], seats[:, 1])).sum())
    near = {c: int(contains_xy(g.buffer(1.5), seats[:, 0], seats[:, 1]).sum()) for c, g in shapes.items()
            if c != "P1-AUDITORIO-SONIA"}
    print(f"seat pixels: {len(seats)}, outside the hall: {out}, within 1.5 units of another room: "
          f"{sum(near.values())} {[c for c, n in near.items() if n]}", file=sys.stderr)
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
    arc = set(rooms["P1-AUD-ESCENARIO"]) & set(rooms["P1-AUDITORIO-SONIA"])
    print("  room                    corners (cap)  shortest side", file=sys.stderr)
    for c, s in rooms.items():
        sides = [math.dist(s[i], s[i - 1]) for i in range(len(s))]
        corners = len(s) - (len(arc) - 2 if c == "P1-AUDITORIO-SONIA" else 0)
        cap = CORNERS.get(c, 5)
        short = [i for i, x in enumerate(sides) if x < 20 and (c, i) not in SHORT
                 and not (c in ("P1-AUD-ESCENARIO", "P1-AUDITORIO-SONIA") and s[i] in arc and s[i - 1] in arc)]
        print(f"  {c:<24} {corners:>2} ({cap:>2})        {min(sides):.0f}" + (f"  SHORT {short}" if short else ""),
              file=sys.stderr)
        ok &= corners <= cap and not short
    for name, (p, d) in lines.items():
        n = np.array([-d[1], d[0]])
        far = max((abs(np.dot(np.array(v) - p, n)) for s in rooms.values() for v in s
                   if abs(np.dot(np.array(v) - p, n)) < 5), default=0)
        print(f"  corners on the {name} wall: at most {far:.1f} units off its line", file=sys.stderr)
        ok &= far <= 1.0
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
