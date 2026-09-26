#!/usr/bin/env python3
"""
Puts a traced floor into the map snapshot, with every room it can name.

    scripts/plan-tracing/build-floor.py <trace.json> <spec.json>

<trace.json> is what trace.py wrote for the floor; <spec.json> is the same spec it read, whose
"floor" part says where the result goes and which room is which:

    "floor": {
      "building": "CPC1",                  the building's file in db/seed/map, by code
      "code": "P1",                        the floor in it
      "spaces": {"101": [104, 179], ...},  a space of the floor's inventory, by code, and a point
                                           inside the room (or staircase) the plan draws for it
      "split": [{"at": [560, 440], "x": 646}],
                                           rooms the plan draws as one that are two: the room
                                           under "at", cut along x = 646 (or "y")
      "extra": {"P1-COFFEE-BREAK": [[x, y], ...]}
                                           an outline for a space the plan does not paint
      "top": "EAST"                        which way the drawing's top edge faces, when known
    }

Every traced room nobody named is added as "Sin identificar", and every staircase as "Escalera
por identificar", for the floor editor to match with the inventory on site. Every space of the inventory the spec does not place is kept, undrawn.
The floor's size becomes the trace's, and corridors drawn on the old drawing - in other units -
go. The floor stays DRAFT: drawn from photos, not yet walked.

Standard library only.
"""
import json
import os
import sys

UNNAMED = "Sin identificar"
UNNAMED_STAIRS = "Escalera por identificar"

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SEED = os.path.join(REPO, "app", "backend", "microservices", "map-service", "src", "main", "resources",
                    "db", "seed", "map")


def inside(poly, x, y):
    hit = False
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i - 1], poly[i]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            hit = not hit
    return hit


def clip(poly, axis, at, keep_below):
    """The part of a polygon on one side of the line axis = at (Sutherland-Hodgman)."""
    k = 0 if axis == "x" else 1

    def keeps(p):
        return p[k] <= at if keep_below else p[k] >= at

    out = []
    for i in range(len(poly)):
        a, b = poly[i - 1], poly[i]
        if keeps(b):
            if not keeps(a):
                out.append(cross(a, b, k, at))
            out.append(b)
        elif keeps(a):
            out.append(cross(a, b, k, at))
    # Corners in a straight line add nothing.
    tidy = []
    for i, p in enumerate(out):
        q, r = out[i - 1], out[(i + 1) % len(out)]
        if p == q or (q[0] == p[0] == r[0]) or (q[1] == p[1] == r[1]):
            continue
        tidy.append(p)
    return tidy


def cross(a, b, k, at):
    t = (at - a[k]) / (b[k] - a[k])
    p = [a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])]
    p[k] = at
    return [round(p[0]), round(p[1])]


def on_outline(poly, point):
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        dx, dy = x1 - x0, y1 - y0
        length = dx * dx + dy * dy
        t = 0 if length == 0 else max(0, min(1, ((point[0] - x0) * dx + (point[1] - y0) * dy) / length))
        if abs(point[0] - x0 - t * dx) <= 1 and abs(point[1] - y0 - t * dy) <= 1:
            return True
    return False


def points(poly):
    return [{"x": int(x), "y": int(y)} for x, y in poly]


def main():
    trace = json.load(open(sys.argv[1]))
    spec = json.load(open(sys.argv[2]))["floor"]

    # Every outline the plan gives, with its doors; stairs have none drawn.
    shapes = [{"shape": r["shape"], "doors": r["doors"]} for r in trace["rooms"]]
    shapes += [{"shape": s["shape"], "doors": [], "stairs": True} for s in trace.get("stairs", [])]

    for cut in spec.get("split", []):
        x, y = cut["at"]
        axis = "x" if "x" in cut else "y"
        room = next((s for s in shapes if inside(s["shape"], x, y)), None)
        if room is None:
            sys.exit(f"split {cut}: no room at {cut['at']}")
        shapes.remove(room)
        for below in (True, False):
            part = clip(room["shape"], axis, cut[axis], below)
            if len(part) >= 3:
                shapes.append({"shape": part, "stairs": room.get("stairs", False), "doors": [d for d in room["doors"]
                                                          if on_outline(part, d[0]) and on_outline(part, d[1])]})

    path = os.path.join(SEED, spec["building"].lower() + ".json")
    building = json.load(open(path, encoding="utf-8"))
    floor = next(f for f in building["floors"] if f["code"] == spec["code"])
    # The boxes an earlier run could not name are that run's, not the inventory's: they go, and
    # whatever is still unnamed now comes back under the same codes.
    inventory = {s["code"]: s for s in floor.get("spaces", [])
                 if not (s.get("name") in (UNNAMED, UNNAMED_STAIRS) and not s.get("doorCode"))}

    named = {}
    for code, (x, y) in spec.get("spaces", {}).items():
        if code not in inventory:
            sys.exit(f"{code} is not in the inventory of {spec['building']} {spec['code']}")
        room = next((s for s in shapes if inside(s["shape"], x, y)), None)
        if room is None:
            sys.exit(f"{code}: no traced room at {[x, y]}")
        if id(room) in named:
            sys.exit(f"{code}: the room at {[x, y]} is already {named[id(room)][0]}")
        named[id(room)] = (code, room)

    for space in inventory.values():
        space.pop("shape", None)
        space.pop("doors", None)
    for code, room in named.values():
        inventory[code]["shape"] = points(room["shape"])
        if room["doors"]:
            inventory[code]["doors"] = [{"from": points([d[0]])[0], "to": points([d[1]])[0]} for d in room["doors"]]
    for code, outline in spec.get("extra", {}).items():
        inventory[code]["shape"] = points(outline)

    # The rooms nobody could name yet, numbered after the floor like the editor numbers them.
    taken = {code.upper() for code in inventory}
    for name in os.listdir(SEED):
        if not name.endswith(".json"):
            continue
        other = json.load(open(os.path.join(SEED, name), encoding="utf-8"))
        for f in other["floors"]:
            if not (other["code"] == spec["building"] and f["code"] == spec["code"]):
                taken.update(s["code"].upper() for s in f.get("spaces", []))
    n = 0
    unnamed = [s for s in shapes if id(s) not in named]
    unnamed.sort(key=lambda s: (min(p[1] for p in s["shape"]), min(p[0] for p in s["shape"])))
    for room in unnamed:
        while True:
            n += 1
            code = f"{spec['code']}-{n:02d}"
            if code.upper() not in taken:
                break
        taken.add(code.upper())
        # Stairs are drawn as stairs even before anyone knows which ones they are.
        stairs = room.get("stairs", False)
        space = {"code": code, "name": UNNAMED_STAIRS if stairs else UNNAMED,
                 "typeCode": "STAIRS" if stairs else "OTHER", "aliases": [], "shape": points(room["shape"])}
        if room["doors"]:
            space["doors"] = [{"from": points([d[0]])[0], "to": points([d[1]])[0]} for d in room["doors"]]
        inventory[code] = space

    floor["width"], floor["height"] = trace["width"], trace["height"]
    if spec.get("top"):
        floor["top"] = spec["top"]
    floor["outline"] = []
    floor.pop("corridors", None)
    floor["status"] = "DRAFT"
    order = ["code", "doorCode", "wing", "name", "typeCode", "aliases", "shape", "doors", "accessVia",
             "accessibility", "note", "capacity"]
    floor["spaces"] = [dict(sorted(s.items(), key=lambda kv: order.index(kv[0]) if kv[0] in order else 99))
                       for s in sorted(inventory.values(), key=lambda s: s["code"])]
    keys = ["code", "level", "name", "status", "accessibility", "note", "width", "height", "top", "outline",
            "corridors", "spaces"]
    floor_items = sorted(floor.items(), key=lambda kv: keys.index(kv[0]) if kv[0] in keys else 99)
    floor.clear()
    floor.update(floor_items)

    with open(path, "w", encoding="utf-8") as out:
        json.dump(building, out, ensure_ascii=False, indent=2)
        out.write("\n")
    drawn = sum(1 for s in floor["spaces"] if "shape" in s)
    print(f"{spec['building']} {spec['code']}: {trace['width']}x{trace['height']}, {drawn} drawn "
          f"({len(named) + len(spec.get('extra', {}))} named, {len(unnamed)} to identify), "
          f"{len(floor['spaces']) - drawn} in the inventory undrawn")


if __name__ == "__main__":
    main()
