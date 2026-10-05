# aud.py <prefix> <spec> x0 x1 y0 y1 : the auditorium of an Edificio Central evacuation plan, as
# straight walls fitted to its orange: a level top, a slanted top, a plumb right side and a slanted
# bottom, with the block in its corner left out. Each wall is fitted to the edge of the orange column
# by column, the columns that stray (a label, the yellow street line) dropped.
import os, importlib.util, json, sys
spec = importlib.util.spec_from_file_location('t', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'trace.py'))
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
prefix, spec_path = sys.argv[1:3]
x0, x1, y0, y1 = map(int, sys.argv[3:7])
s = json.load(open(spec_path))
W, H, rgb = T.read_bmp(prefix + '.rect.bmp')
w, h = x1 - x0, y1 - y0
img = [rgb(x, y) for y in range(y0, y1) for x in range(x0, x1)]
dim = s.get('light') == 'balanced'
if dim:
    img = T.balanced(img)
bounds = tuple(s.get('fill', ((0.52, 0.76), 0.40)))
fill = bytearray(1 if T.klass(*c, proportional=dim, bounds=bounds) == T.FILL else 0 for c in img)
fill = T.erode(T.dilate(fill, w, h, 6), w, h, 6)
top, bottom = {}, {}
for i in range(w):
    col = [j for j in range(h) if fill[j * w + i]]
    if len(col) > 40:
        top[x0 + i] = y0 + col[0]
        bottom[x0 + i] = y0 + col[-1] + 1
xs = sorted(top)
left, right = xs[0], xs[-1] + 1


def line(points):
    """y = a + b x, fitted with the strays dropped."""
    pts = list(points)
    for _ in range(5):
        n = len(pts)
        mx = sum(p[0] for p in pts) / n; my = sum(p[1] for p in pts) / n
        b = sum((p[0] - mx) * (p[1] - my) for p in pts) / max(1e-9, sum((p[0] - mx) ** 2 for p in pts))
        a = my - b * mx
        res = sorted(abs(p[1] - (a + b * p[0])) for p in pts)
        cut = max(2.0, res[int(len(res) * 0.7)] * 1.5)
        pts = [p for p in pts if abs(p[1] - (a + b * p[0])) <= cut] or pts
    return a, b


# The level top: the columns whose top is within a few units of the most common one.
tops = sorted(top.values())
level = tops[len(tops) // 4]
flat = [x for x in xs if abs(top[x] - level) <= 4]
flat_y = sorted(top[x] for x in flat)[len(flat) // 2]
bend = max(x for x in flat if x < (left + right) / 2 + (right - left) / 4)
# The slanted top: from the bend to where the corner block starts - the top jumps down there.
corner = s.get('aud_corner')          # [x, y bottom] of the block in the corner, when the plan has it
slant_end = corner[0] if corner else right
a1, b1 = line((x, top[x]) for x in xs if bend + 15 < x < slant_end - 15)
a2, b2 = line((x, bottom[x]) for x in xs if left + 15 < x < right - 60)
bx = (a1 - flat_y) / -b1 if b1 else bend       # where the slant meets the level top
poly = [[left, flat_y], [round(bx), flat_y]]
if corner:
    poly += [[corner[0], round(a1 + b1 * corner[0])], [corner[0], corner[1]], [right, corner[1]]]
else:
    poly += [[right, round(a1 + b1 * right)]]
poly += [[right, round(a2 + b2 * right)], [left, round(a2 + b2 * left)]]
print(json.dumps(poly))
print(f'level top {flat_y}, slant y = {a1:.1f} + {b1:.4f} x, bottom y = {a2:.1f} + {b2:.4f} x', file=sys.stderr)
