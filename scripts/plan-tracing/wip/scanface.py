# scanface.py <photo.bmp> '<json>' : the building's four outer faces, scanned point by point.
# json: {"left": [y0, y1, x_out, x_in], "right": [y0, y1, x_out, x_in], "top": [x0, x1, y_out, y_in],
#        "bottom": [x0, x1, y_out, y_in]} - for each, along the stretch, from the outside coordinate
# toward the inside one, the first pixel darker than 0.8 of the paper where the scan starts. A line
# is fitted to those points, the strays (a jog, a door) dropped; the four lines' crossings print.
import os, importlib.util, json, sys
spec = importlib.util.spec_from_file_location('t', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'trace.py'))
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
W, H, rgb = T.read_bmp(sys.argv[1])
cfg = json.loads(sys.argv[2])
thr = float(sys.argv[3]) if len(sys.argv) > 3 else 0.8


def lum(x, y):
    return sum(rgb(x, y)) / 3


def scan(key):
    a0, a1, out, inn = cfg[key]
    vertical = key in ('left', 'right')     # the face is a plumb line: scan across x
    step = 1 if inn > out else -1
    pts = []
    for t in range(a0, a1, 4):
        paper = lum(out, t) if vertical else lum(t, out)
        c = out
        while c != inn:
            v = lum(c, t) if vertical else lum(t, c)
            if v < paper * thr:
                break
            c += step
        if c != inn:
            pts.append((t, c))
    # fit c = a + b t, drop the strays twice
    for _ in range(3):
        n = len(pts)
        mt = sum(p[0] for p in pts) / n; mc = sum(p[1] for p in pts) / n
        b = sum((p[0] - mt) * (p[1] - mc) for p in pts) / max(1e-9, sum((p[0] - mt) ** 2 for p in pts))
        a = mc - b * mt
        res = [abs(p[1] - (a + b * p[0])) for p in pts]
        cut = max(1.5, sorted(res)[int(len(res) * 0.6)] * 2)
        pts = [p for p, r in zip(pts, res) if r <= cut]
    print(f'  {key}: {len(pts)} points, {"x" if vertical else "y"} = {a + b * a0:.1f} .. {a + b * a1:.1f}', file=sys.stderr)
    return a, b, vertical


lines = {k: scan(k) for k in ('left', 'right', 'top', 'bottom')}


def cross(v, h):
    av, bv, _ = v; ah, bh, _ = h            # v: x = av + bv y ; h: y = ah + bh x
    y = (ah + bh * av) / (1 - bh * bv)
    return [round(av + bv * y, 1), round(y, 1)]


print(json.dumps({'tl': cross(lines['left'], lines['top']), 'tr': cross(lines['right'], lines['top']),
                  'br': cross(lines['right'], lines['bottom']), 'bl': cross(lines['left'], lines['bottom'])}))
