# profile.py <rect.bmp> <axis x|y> <across0> <across1> <along0> <along1> [thresh]
# Orange runs along a strip: for axis y, rows from along0 to along1 averaged over x across0..across1;
# prints the orange stretches (where r - b is over half its highest) and the dips inside them.
import importlib.util, sys
spec = importlib.util.spec_from_file_location('t', '/Users/13rian/Development/3-K-Forge/KApp-worktrees/backend/scripts/plan-tracing/trace.py')
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
W, H, rgb = T.read_bmp(sys.argv[1])
axis = sys.argv[2]
a0, a1, b0, b1 = map(int, sys.argv[3:7])
th = float(sys.argv[7]) if len(sys.argv) > 7 else 0.5
vals = []
for t in range(b0, b1):
    cs = [rgb(s, t) if axis == 'y' else rgb(t, s) for s in range(a0, a1, 2)]
    vals.append((t, sum(c[0] - c[2] for c in cs) / len(cs)))
hi = sorted(v for _, v in vals)[int(len(vals) * 0.9)]
on = [t for t, v in vals if v > hi * th]
g = []
for t in on:
    if g and t - g[-1][-1] <= 2:
        g[-1].append(t)
    else:
        g.append([t])
print('orange:', [(x[0], x[-1]) for x in g if len(x) > 3])
dip = [t for t, v in vals if hi * th < v < hi * 0.85]
g = []
for t in dip:
    if g and t - g[-1][-1] <= 2:
        g[-1].append(t)
    else:
        g.append([t])
print('faint lines:', [(x[0], x[-1]) for x in g if len(x) >= 3])
