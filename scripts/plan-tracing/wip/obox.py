# obox.py <prefix> <spec> x0 y0 x1 y1 : the connected pieces of the plan's orange inside a box, as boxes.
import os, importlib.util, json, sys
spec = importlib.util.spec_from_file_location('t', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'trace.py'))
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
prefix, sp = sys.argv[1:3]
x0, y0, x1, y1 = map(int, sys.argv[3:7])
s = json.load(open(sp))
W, H, rgb = T.read_bmp(prefix + '.rect.bmp')
w, h = x1 - x0, y1 - y0
img = [rgb(x, y) for y in range(y0, y1) for x in range(x0, x1)]
dim = s.get('light') == 'balanced'
if dim:
    img = T.balanced(img)
bounds = tuple(s.get('fill', ((0.52, 0.76), 0.40)))
fill = bytearray(1 if T.klass(*c, proportional=dim, bounds=bounds) == T.FILL else 0 for c in img)
for piece in T.components(fill, w, h, 1):
    if len(piece) < 30:
        continue
    xs = [p % w for p in piece]; ys = [p // w for p in piece]
    print([x0 + min(xs), y0 + min(ys), x0 + max(xs) + 1, y0 + max(ys) + 1], len(piece))
