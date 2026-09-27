#!/usr/bin/env python3
"""Lays a building's drawing on the ground: finds its placement - where the drawing's top-left
corner is, the bearing its top edge faces and how many metres one unit is - from the city's
cadastre, and writes it into the building's seed file.

    scripts/plan-tracing/place.py ec --lot 008213024019 --floors P3:3,P4:4,P6:6,P7:7 --bearing 125 [--write]

The evacuation plans draw their streets schematically, so the drawing is fitted to the building
itself. The cadastre (Bogota's reference map, IDECA, CC BY 4.0) records each part of a building
with how many floors it rises: `--floors P3:3` scores floor P3 against the parts of three floors or
more. One similarity - turn, scale, shift - is sought for all the floors named, the one whose
rooms best cover those parts, with twice the share of rooms that fall outside the lot taken off:
a room on the sidewalk is the error anybody sees first. `--bearing` is where to start looking,
the direction the drawing's top faces as near as the plans tell. Standard library only.
"""
import argparse
import json
import math
import os
import urllib.parse
import urllib.request

SERVICE = 'https://serviciosgis.catastrobogota.gov.co/arcgis/rest/services/Mapa_Referencia/Mapa_Referencia/MapServer'
SEED = os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'backend', 'microservices', 'map-service',
                    'src', 'main', 'resources', 'db', 'seed', 'map')
RES = 0.5          # metres per cell of the grids the overlap is counted on


def query(layer, where):
    params = urllib.parse.urlencode({'where': where, 'outFields': '*', 'outSR': 4326, 'f': 'geojson'})
    request = urllib.request.Request(f'{SERVICE}/{layer}/query?{params}', headers={'User-Agent': 'KApp place script'})
    with urllib.request.urlopen(request, timeout=120) as answer:
        return json.load(answer).get('features', [])


def outer(feature):
    g = feature['geometry']
    return g['coordinates'][0] if g['type'] == 'Polygon' else g['coordinates'][0][0]


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


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('building', help='the seed file, as ec for ec.json')
    parser.add_argument('--lot', required=True, help="the building's lot code in the cadastre (LOTCODIGO)")
    parser.add_argument('--floors', required=True, help='floor:floors-reached pairs, as P3:3,P4:4')
    parser.add_argument('--bearing', type=float, required=True, help='where to start: the bearing the top faces')
    parser.add_argument('--write', action='store_true', help="write the placement into the seed file")
    args = parser.parse_args()

    seed_path = os.path.normpath(os.path.join(SEED, f'{args.building}.json'))
    seed = json.load(open(seed_path, encoding='utf-8'))
    floors = {f['code']: f for f in seed['floors']}
    use = [(code, int(reach)) for code, reach in (pair.split(':') for pair in args.floors.split(','))]

    lot = query(38, f"LOTCODIGO='{args.lot}'")
    parts = query(39, f"LOTECODIGO='{args.lot}'")
    if not lot or not parts:
        raise SystemExit(f'lot {args.lot}: nothing in the cadastre')
    ring = outer(lot[0])
    lat0 = sum(c[1] for c in ring) / len(ring)
    lon0 = sum(c[0] for c in ring) / len(ring)
    kx, ky = math.cos(math.radians(lat0)) * 111320.0, 110574.0

    def local(c):        # metres east, and south - the drawing's y grows down too
        return ((c[0] - lon0) * kx, -(c[1] - lat0) * ky)

    lot_m = [local(c) for c in ring]
    parts_m = [((p['properties'].get('CONNPISOS') or 0), [local(c) for c in outer(p)]) for p in parts]
    xs = [p[0] for p in lot_m]
    ys = [p[1] for p in lot_m]
    grid = Grid(min(xs) - 25, min(ys) - 25, max(xs) + 25, max(ys) + 25)
    inside = grid.fill([lot_m])
    targets = {code: grid.fill([r for n, r in parts_m if n >= reach]) for code, reach in use}
    rooms = {code: [[(p['x'], p['y']) for p in s['shape']] for s in floors[code]['spaces'] if s.get('shape')]
             for code, _ in use}

    def lay(p, v):
        th, s, tx, ty = v
        c, si = math.cos(th), math.sin(th)
        return (s * (c * p[0] - si * p[1]) + tx, s * (si * p[0] + c * p[1]) + ty)

    def score(v):
        total = 0.0
        for code, _ in use:
            drawn = grid.fill([[lay(p, v) for p in r] for r in rooms[code]])
            target = targets[code]
            n, t = sum(drawn), sum(target)
            both = sum(1 for a, b in zip(drawn, target) if a and b)
            off = sum(1 for a, b in zip(drawn, inside) if a and not b)
            total += both / (n + t - both) - 2 * off / max(1, n)
        return total / len(use)

    # A first scale from areas: the rooms of the lowest floor named against the parts reaching it.
    code, reach = use[0]
    area_rooms = sum(0.5 * abs(sum(r[i][0] * r[i - 1][1] - r[i - 1][0] * r[i][1] for i in range(len(r)))) for r in rooms[code])
    area_parts = sum(targets[code]) * RES * RES
    s0 = math.sqrt(area_parts / area_rooms)
    cx = sum(p[0] for r in rooms[code] for p in r) / sum(len(r) for r in rooms[code])
    cy = sum(p[1] for r in rooms[code] for p in r) / sum(len(r) for r in rooms[code])
    mx = sum(p[0] for n, r in parts_m if n >= reach for p in r) / sum(len(r) for n, r in parts_m if n >= reach)
    my = sum(p[1] for n, r in parts_m if n >= reach for p in r) / sum(len(r) for n, r in parts_m if n >= reach)

    best = None
    for start in (args.bearing - 10, args.bearing, args.bearing + 10):
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

    th, s, tx, ty = best[1]
    placement = {'origin': {'lat': round(lat0 - ty / ky, 7), 'lon': round(lon0 + tx / kx, 7)},
                 'bearing': round(math.degrees(th) % 360, 2), 'metresPerUnit': round(s, 5)}
    print(json.dumps(placement))
    if args.write:
        laid = {}
        for key, value in seed.items():
            if key != 'placement':
                laid[key] = value
            if key == 'wings':
                laid['placement'] = placement
        if 'placement' not in laid:
            laid['placement'] = placement
        with open(seed_path, 'w', encoding='utf-8') as out:
            json.dump(laid, out, ensure_ascii=False, indent=2)
            out.write('\n')
        print(f'wrote {seed_path}')


if __name__ == '__main__':
    main()
