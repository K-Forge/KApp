#!/usr/bin/env python3
"""Lays a building's drawing on the ground and takes its footprint from the city's cadastre,
writing both into the building's seed file.

    # a building traced from its plans: fit the drawing to the cadastre
    scripts/plan-tracing/place.py ec --lot 008213024019 --floors P3:3,P4:4,P6:6,P7:7 --bearing 127 --footprint --write

    # a building nobody has drawn yet: lay a frame on its lot
    scripts/plan-tracing/place.py rh --lot 008213024004 --frame --footprint --write

The source is Bogota's reference map (IDECA, CC BY 4.0): the lot (layer Lote) and the parts of the
building on it (layer Construccion), each with how many floors it rises.

Fitting. The evacuation plans draw their streets schematically, so the drawing is fitted to the
building itself: `--floors P3:3` scores floor P3 against the parts of three floors or more. One
similarity - turn, scale, shift - is sought for all the floors named, the one whose rooms best
cover those parts, with twice the share of rooms that fall outside the lot taken off: a room on the
sidewalk is the error anybody sees first. A building extended since the cadastre was last updated
fits against its whole lot instead (`--whole-lot`). `--bearing` is where to start looking: the direction the
drawing's top faces, as near as the plans and the street the entrance is on tell.

A frame (`--frame`), for a building with no drawing: squared to the lot, the side nearest north at
the top, 30 units a metre, with two metres round the building. Every floor with nothing drawn on
it takes that frame, so whoever draws it later draws it in place.

Either way every floor's `top` becomes the quarter the bearing is nearest, and the floors nobody
has drawn take the drawn floors' frame. `--footprint` writes the parts as the building's footprint;
a part keeps the wing somebody gave it, matched by where it stands. Standard library only.
"""
import argparse
import collections
import json
import math
import os
import re
import urllib.parse
import urllib.request

SERVICE = 'https://serviciosgis.catastrobogota.gov.co/arcgis/rest/services/Mapa_Referencia/Mapa_Referencia/MapServer'
SEED = os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'backend', 'microservices', 'map-service',
                    'src', 'main', 'resources', 'db', 'seed', 'map')
RES = 0.5          # metres per cell of the grids the overlap is counted on
UNITS_PER_METRE = 30
FRAME_MARGIN = 2.0  # metres round a building a frame leaves
COMPASS = ['NORTH', 'EAST', 'SOUTH', 'WEST']


def query(layer, where):
    params = urllib.parse.urlencode({'where': where, 'outFields': '*', 'outSR': 4326, 'f': 'geojson'})
    request = urllib.request.Request(f'{SERVICE}/{layer}/query?{params}', headers={'User-Agent': 'KApp place script'})
    with urllib.request.urlopen(request, timeout=120) as answer:
        return json.load(answer).get('features', [])


def outer(feature):
    g = feature['geometry']
    return g['coordinates'][0] if g['type'] == 'Polygon' else g['coordinates'][0][0]


class Plane:
    """Metres east and south of a point - south, since the drawings' y grows down too."""

    def __init__(self, lat, lon):
        self.lat, self.lon = lat, lon
        self.kx, self.ky = math.cos(math.radians(lat)) * 111320.0, 110574.0

    def to(self, c):
        return ((c[0] - self.lon) * self.kx, -(c[1] - self.lat) * self.ky)

    def back(self, p):
        return {'lat': round(self.lat - p[1] / self.ky, 7), 'lon': round(self.lon + p[0] / self.kx, 7)}


class Grid:
    """Polygons filled on a grid of RES-metre cells, by scanlines."""

    def __init__(self, x0, y0, x1, y1):
        self.x0, self.y0 = x0, y0
        self.w, self.h = int((x1 - x0) / RES) + 1, int((y1 - y0) / RES) + 1

    def fill(self, polygons):
        cells = bytearray(self.w * self.h)
        for poly in polygons:
            n = len(poly)
            ys = [p[1] for p in poly]
            for r in range(max(0, int((min(ys) - self.y0) / RES)), min(self.h - 1, int((max(ys) - self.y0) / RES) + 1) + 1):
                y = self.y0 + (r + 0.5) * RES
                xs = sorted(xa + (y - ya) * (xb - xa) / (yb - ya)
                            for (xa, ya), (xb, yb) in ((poly[i], poly[(i + 1) % n]) for i in range(n))
                            if (ya <= y) != (yb <= y))
                for a, b in zip(xs[::2], xs[1::2]):
                    c0 = max(0, int((a - self.x0) / RES + 0.5))
                    c1 = min(self.w, int((b - self.x0) / RES + 0.5))
                    if c1 > c0:
                        cells[r * self.w + c0:r * self.w + c1] = b'\x01' * (c1 - c0)
        return cells


def lay(p, v):
    """A point of the drawing on the plane, for turn, scale and shift v."""
    th, s, tx, ty = v
    c, si = math.cos(th), math.sin(th)
    return (s * (c * p[0] - si * p[1]) + tx, s * (si * p[0] + c * p[1]) + ty)


def hull_area(points):
    """The area of the convex hull round the points (monotone chain)."""
    pts = sorted(set(points))
    if len(pts) < 3:
        return 0.0

    def half(seq):
        out = []
        for p in seq:
            while len(out) >= 2 and ((out[-1][0] - out[-2][0]) * (p[1] - out[-2][1])
                                     - (out[-1][1] - out[-2][1]) * (p[0] - out[-2][0])) <= 0:
                out.pop()
            out.append(p)
        return out
    hull = half(pts)[:-1] + half(reversed(pts))[:-1]
    return 0.5 * abs(sum(hull[i][0] * hull[i - 1][1] - hull[i - 1][0] * hull[i][1] for i in range(len(hull))))


def fit(floors, use, lots_m, parts_m, bearing, whole_lot=False):
    """lots_m: each lot's outline, in metres - a building may stand on more than one."""
    lot_points = [p for ring in lots_m for p in ring]
    xs = [p[0] for p in lot_points]
    ys = [p[1] for p in lot_points]
    grid = Grid(min(xs) - 25, min(ys) - 25, max(xs) + 25, max(ys) + 25)
    inside = grid.fill(lots_m)
    targets = {code: inside if whole_lot else grid.fill([r for n, r in parts_m if n >= reach]) for code, reach in use}
    rooms = {code: [[(p['x'], p['y']) for p in s['shape']] for s in floors[code]['spaces'] if s.get('shape')]
             for code, _ in use}

    def score(v):
        total = 0.0
        for code, _ in use:
            drawn = grid.fill([[lay(p, v) for p in r] for r in rooms[code]])
            target = targets[code]
            n, t = sum(drawn), sum(target)
            both = sum(1 for a, b in zip(drawn, target) if a and b)
            off = sum(1 for a, b in zip(drawn, inside) if a and not b)
            total += both / max(1, n + t - both) - 2 * off / max(1, n)
        return total / len(use)

    # A first scale from the hull round the lowest floor's rooms against the hull round what they
    # should cover. Hulls, not areas: corridors are seldom drawn as rooms, so the rooms' own area
    # falls short of the building's by as much as half, and the fit starts twice too big.
    code, reach = use[0]
    target_points = lot_points if whole_lot else [p for n, r in parts_m if n >= reach for p in r]
    s0 = math.sqrt(hull_area(target_points) / hull_area([p for r in rooms[code] for p in r]))
    cx = sum(p[0] for r in rooms[code] for p in r) / sum(len(r) for r in rooms[code])
    cy = sum(p[1] for r in rooms[code] for p in r) / sum(len(r) for r in rooms[code])
    reaching = lots_m if whole_lot else [r for n, r in parts_m if n >= reach]
    mx = sum(p[0] for r in reaching for p in r) / sum(len(r) for r in reaching)
    my = sum(p[1] for r in reaching for p in r) / sum(len(r) for r in reaching)

    best = None
    for start in (bearing - 10, bearing, bearing + 10):
        th = math.radians(start)
        v = [th, s0, mx - s0 * (math.cos(th) * cx - math.sin(th) * cy), my - s0 * (math.sin(th) * cx + math.cos(th) * cy)]
        here = score(v)
        steps = [math.radians(2), s0 * 0.03, 1.5, 1.5]
        while steps[2] >= 0.05:
            moved = False
            for k in range(4):
                for sign in (1, -1):
                    w = v[:]
                    w[k] += sign * steps[k]
                    sc = score(w)
                    if sc > here:
                        here, v, moved = sc, w, True
            if not moved:
                steps = [x * 0.5 for x in steps]
        print(f'from {start:.0f} deg: score {here:.3f}, bearing {math.degrees(v[0]) % 360:.2f}, {1 / v[1]:.1f} units a metre')
        if best is None or here > best[0]:
            best = (here, v)
    return best[1]


def frame(lots_m, parts_m):
    """A frame squared to the lots, the side nearest north at the top, round the building's parts."""
    lot_m = [p for ring in lots_m for p in ring]
    best = None
    for tenth in range(0, 900):
        a = math.radians(tenth / 10)
        u = [p[0] * math.cos(a) + p[1] * math.sin(a) for p in lot_m]
        w = [-p[0] * math.sin(a) + p[1] * math.cos(a) for p in lot_m]
        area = (max(u) - min(u)) * (max(w) - min(w))
        if best is None or area < best[0]:
            best = (area, tenth / 10)
    # The rectangle's four ways up; the one whose top faces nearest north.
    th = min((math.radians(best[1] + q * 90) for q in range(4)), key=lambda t: abs(((math.degrees(t) + 180) % 360) - 180))
    s = 1 / UNITS_PER_METRE
    points = [p for _, r in parts_m for p in r] or lot_m
    # The parts in the frame's axes: x along the rotated right, y along the rotated down.
    c, si = math.cos(th), math.sin(th)
    fx = [(p[0] * c + p[1] * si) for p in points]
    fy = [(-p[0] * si + p[1] * c) for p in points]
    x0, y0 = min(fx) - FRAME_MARGIN, min(fy) - FRAME_MARGIN
    x1, y1 = max(fx) + FRAME_MARGIN, max(fy) + FRAME_MARGIN
    origin = (x0 * c - y0 * si, x0 * si + y0 * c)
    width, height = round((x1 - x0) / s), round((y1 - y0) / s)
    return [th, s, origin[0], origin[1]], width, height


def centroid(ring):
    return (sum(p[0] for p in ring[:-1]) / (len(ring) - 1), sum(p[1] for p in ring[:-1]) / (len(ring) - 1))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('building', help='the seed file, as ec for ec.json')
    parser.add_argument('--lot', required=True, action='append', help="a lot the building stands on (LOTCODIGO); repeat for more")
    parser.add_argument('--floors', help='fit: floor:floors-reached pairs, as P3:3,P4:4')
    parser.add_argument('--bearing', type=float, help='fit: where to start - the bearing the top faces')
    parser.add_argument('--whole-lot', action='store_true',
                        help="fit: score against the whole lot, for a building the cadastre has not caught up with")
    parser.add_argument('--frame', action='store_true', help='no drawing: lay a frame on the lot instead')
    parser.add_argument('--footprint', action='store_true', help="write the cadastre's parts as the building's footprint")
    parser.add_argument('--write', action='store_true', help='write into the seed file')
    args = parser.parse_args()
    if not args.frame and (not args.floors or args.bearing is None):
        parser.error('a fit needs --floors and --bearing; a building with no drawing takes --frame')

    seed_path = os.path.normpath(os.path.join(SEED, f'{args.building}.json'))
    seed = json.load(open(seed_path, encoding='utf-8'))
    floors = {f['code']: f for f in seed['floors']}

    lots, parts = [], []
    for code in args.lot:
        lot = query(38, f"LOTCODIGO='{code}'")
        if not lot:
            raise SystemExit(f'lot {code}: not in the cadastre')
        lots.append(outer(lot[0]))
        parts += [(code, p) for p in query(39, f"LOTECODIGO='{code}'")]
    ring = lots[0]
    plane = Plane(sum(c[1] for c in ring) / len(ring), sum(c[0] for c in ring) / len(ring))
    lots_m = [[plane.to(c) for c in r] for r in lots]
    parts_m = [((p['properties'].get('CONNPISOS') or 0), [plane.to(c) for c in outer(p)]) for _, p in parts]

    drawn = [f for f in seed['floors'] if any(s.get('shape') for s in f['spaces'])]
    if args.frame:
        v, width, height = frame(lots_m, parts_m)
    else:
        use = [(code, int(reach)) for code, reach in (pair.split(':') for pair in args.floors.split(','))]
        v = fit(floors, use, lots_m, parts_m, args.bearing, args.whole_lot)
        width, height = collections.Counter((f['width'], f['height']) for f in drawn).most_common(1)[0][0]

    th, s, tx, ty = v
    placement = {'origin': plane.back((tx, ty)), 'bearing': round(math.degrees(th) % 360, 2), 'metresPerUnit': round(s, 5)}
    top = COMPASS[round(placement['bearing'] / 90) % 4]
    print(json.dumps(placement), 'top', top, 'frame', width, 'x', height)
    if not args.write:
        return

    for i, f in enumerate(seed['floors']):
        f['top'] = top
        if not any(sp.get('shape') for sp in f['spaces']):
            f['width'], f['height'] = width, height
        # In the order scripts/export-map-snapshot.py writes a floor, so an export changes nothing.
        order = ['code', 'level', 'name', 'status', 'accessibility', 'note', 'width', 'height', 'top',
                 'outline', 'corridors', 'spaces']
        seed['floors'][i] = {k: f[k] for k in order if k in f} | {k: v for k, v in f.items() if k not in order}
    laid = {}
    for key, value in seed.items():
        if key in ('placement', 'footprint'):
            continue
        laid[key] = value
        if key == 'wings':
            laid['placement'] = placement
    laid.setdefault('placement', placement)
    if args.footprint or seed.get('footprint'):
        before = seed.get('footprint', [])
        footprint = []
        for lot, p in parts:
            ring = [[round(c[0], 7), round(c[1], 7)] for c in outer(p)]
            if ring[0] != ring[-1]:
                ring.append(ring[0])
            part = {'lot': lot, 'floors': p['properties'].get('CONNPISOS') or 0,
                    'basements': p['properties'].get('CONNSOTANO') or 0}
            here = plane.to(centroid(ring))
            kept = [b for b in before if b.get('wing') and math.dist(plane.to(centroid(b['ring'])), here) < 1.5]
            if kept:
                part['wing'] = kept[0]['wing']
            part['ring'] = ring
            footprint.append(part)
        footprint.sort(key=lambda part: (-part['floors'], part['lot']))
        ordered = {}
        for key, value in laid.items():
            ordered[key] = value
            if key == 'placement':
                ordered['footprint'] = footprint
        laid = ordered
    # As scripts/export-map-snapshot.py writes it: a [lon, lat] pair on one line.
    text = json.dumps(laid, ensure_ascii=False, indent=2)
    text = re.sub(r'\[\s*(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)\s*\]', r'[\1, \2]', text)
    with open(seed_path, 'w', encoding='utf-8') as out:
        out.write(text + '\n')
    print(f'wrote {seed_path}')


if __name__ == '__main__':
    main()
