# face.py <photo.bmp> '<json lines>' [band] [colour]
# Like lot.py, but fits the OUTER face of a coloured wall: along each rough line, scanning from
# outside inward ("out": which side is outside, as a point), the first pixel of the colour.
# colour: "magenta" (the Bienestar plans' walls) or "dark".
import importlib.util, json, math, sys
spec = importlib.util.spec_from_file_location('t', '/Users/13rian/Development/3-K-Forge/KApp-worktrees/backend/scripts/plan-tracing/trace.py')
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
W, H, rgb = T.read_bmp(sys.argv[1])
lines = json.loads(sys.argv[2])
band = int(sys.argv[3]) if len(sys.argv) > 3 else 25
COLOUR = sys.argv[4] if len(sys.argv) > 4 else 'magenta'


def hit(x, y, colour=None):
    r, g, b = rgb(x, y)
    colour = colour or COLOUR
    if colour == 'green':
        return g > r + 30 and g > b + 20
    if colour == 'blueline':
        return b >= r + 10 and g > r + 15 and r < 125
    if colour == 'lightblue':
        return g > r + 15 and b > r + 2 and r + g + b < 450
    if colour == 'magenta':
        return r > g + 40 and b > g + 10 and r > 50
    return (r + g + b) / 3 < 90


def fit(p0, p1, out, colour=None):
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    nx, ny = -uy, ux
    if (out[0] - x0) * nx + (out[1] - y0) * ny < 0:
        nx, ny = -nx, -ny                      # n points outward
    pts = []
    for k in range(0, int(L), 3):
        cx, cy = x0 + ux * k, y0 + uy * k
        if colour in ('rel', 'relgrey'):          # the first pixel from outside much darker than the paper
            cs = [(rgb(round(cx + nx * d), round(cy + ny * d)), d) for d in range(band, -band - 1, -1)]
            prof = [(sum(c) / 3, d, c) for c, d in cs]
            paper = sorted(v for v, _, _ in prof)[int(len(prof) * 0.8)]
            for v, d, c in prof:
                if colour == 'relgrey' and c[0] > c[1] + 30:
                    continue                     # the red lot line drawn beside the wall
                if v < paper * 0.78:
                    pts.append((cx + nx * d, cy + ny * d))
                    break
            continue
        if colour == 'darkest':                  # the darkest pixel across, as lot.py
            prof = [(sum(rgb(round(cx + nx * d), round(cy + ny * d))) / 3, d) for d in range(-band, band + 1)]
            paper = sorted(v for v, _ in prof)[int(len(prof) * 0.8)]
            v, d = min(prof)
            if v < paper * 0.8:
                pts.append((cx + nx * d, cy + ny * d))
            continue
        for d in range(band, -band - 1, -1):     # from outside in
            if hit(round(cx + nx * d), round(cy + ny * d), colour):
                pts.append((cx + nx * d, cy + ny * d))
                break
    for _ in range(4):
        n = len(pts)
        mx = sum(p[0] for p in pts) / n; my = sum(p[1] for p in pts) / n
        sxx = sum((p[0] - mx) ** 2 for p in pts); syy = sum((p[1] - my) ** 2 for p in pts)
        sxy = sum((p[0] - mx) * (p[1] - my) for p in pts)
        ang = 0.5 * math.atan2(2 * sxy, sxx - syy)
        dx, dy = math.cos(ang), math.sin(ang)
        res = [abs((p[0] - mx) * -dy + (p[1] - my) * dx) for p in pts]
        cut = max(1.5, sorted(res)[int(len(res) * 0.7)] * 1.5)
        kept = [p for p, r in zip(pts, res) if r <= cut]
        if len(kept) < 6:
            break
        pts = kept
    print(f'  {len(pts)} samples, mean offset {sum(res) / len(res):.1f}px', file=sys.stderr)
    return (mx, my, dx, dy)


def cross(a, b):
    (x1, y1, dx1, dy1), (x2, y2, dx2, dy2) = a, b
    det = dx1 * -dy2 - dy1 * -dx2
    t = ((x2 - x1) * -dy2 - (y2 - y1) * -dx2) / det
    return [round(x1 + t * dx1, 1), round(y1 + t * dy1, 1)]


f = {k: fit(v[0], v[1], v[2], v[3] if len(v) > 3 else None) for k, v in lines.items()}
print(json.dumps({'tl': cross(f['top'], f['left']), 'tr': cross(f['top'], f['right']),
                  'br': cross(f['bottom'], f['right']), 'bl': cross(f['bottom'], f['left'])}))
