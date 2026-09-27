# pe_check.py <prefix> <spec.json> [out.jpg x0 y0 x1 y1 zoom]
# How well a trace of an evacuation plan matches its orange, pixel by pixel:
#  - per room: the share of its outline with orange just inside and none just outside, and the
#    share of its area that is orange;
#  - over the floor: how much of the orange no room covers (missed) and how much room is not orange.
# With out.jpg, a tile of the rectified plan with every outline drawn: green where it follows the
# orange's edge, red where it does not; orange nobody covers is tinted magenta; rooms numbered.
import importlib.util, json, math, subprocess, sys, os
spec = importlib.util.spec_from_file_location('t', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'trace.py'))
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
lspec = importlib.util.spec_from_file_location('L', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'lines.py'))
L = importlib.util.module_from_spec(lspec); lspec.loader.exec_module(L)

prefix, spec_path = sys.argv[1:3]
s = json.load(open(spec_path))
data = json.load(open(prefix + '.json'))
W, H, rgb = T.read_bmp(prefix + '.rect.bmp')
img = [rgb(x, y) for y in range(H) for x in range(W)]
dim = s.get('light') == 'balanced'
if dim:
    img = T.balanced(img)
bounds = tuple(s.get('fill', ((0.52, 0.76), 0.40)))
red = T.WALL if s.get('red') == 'wall' else T.MARK
cls = bytearray(T.klass(*c, proportional=dim, bounds=bounds, red=red) for c in img)
fill = bytearray(1 if c == T.FILL else 0 for c in cls)
# A few pixels of fill are enough to call a spot orange: a label or a line may sit on it.
near = T.dilate(fill, W, H, 2)

rooms = [r['shape'] for r in data['rooms']]
stairs = [st['shape'] for st in data.get('stairs', [])]

inside_any = bytearray(W * H)
owner = [-1] * (W * H)
areas = []
for k, poly in enumerate(rooms):
    xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
    tot = orange = 0
    for y in range(max(0, int(min(ys))), min(H, int(max(ys)) + 1)):
        for x in range(max(0, int(min(xs))), min(W, int(max(xs)) + 1)):
            if T.inside(poly, x + 0.5, y + 0.5):
                inside_any[y * W + x] = 1
                owner[y * W + x] = k
                tot += 1
                orange += near[y * W + x]
    areas.append(orange / max(1, tot))


def edge_ok(poly, k, px, py, nx, ny):
    """Orange just inside, and just outside either none or another room's."""
    a = (int(px + nx * 4), int(py + ny * 4)); b = (int(px - nx * 4), int(py - ny * 4))
    inp, outp = (a, b) if T.inside(poly, *a) and not T.inside(poly, *b) else (b, a)
    ok_in = 0 <= inp[0] < W and 0 <= inp[1] < H and fill[inp[1] * W + inp[0]]
    if not (0 <= outp[0] < W and 0 <= outp[1] < H):
        return ok_in
    o = outp[1] * W + outp[0]
    return ok_in and (not fill[o] or owner[o] not in (-1, k))


report = []
for k, poly in enumerate(rooms):
    good = n = 0
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        length = math.hypot(x1 - x0, y1 - y0)
        if not length:
            continue
        nx, ny = -(y1 - y0) / length, (x1 - x0) / length
        for t in range(2, int(length) - 1, 2):
            n += 1
            good += edge_ok(poly, k, x0 + (x1 - x0) * t / length, y0 + (y1 - y0) * t / length, nx, ny)
    report.append((k, good / max(1, n), areas[k]))

total_fill = sum(fill)
missed = sum(1 for p in range(W * H) if fill[p] and not inside_any[p])
for k, edge, area in report:
    flag = '' if edge >= 0.8 and area >= 0.85 else '   <- check'
    xs = [q[0] for q in rooms[k]]; ys = [q[1] for q in rooms[k]]
    print(f'  {k:3d} edge {edge:4.0%}  orange {area:4.0%}  [{min(xs)},{min(ys)}]-[{max(xs)},{max(ys)}] {len(rooms[k])}pts{flag}')
print(f'orange covered by rooms: {1 - missed / max(1, total_fill):.1%} ({missed} px of orange outside every room)')

if len(sys.argv) > 3:
    out = sys.argv[3]
    x0, y0, x1, y1 = map(int, sys.argv[4:8])
    zoom = int(sys.argv[8]) if len(sys.argv) > 8 else 1
    x1, y1 = min(x1, W), min(y1, H)
    raw = [rgb(x, y) for y in range(H) for x in range(W)]
    ow, oh = (x1 - x0) * zoom, (y1 - y0) * zoom
    canvas = []
    for j in range(oh):
        for i in range(ow):
            x, y = x0 + i // zoom, y0 + j // zoom
            c = raw[y * W + x]
            if fill[y * W + x] and not inside_any[y * W + x]:
                c = (230, 40, 200)
            elif x % 100 == 0 or y % 100 == 0:
                c = (40, 90, 230)
            canvas.append(c)

    def put(x, y, c):
        i, j = int((x - x0) * zoom), int((y - y0) * zoom)
        if 0 <= i < ow and 0 <= j < oh:
            canvas[j * ow + i] = c

    for poly in rooms:
        for i in range(len(poly)):
            (ax, ay), (bx, by) = poly[i], poly[(i + 1) % len(poly)]
            length = max(1, math.hypot(bx - ax, by - ay))
            nx, ny = -(by - ay) / length, (bx - ax) / length
            for t in range(int(length * zoom) + 1):
                px, py = ax + (bx - ax) * t / (length * zoom), ay + (by - ay) * t / (length * zoom)
                ok = edge_ok(poly, rooms.index(poly), px, py, nx, ny)
                colour = (0, 170, 60) if ok else (230, 20, 20)
                for d in range(zoom + 1):
                    put(px + d / zoom * nx * 0, py, colour)
                    put(px, py + d / zoom * 0, colour)
    for poly in stairs:
        for i in range(len(poly)):
            (ax, ay), (bx, by) = poly[i], poly[(i + 1) % len(poly)]
            length = max(1, math.hypot(bx - ax, by - ay))
            for t in range(int(length * zoom) + 1):
                put(ax + (bx - ax) * t / (length * zoom), ay + (by - ay) * t / (length * zoom), (60, 60, 200))
    for k, poly in enumerate(rooms):
        xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        text = str(k)
        size = 3
        for ci, ch in enumerate(text):
            g = L.DIGITS.get(ch, L.DIGITS['0'])
            for r in range(-1, 6):
                for q in range(-1, 4):
                    on = 0 <= r < 5 and 0 <= q < 3 and g[r * 3 + q] == '1'
                    for dy in range(size):
                        for dx in range(size):
                            i = int((cx - x0) * zoom) + ci * 4 * size + q * size + dx
                            j = int((cy - y0) * zoom) + r * size + dy
                            if 0 <= i < ow and 0 <= j < oh:
                                canvas[j * ow + i] = (0, 0, 0) if on else (255, 240, 120)
    T.write_bmp(out + '.bmp', canvas, ow, oh)
    subprocess.run(['sips', '-s', 'format', 'jpeg', out + '.bmp', '--out', out], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    os.remove(out + '.bmp')
