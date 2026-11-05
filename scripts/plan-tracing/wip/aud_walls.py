#!/usr/bin/env python3
"""EC P1's auditorium wing as Brian outlined it over the auditorium's plan, laid on the floor so that
it follows the plan's walls and keeps every seat the plan prints inside the hall (#218).

    python3 -I scripts/plan-tracing/wip/aud_walls.py > /tmp/drawn.json

Prints the rooms, in the building's units, for the "drawn" block of P1 in specs/ec-surveyed.json,
and on stderr how many of the plan's seat pixels fall outside the hall (it must be 0).

How:
 1. The auditorium's plan photo (EC-AU-P1-PE, the original in $KAPP_SURVEY) goes onto the floor by
    the similarity - scale, turn and shift, nothing else - that takes the plan onto the four
    surveyed corners of ec-surveyed.json's auditorium group. A similarity keeps the plan's angles,
    where the group's four-corner warp did not.
 2. Brian's outlines (traced/ec-p1-auditorio.json, in the P1 drawing's units) go the same way.
 3. The seats are the plan's grey lines (walls are blue, red or yellow, arrows green, stairs black),
    read off the photo and laid the same way. The hall takes them all, 1.5 units clear of its walls,
    and every other room stays off them.
 4. The ticket office, the north anteroom and the stair are squared to the building with
    regular.py, as both plans draw them; the hall is cut on the plan's east wall, fitted on its
    lines; the south anteroom is what is left between the hall, the stair, the prop room and the
    front.
 5. Walls two rooms share are simplified once (to 6 units), everything is rounded to whole units,
    and a room rounding pushes onto another gives way.

Not standard library: numpy, opencv-python-headless, shapely >= 2.1 and pillow. A one-off kept so
the wing can be drawn again the same way; see README.md.
"""
import json
import math
import os
import sys

import cv2
import numpy as np
from PIL import Image, ImageFilter
from shapely import contains_xy, coverage_simplify, set_precision
from shapely.geometry import Point, Polygon, box
from shapely.ops import unary_union

HERE = os.path.dirname(os.path.abspath(__file__))
TRACING = os.path.dirname(HERE)
sys.path.insert(0, TRACING)
import regular  # noqa: E402
from trace import homography, survey_folder  # noqa: E402

K = 2                                  # raster pixels a floor unit
X0, Y0, X1, Y1 = 1550, 560, 2400, 1220  # the wing's box on the floor
TOL = 6                                # how far a shared wall may be straightened, in units


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


def main():
    spec = json.load(open(os.path.join(TRACING, "specs", "ec-surveyed.json")))
    rules = spec["floors"]["P1"]
    spec_au = json.load(open(os.path.join(TRACING, "specs", "ec-au-p1-pe.json")))
    strokes = json.load(open(os.path.join(TRACING, "traced", "ec-p1-auditorio.json")))
    photo = Image.open(os.path.join(survey_folder(), "Edificio Central", "Originales", "EC-AU-P1-PE.jpeg")).convert("RGB")

    a, b, to_photo = similarity(spec_au, rules["groups"][0]["onto"])

    def to_floor(p):
        z = a * to_photo((p["x"], p["y"])) + b
        return z.real, z.imag

    outline = Polygon(rules["outline"])
    inside = outline.buffer(-10, join_style="mitre")         # a wall inside the building's faces
    seed = json.load(open(os.path.join(os.path.dirname(os.path.dirname(TRACING)), "app", "backend", "microservices",
                                       "map-service", "src", "main", "resources", "db", "seed", "map", "ec.json")))
    floor = next(f for f in seed["floors"] if f["code"] == "P1")
    vestibulo = next(sp for sp in floor["spaces"] if sp["code"] == "P1-AUD-VESTIBULO")    # as the fit left it
    lobby = Polygon([(p["x"], p["y"]) for p in vestibulo["shape"]]).buffer(10, join_style="mitre")

    # The plan's lines and its seats on the floor.
    lum = photo.convert("L")
    lines = ((np.asarray(lum.filter(ImageFilter.GaussianBlur(20))).astype(int) - np.asarray(lum).astype(int)) > 16)
    line_mask = warped(lines, a, b)
    seat_px = seat_mask(photo, a, b)
    # the rows as smooth bands: the seats grown 3 units and closed over 20, so a wall the rows reach
    # follows the row, not each seat
    m = cv2.morphologyEx(cv2.dilate(seat_px.astype(np.uint8), np.ones((7, 7), np.uint8)), cv2.MORPH_CLOSE,
                         cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (41, 41)))
    contours, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    seats = unary_union([Polygon([(X0 + x / K, Y0 + y / K) for x, y in c[:, 0]]).buffer(0)
                         for c in contours if len(c) >= 3]).simplify(1.0)

    # Brian's rooms on the floor, inside the building and off the lobby.
    R = {sp["code"]: Polygon([to_floor(p) for p in sp["shape"]]).buffer(0).intersection(inside).difference(lobby)
         for sp in strokes["spaces"] if sp.get("shape")}
    square = ["P1-AUD-TAQUILLA", "P1-AUD-ANTESALA-N", "P1-AUD-ESCALERA"]
    sq = [{"code": c, "shape": [{"x": round(x), "y": round(y)} for x, y in list(R[c].exterior.coords)[:-1]]}
          for c in square]
    regular.regularize(sq, {"jog": 20, "share": 8}, set())
    for s in sq:
        R[s["code"]] = Polygon([(p["x"], p["y"]) for p in s["shape"]]).buffer(0).intersection(inside).difference(lobby)

    # The hall's east wall, fitted on the plan's lines: plumb from the front, then the diagonal.
    (ca, da), (cd, dd) = (fit_line(line_mask, (1322, 470), (1328, 630), 4),
                          fit_line(line_mask, (1316, 690), (1236, 850), 4))
    t = np.linalg.solve(np.array([da, -dd]).T, cd - ca)
    corner = tuple(ca + t[0] * da)

    def on(c, d, y):
        return c[0] + (y - c[1]) * d[0] / d[1], y
    east = Polygon([on(ca, da, 700), corner, on(cd, dd, 1070), (1500, 1070), (1500, 700)])
    R["P1-AUDITORIO-SONIA"] = R["P1-AUDITORIO-SONIA"].intersection(east)
    # The prop room: Brian's corners, its top along the plan's wall from the hall to the stair.
    R["P1-AUD-UTILERIA"] = Polygon([(2202, 1074), (2264, 1092), (2264, 1097), (2310, 1097), (2310, 1152), (2190, 1131)])

    order = ["P1-AUD-ESCENARIO", "P1-AUD-ESCALERA", "P1-AUD-TAQUILLA", "P1-AUD-UTILERIA", "P1-20", "P1-23",
             "P1-26", "P1-27", "P1-28", "P1-29", "P1-AUD-AUDIOVISUALES", "P1-AUD-ANTESALA-N"]
    taken, out = seats.buffer(1.5), {}
    for c in order:
        out[c] = largest(R[c].difference(taken))
        taken = taken.union(out[c])
    G = {c: largest(set_precision(g.buffer(0), 1.0))
         for c, g in zip(order, coverage_simplify([out[c] for c in order], TOL, simplify_boundary=True))}
    others = unary_union(list(G.values()))
    hall = R["P1-AUDITORIO-SONIA"].union(seats.buffer(1.5)).difference(others)
    # the pen's wobble straightened to 3 units, the seats' bands put back so none is cut
    hall = Polygon(largest(hall).exterior).simplify(3.0).union(seats.buffer(1.5)).difference(others)
    hall = set_precision(Polygon(largest(hall).exterior).simplify(1.0), 1.0).difference(others)
    G["P1-AUDITORIO-SONIA"] = largest(hall)
    others = unary_union(list(G.values()))
    s = box(2140, 780, 2345, 1100).intersection(inside).difference(others.buffer(0.01))
    s = s.difference(box(2310, 780, 2345, 1100))                             # the strip by the Calle 62 wall
    s = s.difference(Polygon([(2100, 1074 - 62 * 0.29), (2264, 1092), (2264, 1200), (2100, 1200)]))
    s = largest(s.buffer(-4, join_style="mitre").buffer(4, join_style="mitre")).simplify(2)
    G["P1-AUD-SALIDA-SUR"] = largest(set_precision(s, 1.0).difference(others))
    for c in list(G):
        o = 1.5 if c in ("P1-AUDITORIO-SONIA", "P1-AUD-SALIDA-SUR") else 1.0
        g = G[c].simplify(1.2 if c == "P1-AUDITORIO-SONIA" else 2.0)
        G[c] = largest(g.buffer(-o, join_style="mitre").buffer(o, join_style="mitre"))
    prio = order + ["P1-AUDITORIO-SONIA", "P1-AUD-SALIDA-SUR"]
    for _ in range(5):
        G = {c: largest(set_precision(g, 1.0)) for c, g in G.items()}
        moved = False
        for i, hi in enumerate(prio):
            for lo in prio[i + 1:]:
                if G[hi].intersection(G[lo]).area > 0.2:
                    G[lo] = largest(set_precision(G[lo].difference(G[hi]), 1.0))
                    moved = True
        if not moved:
            break

    # The covered patio: from a wall behind the wing to a wall inside the casa's lot line.
    wing = unary_union(list(G.values())).buffer(10, join_style="mitre")
    thick = box(1460, 600, 1545, 989)     # Brian: a thick wall between the lobby and the north anteroom
    patio = Polygon([(1516, 900), (2340, 1100), (2340, 1302), (1516, 1114)]).difference(wing).difference(lobby)
    patio = largest(patio.difference(thick).buffer(-8, join_style="mitre").buffer(8, join_style="mitre"))
    G["P1-PATIO-CUBIERTO"] = set_precision(patio.simplify(2), 1.0)

    rooms = {}
    doors = {sp["code"]: sp.get("doors") or [] for sp in strokes["spaces"]}
    for c, g in G.items():
        ring = g.exterior
        hung = []
        for d in doors.get(c, []):
            ends = [ring.interpolate(ring.project(Point(to_floor(e))))
                    for e in (d["from"], d["to"])]
            hung.append([[round(e.x), round(e.y)] for e in ends])
        rooms[c] = {"shape": [[round(x), round(y)] for x, y in list(ring.coords)[:-1]], "doors": hung}

    ys, xs = np.nonzero(seat_px)
    pts = np.stack([X0 + (xs + 0.5) / K, Y0 + (ys + 0.5) / K], 1)
    out_of_hall = (~contains_xy(Polygon(rooms["P1-AUDITORIO-SONIA"]["shape"]), pts[:, 0], pts[:, 1])).sum()
    print(f"seat pixels: {len(pts)}, outside the hall: {out_of_hall}", file=sys.stderr)
    json.dump(rooms, sys.stdout, indent=1)


if __name__ == "__main__":
    main()
