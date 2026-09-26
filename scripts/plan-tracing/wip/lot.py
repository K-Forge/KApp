# lot.py <photo.bmp> '<json lines>' : fits straight dark lines in a photo and prints where they cross.
# lines: {"left": [[x0,y0],[x1,y1]], "top": ..., "right": ..., "bottom": ...} rough endpoints in
# the bmp's pixels. Each is refined: along the rough line, the darkest pixel within `band` across it
# (darker than the paper around it) is taken, a line is fitted, strays dropped, fitted again.
import importlib.util, json, math, sys
spec = importlib.util.spec_from_file_location('t', '/Users/13rian/Development/3-K-Forge/KApp-worktrees/backend/scripts/plan-tracing/trace.py')
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)

W, H, rgb = T.read_bmp(sys.argv[1])
lines = json.loads(sys.argv[2])
band = int(sys.argv[3]) if len(sys.argv) > 3 else 40


def lum(x, y):
    r, g, b = rgb(x, y)
    return (r + g + b) / 3


def fit(p0, p1, band):
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    nx, ny = -uy, ux
    pts = []
    for k in range(0, int(L), 6):
        cx, cy = x0 + ux * k, y0 + uy * k
        prof = [(lum(cx + nx * d, cy + ny * d), d) for d in range(-band, band + 1)]
        paper = sorted(v for v, _ in prof)[int(len(prof) * 0.8)]
        v, d = min(prof)
        if v < paper * 0.8:
            pts.append((cx + nx * d, cy + ny * d))
    for _ in range(4):
        n = len(pts)
        mx = sum(p[0] for p in pts) / n; my = sum(p[1] for p in pts) / n
        sxx = sum((p[0] - mx) ** 2 for p in pts); syy = sum((p[1] - my) ** 2 for p in pts)
        sxy = sum((p[0] - mx) * (p[1] - my) for p in pts)
        ang = 0.5 * math.atan2(2 * sxy, sxx - syy)
        dx, dy = math.cos(ang), math.sin(ang)
        res = [abs((p[0] - mx) * -dy + (p[1] - my) * dx) for p in pts]
        cut = max(3.0, sorted(res)[int(len(res) * 0.7)] * 1.5)
        kept = [p for p, r in zip(pts, res) if r <= cut]
        if len(kept) < 6:
            break
        pts = kept
    spread = sum(res) / len(res)
    return (mx, my, dx, dy), len(pts), spread


def cross(a, b):
    (x1, y1, dx1, dy1), (x2, y2, dx2, dy2) = a, b
    det = dx1 * -dy2 - dy1 * -dx2
    t = ((x2 - x1) * -dy2 - (y2 - y1) * -dx2) / det
    return [round(x1 + t * dx1, 1), round(y1 + t * dy1, 1)]


fitted = {}
for name, (p0, p1) in lines.items():
    fitted[name], n, spread = fit(p0, p1, band)
    print(f'{name}: {n} samples, mean offset {spread:.1f}px', file=sys.stderr)
corners = {'tl': cross(fitted['top'], fitted['left']), 'tr': cross(fitted['top'], fitted['right']),
           'br': cross(fitted['bottom'], fitted['right']), 'bl': cross(fitted['bottom'], fitted['left'])}
print(json.dumps(corners))
