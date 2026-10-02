# overlaps.py <building.json>...: spaces of a floor whose shapes share area.
import json, sys
def inside(poly, x, y):
    hit = False
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i - 1], poly[i]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            hit = not hit
    return hit
for path in sys.argv[1:]:
    b = json.load(open(path))
    for f in b['floors']:
        W, H = f.get('width'), f.get('height')
        sp = [(s['code'], [(p['x'], p['y']) for p in s['shape']]) for s in f.get('spaces', []) if 'shape' in s]
        bad = [c for c, p in sp if any(not (0 <= x <= W and 0 <= y <= H) for x, y in p)]
        grid = {}
        for c, p in sp:
            xs = [q[0] for q in p]; ys = [q[1] for q in p]
            for y in range(int(min(ys)), int(max(ys)) + 1):
                for x in range(int(min(xs)), int(max(xs)) + 1):
                    if inside(p, x + 0.5, y + 0.5):
                        grid.setdefault((x, y), set()).add(c)
        over = {}
        for v in grid.values():
            if len(v) > 1:
                k = tuple(sorted(v)); over[k] = over.get(k, 0) + 1
        if over or bad:
            print(b['code'], f['code'], 'overlaps', over, 'outside', bad)
print('checked')
