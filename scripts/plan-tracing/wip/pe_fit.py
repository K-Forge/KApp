# pe_fit.py <prefix> <spec.json>
# Puts each level-and-plumb room of a trace on the edge of the plan's orange, pixel by pixel.
# For every edge of a rectangular room, the line across the room's width is scored at each offset
# within "reach": how much of it is orange (labels and icons on the fill closed over first). The
# edge goes where the orange stops, the change nearest where it was. Rooms with a slanted edge are
# the tracer's own outline and stay. Writes <prefix>.json back, keeping the tracer's as .raw.json.
import importlib.util, json, shutil, sys, os
spec = importlib.util.spec_from_file_location('t', '/Users/13rian/Development/3-K-Forge/KApp-worktrees/backend/scripts/plan-tracing/trace.py')
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)

prefix, spec_path = sys.argv[1:3]
s = json.load(open(spec_path))
if not os.path.exists(prefix + '.raw.json') or os.path.getmtime(prefix + '.raw.json') < os.path.getmtime(prefix + '.json'):
    shutil.copy(prefix + '.json', prefix + '.raw.json')
data = json.load(open(prefix + '.raw.json'))
# "only": the rooms the spec draws are the floor's; what the tracer found besides is left out.
if s.get('only'):
    data['rooms'] = [{'shape': [list(p) for p in r['shape']], 'doors': [[list(q) for q in d] for d in r.get('doors', [])]} for r in s['rooms']]
    data['stairs'] = [{'shape': [list(p) for p in st]} for st in s.get('stairs', [])]
W, H, rgb = T.read_bmp(prefix + '.rect.bmp')
img = [rgb(x, y) for y in range(H) for x in range(W)]
dim = s.get('light') == 'balanced'
if dim:
    img = T.balanced(img)
bounds = tuple(s.get('fill', ((0.52, 0.76), 0.40)))
red = T.WALL if s.get('red') == 'wall' else T.MARK
cls = bytearray(T.klass(*c, proportional=dim, bounds=bounds, red=red) for c in img)
fill = bytearray(1 if c == T.FILL else 0 for c in cls)
close = s.get('fit_close', 6)
closed = T.erode(T.dilate(fill, W, H, close), W, H, close)
reach = s.get('fit_reach', 14)
keep = {tuple(p) for p in s.get('keep', [])}      # a point in each room to leave as traced


def frac(axis, at, lo, hi):
    n = hit = 0
    for t in range(int(lo), int(hi) + 1):
        x, y = (t, at) if axis == 'y' else (at, t)
        if 0 <= x < W and 0 <= y < H:
            n += 1
            hit += closed[y * W + x]
    return hit / max(1, n)


def snap(axis, at, lo, hi, outward):
    """The last line of orange going outward, nearest the edge as it was."""
    margin = max(2, (hi - lo) // 8)
    lo, hi = lo + margin, hi - margin
    best = None
    for d in range(-reach, reach + 1):
        here = frac(axis, at + d, lo, hi)
        beyond = frac(axis, at + d + outward, lo, hi)
        if here >= 0.5 > beyond:
            if best is None or abs(d) < abs(best):
                best = d
    return at if best is None else at + best


fitted = 0
for room in data['rooms']:
    poly = room['shape']
    xs = sorted({p[0] for p in poly}); ys = sorted({p[1] for p in poly})
    if len(poly) != 4 or len(xs) != 2 or len(ys) != 2:
        continue
    if any(T.inside(poly, *k) for k in keep):
        continue
    x0, x1 = xs; y0, y1 = ys
    nx0 = snap('x', x0, y0, y1, -1); nx1 = snap('x', x1, y0, y1, 1)
    ny0 = snap('y', y0, x0, x1, -1); ny1 = snap('y', y1, x0, x1, 1)
    # the far side of the last orange line is the room's edge
    nx1 += 1; ny1 += 1
    moved = (nx0, ny0, nx1, ny1) != (x0, y0, x1, y1)
    fitted += moved
    room['shape'] = [[nx0, ny0], [nx1, ny0], [nx1, ny1], [nx0, ny1]]
json.dump(data, open(prefix + '.json', 'w'))
print(f'{fitted} room(s) moved onto the orange')
