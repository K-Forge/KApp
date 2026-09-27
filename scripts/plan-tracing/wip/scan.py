# scan.py <prefix> <spec> x0 x1 y0 y1 [eps] : the outline of the orange in a box, scanned column by
# column (top and bottom of the orange in each), simplified - for rooms convex up and down, like
# the auditorium, whose slanted walls the tracer steps.
import os, importlib.util, json, sys
spec = importlib.util.spec_from_file_location('t', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'trace.py'))
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
prefix, spec_path = sys.argv[1:3]
x0, x1, y0, y1 = map(int, sys.argv[3:7])
eps = float(sys.argv[7]) if len(sys.argv) > 7 else 3
s = json.load(open(spec_path))
W, H, rgb = T.read_bmp(prefix + '.rect.bmp')
w, h = x1 - x0, y1 - y0
img = [rgb(x, y) for y in range(y0, y1) for x in range(x0, x1)]
dim = s.get('light') == 'balanced'
if dim:
    img = T.balanced(img)
bounds = tuple(s.get('fill', ((0.52, 0.76), 0.40)))
fill = bytearray(1 if T.klass(*c, proportional=dim, bounds=bounds) == T.FILL else 0 for c in img)
c = s.get('fit_close', 8)
fill = T.erode(T.dilate(fill, w, h, c), w, h, c)
top, bottom = [], []
for i in range(w):
    col = [j for j in range(h) if fill[j * w + i]]
    if len(col) < 10:
        continue
    top.append((x0 + i, y0 + col[0]))
    bottom.append((x0 + i + 1, y0 + col[-1] + 1))
loop = top + bottom[::-1]
back = bottom[::-1]
poly = [top[i] for i in sorted(T.rdp(top, eps))] + [back[i] for i in sorted(T.rdp(back, eps))]
print(json.dumps([[round(p[0]), round(p[1])] for p in poly]))
