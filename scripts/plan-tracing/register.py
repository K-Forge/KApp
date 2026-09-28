#!/usr/bin/env python3
"""Moves a traced floor from its evacuation plan's proportions onto the building's, as the cadastre
measures them.

    scripts/plan-tracing/register.py specs/ec-register.json P1           # print what would move
    scripts/plan-tracing/register.py specs/ec-register.json P1 --write   # write it into the seed

The evacuation plans (PE) are drawn by eye. One building's plans agree with each other, since they
were drawn from the same base, but not with the building: the Edificio Central's draw its tower 17%
and its south wing 15% narrower than they are. place.py can only turn, scale and shift a drawing,
so it splits the difference and every part ends up a few metres off. This stretches each stretch of
the drawing to its true size instead, keeping the arrangement the plan shows.

The spec:

    {"building": "ec",
     "x": [[64, 53], [638, 625], ...],     drawing x -> true x: the same wall in the plans and in
                                           the cadastre, for the whole building. Between two pairs
                                           x is stretched evenly; beyond the last, as the last
                                           stretch was.
     "y": [[...]],                         the same for y, when the plans need it
     "floors": {"P1": {
        "place": {"x": [0.76, 480], "y": [0.91, 211], "why": "..."},
                                           a floor whose plan was laid into the frame out of place:
                                           x -> 0.76 x + 480 before anything else, and y likewise
        "drop": {"P7-02": "..."},          traced shapes that are not this floor's, with why
        "becomes": {"P6-26": "P6-TERRAZA-SUR"},
                                           a traced shape that is an inventoried space
        "width": 2400,                     the floor's new width, when the stretched floor needs
                                           more room; a floor already this wide is taken as
                                           registered, and refused
        "groups": [{"spaces": ["P1-AUD*"], "y": [[723, 590], ...], "why": "..."}],
                                           a part of the floor the plan draws out of proportion
                                           with the rest: its own y (or x) pairs
        "limits": [{"space": "P1-24", "below": 2120, "why": "..."},
                   {"space": "P1-24", "notch": [986, 1911], "why": "..."}]
                                           after moving: a room cut off past y = 2120 ("below", or
                                           "above"), x ("left of", "right of"), or a rectangle with
                                           its corner beyond (986, 1911) taken out ("notch"); a
                                           door on a wall that moves in goes with it
        "corridors": [{"code": "PAS-CENTRAL", "name": "...", "color": "#5B8DEF",
                       "path": [[660, 540], [660, 1870]]}]
                                           walkable routes to add, in the drawing's units - the
                                           evacuation plans paint them as green arrows
     }}}

Coordinates in "groups" and "limits" are the true ones except the pairs' first halves, which are
the drawing's. Doors move with their rooms and are dropped when their wall is cut away. The floor
keeps its status: it is still drawn from photos. Standard library only.
"""
import argparse
import fnmatch
import json
import os
import re
import sys
from bisect import bisect_right

import margin

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RESOURCES = os.path.join(REPO, "app", "backend", "microservices", "map-service", "src", "main", "resources")
SEED = os.path.join(RESOURCES, "db", "seed", "map")
GROUND = os.path.join(RESOURCES, "db", "ground")
UNNAMED = ("Sin identificar", "Escalera por identificar")


def stretch(pairs, v):
    """Piecewise-linear through the pairs, extended past the ends by the end stretches."""
    if not pairs:
        return v
    if len(pairs) == 1:
        return v + pairs[0][1] - pairs[0][0]
    keys = [p[0] for p in pairs]
    i = min(max(bisect_right(keys, v) - 1, 0), len(pairs) - 2)
    (a, fa), (b, fb) = pairs[i], pairs[i + 1]
    return fa + (v - a) * (fb - fa) / (b - a)


def split(poly, xs, ys):
    """The polygon with a corner wherever an edge crosses a breakpoint: the stretch bends a
    straight edge there, and an edge kept straight across it cuts into the room next door."""
    out = []
    for i, p in enumerate(poly):
        q = poly[(i + 1) % len(poly)]
        out.append(p)
        ts = [(v - p[0]) / (q[0] - p[0]) for v in xs if q[0] != p[0] and min(p[0], q[0]) < v < max(p[0], q[0])]
        ts += [(v - p[1]) / (q[1] - p[1]) for v in ys if q[1] != p[1] and min(p[1], q[1]) < v < max(p[1], q[1])]
        out += [(p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])) for t in sorted(ts)]
    return out


def clip(poly, keep):
    """Sutherland-Hodgman against one half-plane; keep(p) is how far p is inside (>= 0 kept)."""
    out = []
    for i, p in enumerate(poly):
        q = poly[(i + 1) % len(poly)]
        dp, dq = keep(p), keep(q)
        if dp >= 0:
            out.append(p)
        if (dp >= 0) != (dq >= 0):
            t = dp / (dp - dq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out


def notch(poly, corner):
    """A rectangle with its corner beyond `corner` taken out: an L, the rest of it."""
    xs = sorted({p[0] for p in poly}); ys = sorted({p[1] for p in poly})
    if len(poly) != 4 or len(xs) != 2 or len(ys) != 2:
        sys.exit(f"notch: only a rectangle can be notched, not {poly}")
    (x0, x1), (y0, y1), (cx, cy) = xs, ys, corner
    if not (x0 < cx < x1 and y0 < cy < y1):
        sys.exit(f"notch: {corner} is not inside {poly}")
    return [(x0, y0), (x1, y0), (x1, cy), (cx, cy), (cx, y1), (x0, y1)]


def on_edge(poly, a, b, tolerance=1.5):
    """Whether segment a-b lies along one of the polygon's edges."""
    def near(p, q, r):
        (px, py), (qx, qy), (rx, ry) = p, q, r
        dx, dy = rx - qx, ry - qy
        length = (dx * dx + dy * dy) ** 0.5 or 1e-9
        cross = abs(dx * (qy - py) - dy * (qx - px)) / length
        t = ((px - qx) * dx + (py - qy) * dy) / (length * length)
        return cross <= tolerance and -0.01 <= t <= 1.01
    return any(near(a, poly[i], poly[(i + 1) % len(poly)]) and near(b, poly[i], poly[(i + 1) % len(poly)])
               for i in range(len(poly)))


def tidy(poly):
    """Whole units, no repeated points, no points in the middle of a straight edge."""
    pts = []
    for x, y in poly:
        p = (round(x), round(y))
        if not pts or pts[-1] != p:
            pts.append(p)
    if len(pts) > 1 and pts[0] == pts[-1]:
        pts.pop()
    changed = True
    while changed and len(pts) > 3:
        changed = False
        for i in range(len(pts)):
            a, b, c = pts[i - 1], pts[i], pts[(i + 1) % len(pts)]
            if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) == 0:
                pts.pop(i)
                changed = True
                break
    return pts


def inside(poly, p):
    """Strictly inside: a point on the outline is not."""
    x, y = p
    for i in range(len(poly)):
        (ax, ay), (bx, by) = poly[i - 1], poly[i]
        if (bx - ax) * (y - ay) == (by - ay) * (x - ax) and min(ax, bx) <= x <= max(ax, bx) and min(ay, by) <= y <= max(ay, by):
            return False
    hit = False
    for i in range(len(poly)):
        (ax, ay), (bx, by) = poly[i - 1], poly[i]
        if (ay > y) != (by > y) and x < ax + (y - ay) * (bx - ax) / (by - ay):
            hit = not hit
    return hit


def settle(shapes):
    """A corner that sat on a neighbour's slanted wall can round to half a unit inside it, and the
    load refuses rooms that share area. Such a corner steps back out, to the nearest whole point
    that is in no other room."""
    for i, shape in enumerate(shapes):
        others = [o for j, o in enumerate(shapes) if j != i]
        for k, p in enumerate(shape):
            if not any(inside(o, p) for o in others):
                continue
            around = sorted(((p[0] + dx, p[1] + dy) for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)),
                            key=lambda q: (q[0] - p[0]) ** 2 + (q[1] - p[1]) ** 2)
            shape[k] = next((q for q in around if not any(inside(o, q) for o in others)), p)
    return shapes


def fit(spec, rules, building, floor, code):
    """The floor fitted to the building's margin: the stairs and lifts that climb the building put
    where they are on every floor, each room a wall inside the margin and clear of them, terraces
    on the roof of the floor below, and the rooms a spec names stretched out to the facade."""
    notes = []
    width, height, level = floor["width"], floor["height"], floor["level"]
    levels = sorted(f["level"] for f in building["floors"])
    below = max((lv for lv in levels if lv < level), default=level - 1)
    ground = margin.ground_for(building.get("campus", ""), GROUND)
    _, loose, tight = margin.floor_margin(building, width, height, level, ground)
    _, loose_below, tight_below = margin.floor_margin(building, width, height, below, ground)

    spaces = {sp["code"]: sp for sp in floor["spaces"]}
    cores = margin.blank(tight)
    stacked = set()
    for role, stack in spec.get("stack", {}).items():
        target = stack["spaces"].get(code)
        if not target:
            continue
        if target not in spaces:
            sys.exit(f"{code} has no space {target} to stack as {role}")
        shape = [(round(x), round(y)) for x, y in stack["shape"]]
        sp = spaces[target]
        sp["shape"] = [{"x": x, "y": y} for x, y in shape]
        sp["doors"] = [d for d in sp.get("doors") or [] if on_edge(shape, (d["from"]["x"], d["from"]["y"]), (d["to"]["x"], d["to"]["y"]))]
        cores.paint(shape)
        stacked.add(target)
        notes.append(f"  {target} stacked as {role}")

    unplaced, gone = [], []
    for sp in list(floor["spaces"]):
        if not sp.get("shape") or sp["code"] in stacked:
            continue
        poly = [(p["x"], p["y"]) for p in sp["shape"]]
        terrace = sp.get("typeCode") == "TERRACE"
        cut = margin.clip(poly, loose_below if terrace else loose, tight_below if terrace else tight, cores)
        if cut is poly:
            continue
        if cut is None:
            if sp.get("name") in UNNAMED and not sp.get("doorCode"):
                floor["spaces"].remove(sp)
                gone.append(sp["code"])
            else:
                sp["shape"], sp["doors"] = None, []
                unplaced.append(sp["code"])
            continue
        shape = tidy(cut)
        sp["shape"] = [{"x": x, "y": y} for x, y in shape]
        sp["doors"] = [d for d in sp.get("doors") or [] if on_edge(shape, (d["from"]["x"], d["from"]["y"]), (d["to"]["x"], d["to"]["y"]))]
    if gone:
        notes.append(f"  outside the building, dropped: {', '.join(gone)}")
    if unplaced:
        notes.append(f"  outside the building, left to place: {', '.join(unplaced)}")
    clipped = [sp["code"] for sp in floor["spaces"] if sp.get("shape")]

    for target in rules.get("extend", {}).get("left", []):
        sp = spaces.get(target)
        if not sp or not sp.get("shape"):
            sys.exit(f"{code} has no drawn space {target} to extend")
        others = margin.blank(tight)
        for other in floor["spaces"]:
            if other is not sp and other.get("shape"):
                others.paint([(p["x"], p["y"]) for p in other["shape"]])
        poly = [(p["x"], p["y"]) for p in sp["shape"]]
        grown = margin.extend_left(poly, tight, others)
        if grown is not poly:
            sp["shape"] = [{"x": round(x), "y": round(y)} for x, y in grown]
            notes.append(f"  {target} out to the facade: x {min(p[0] for p in poly):.0f} -> {min(p[0] for p in grown):.0f}")
    notes.append(f"  fitted to the margin: {len(clipped)} room(s) drawn")
    return notes


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("spec", help="the building's registration spec")
    parser.add_argument("floor", help="the floor to move, by code")
    parser.add_argument("--write", action="store_true", help="write the moved floor into the seed")
    args = parser.parse_args()

    spec = json.load(open(args.spec))
    path = os.path.join(SEED, spec["building"] + ".json")
    building = json.load(open(path))
    floor = next((f for f in building["floors"] if f["code"] == args.floor), None)
    if floor is None:
        sys.exit(f"{spec['building']} has no floor {args.floor}")
    rules = spec["floors"].get(args.floor, {})
    width = rules.get("width")
    if width and floor["width"] == width:
        sys.exit(f"{args.floor} is {width} wide already: it has been registered")

    def group(code):
        return next((g for g in rules.get("groups", []) if any(fnmatch.fnmatch(code, s) for s in g["spaces"])), {})

    # A floor whose plan was laid into the building's frame out of place moves as a whole first.
    (sx, tx), (sy, ty) = rules.get("place", {}).get("x", (1, 0)), rules.get("place", {}).get("y", (1, 0))
    place = lambda p: (sx * p[0] + tx, sy * p[1] + ty)

    # Shapes the plan draws that are not this floor's - a wing's silhouette above its roof - go;
    # a traced shape that is an inventoried space takes that space's name.
    dropped = set(rules.get("drop", {}))
    for code in dropped:
        if not any(sp["code"] == code for sp in floor["spaces"]):
            sys.exit(f"{args.floor} has no space {code} to drop")
    floor["spaces"] = [sp for sp in floor["spaces"] if sp["code"] not in dropped]
    for traced, named in rules.get("becomes", {}).items():
        a = next((sp for sp in floor["spaces"] if sp["code"] == traced), None)
        b = next((sp for sp in floor["spaces"] if sp["code"] == named), None)
        if not a or not b or b.get("shape"):
            sys.exit(f"{args.floor}: {traced} cannot become {named}")
        b["shape"], b["doors"] = a["shape"], a.get("doors") or []
        floor["spaces"].remove(a)

    # A name the plan's tracing gave a shape that is not that space: the shape goes back to being a box.
    for code, why in rules.get("unname", {}).items():
        sp = next((x for x in floor["spaces"] if x["code"] == code), None)
        if not sp:
            sys.exit(f"{args.floor} has no space {code} to unname")
        taken = {x["code"] for x in floor["spaces"]}
        n = 1
        while f"{args.floor}-{n:02d}" in taken:
            n += 1
        sp.update({"code": f"{args.floor}-{n:02d}", "doorCode": None, "wing": None, "name": UNNAMED[0],
                   "typeCode": "OTHER", "aliases": []})
        for k in ("doorCode", "wing"):
            sp.pop(k, None)

    moved = [f"  dropped {code}" for code in sorted(dropped)]
    moved += [f"  {code} is a box again" for code in rules.get("unname", {})]
    moved += [f"  {traced} is {named}" for traced, named in rules.get("becomes", {}).items()]
    for space in floor["spaces"]:
        g = group(space["code"])
        fx, fy = g.get("x", spec.get("x", [])), g.get("y", spec.get("y", []))
        move = lambda p: (stretch(fx, p[0]), stretch(fy, p[1]))
        if not space.get("shape"):
            continue
        before = [(p["x"], p["y"]) for p in space["shape"]]
        shape = [move(p) for p in split([place(p) for p in before], [a for a, _ in fx], [a for a, _ in fy])]
        doors = [(move(place((d["from"]["x"], d["from"]["y"]))), move(place((d["to"]["x"], d["to"]["y"]))))
                 for d in space.get("doors") or []]
        for limit in (lm for lm in rules.get("limits", []) if lm["space"] == space["code"]):
            # A wall moved in takes its doors with it.
            if "below" in limit:
                v = limit["below"]
                shape = clip(shape, lambda p: v - p[1])
                doors = [tuple((x, min(y, v)) for x, y in d) for d in doors]
            if "above" in limit:
                v = limit["above"]
                shape = clip(shape, lambda p: p[1] - v)
                doors = [tuple((x, max(y, v)) for x, y in d) for d in doors]
            if "left of" in limit:
                v = limit["left of"]
                shape = clip(shape, lambda p: v - p[0])
                doors = [tuple((min(x, v), y) for x, y in d) for d in doors]
            if "right of" in limit:
                v = limit["right of"]
                shape = clip(shape, lambda p: p[0] - v)
                doors = [tuple((max(x, v), y) for x, y in d) for d in doors]
            if "notch" in limit:
                shape = notch(tidy(shape), limit["notch"])
        shape = tidy(shape)
        kept = [d for d in doors if on_edge(shape, *d)]
        space["shape"] = [{"x": x, "y": y} for x, y in shape]
        if space.get("doors") is not None:
            space["doors"] = [{"from": {"x": round(a[0]), "y": round(a[1])}, "to": {"x": round(b[0]), "y": round(b[1])}}
                              for a, b in kept]
        xs = [p[0] for p in shape]; ys = [p[1] for p in shape]
        moved.append(f"  {space['code']:<24} x {min(p[0] for p in before):>5}-{max(p[0] for p in before):<5} -> {min(xs):>5}-{max(xs):<5}"
                     f" y {min(p[1] for p in before):>5}-{max(p[1] for p in before):<5} -> {min(ys):>5}-{max(ys):<5}"
                     + (f"  ({len(doors) - len(kept)} door(s) dropped)" if len(kept) < len(doors) else ""))
    # The floor's corridors move with it, and the spec's are added, drawn on the plan as they are.
    def carry(path):
        pts = [move(place(p)) for p in path]
        return [{"x": round(x), "y": round(y)} for x, y in pts]
    fx, fy = spec.get("x", []), spec.get("y", [])
    move = lambda p: (stretch(fx, p[0]), stretch(fy, p[1]))
    corridors = [dict(c, path=carry([(p["x"], p["y"]) for p in c["path"]])) for c in floor.get("corridors") or []]
    have = {c["code"] for c in corridors}
    for c in rules.get("corridors", []):
        if c["code"] not in have:
            corridors.append({"code": c["code"], "name": c["name"], "color": c.get("color", "#5B8DEF"),
                              "path": carry([tuple(p) for p in c["path"]])})
            moved.append(f"  corridor {c['code']:<15} {len(c['path'])} points")
    if corridors:
        floor["corridors"] = corridors
    # In the order scripts/export-map-snapshot.py writes a floor, so an export changes nothing.
    order = ["code", "level", "name", "status", "accessibility", "note", "width", "height", "top",
             "outline", "corridors", "spaces"]
    laid = {k: floor[k] for k in order if k in floor} | {k: v for k, v in floor.items() if k not in order}
    floor.clear()
    floor.update(laid)

    if width:
        floor["width"] = width
    if spec.get("fit"):
        moved += fit(spec, rules, building, floor, args.floor)

    drawn = [s for s in floor["spaces"] if s.get("shape")]
    settled = settle([[(p["x"], p["y"]) for p in s["shape"]] for s in drawn])
    for space, shape in zip(drawn, settled):
        space["shape"] = [{"x": x, "y": y} for x, y in shape]
    everything = [p for s in floor["spaces"] for p in s.get("shape") or []] + [p for c in corridors for p in c["path"]]
    widest = max((p["x"] for p in everything), default=0)
    tallest = max((p["y"] for p in everything), default=0)
    if widest > floor["width"] or tallest > floor["height"]:
        sys.exit(f"{args.floor} reaches ({widest}, {tallest}), past its {floor['width']} x {floor['height']} frame: give it a width")
    if min((p[c] for p in everything for c in ("x", "y")), default=0) < 0:
        sys.exit(f"{args.floor} moves past the frame's top or left edge")
    print(f"{spec['building']} {args.floor}: {len(moved)} space(s) moved, frame {floor['width']} x {floor['height']}")
    print("\n".join(moved))
    if args.write:
        # As scripts/export-map-snapshot.py writes it: a [lon, lat] pair on one line.
        text = json.dumps(building, ensure_ascii=False, indent=2)
        text = re.sub(r"\[\s*(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)\s*\]", r"[\1, \2]", text)
        with open(path, "w", encoding="utf-8") as out:
            out.write(text + "\n")
        print(f"wrote {os.path.relpath(path, REPO)}")


if __name__ == "__main__":
    main()
