# mag.py <rect.bmp> <axis x|y> <a0> <a1> <b0> <b1> [colour]: where walls of a colour cross a strip.
# axis y: rows b0..b1, each counted over x a0..a1 -> the y ranges where most of the strip is wall
# (a level wall); axis x: the x ranges of plumb walls.
import os, importlib.util, sys
spec = importlib.util.spec_from_file_location('t', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'trace.py'))
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
W, H, rgb = T.read_bmp(sys.argv[1])
axis = sys.argv[2]; a0, a1, b0, b1 = map(int, sys.argv[3:7])
colour = sys.argv[7] if len(sys.argv) > 7 else 'magenta'
def hit(c):
    r, g, b = c
    if colour == 'magenta':
        return r > g + 40 and b > g + 10 and r > 50
    if colour == 'green':
        return g > r + 25 and g > b + 15
    return (r + g + b) / 3 < 110
out = []
for t in range(b0, b1):
    n = sum(hit(rgb(s, t) if axis == 'y' else rgb(t, s)) for s in range(a0, a1))
    if n > 0.5 * (a1 - a0):
        out.append(t)
g = []
for t in out:
    if g and t - g[-1][-1] <= 2:
        g[-1].append(t)
    else:
        g.append([t])
print([(x[0], x[-1]) for x in g])
