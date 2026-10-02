# migrate.py <old spec> <new spec>: carries every point an old trace.py spec gives in its plan's
# frame - rooms, stairs, merge, drop, box, the floor's names and splits, the door pairs' plan side -
# into the new spec's frame, through the photo both are laid on. Writes the new spec back.
import os, importlib.util, json, sys
spec = importlib.util.spec_from_file_location('t', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'trace.py'))
T = importlib.util.module_from_spec(spec); spec.loader.exec_module(T)
old = json.load(open(sys.argv[1])); new = json.load(open(sys.argv[2]))
if old.get('resample', 2800) != new.get('resample', 2800):
    sys.exit('the two specs measure the photo at different sizes')


def frame(s):
    pw, ph = s['plaque']
    u0, v0 = s['crop'][0] * pw, s['crop'][1] * ph
    hm = T.homography([(0, 0), (pw, 0), (pw, ph), (0, ph)], s['corners'])
    inv = T.homography(s['corners'], [(0, 0), (pw, 0), (pw, ph), (0, ph)])
    k = s.get('scale', 1.0)

    def to_photo(p):
        u, v = u0 + p[0] / k, v0 + p[1] / k
        z = hm[2][0] * u + hm[2][1] * v + hm[2][2]
        return ((hm[0][0] * u + hm[0][1] * v + hm[0][2]) / z, (hm[1][0] * u + hm[1][1] * v + hm[1][2]) / z)

    def from_photo(p):
        x, y = p
        z = inv[2][0] * x + inv[2][1] * y + inv[2][2]
        u, v = (inv[0][0] * x + inv[0][1] * y + inv[0][2]) / z, (inv[1][0] * x + inv[1][1] * y + inv[1][2]) / z
        return [round((u - u0) * k), round((v - v0) * k)]
    return to_photo, from_photo


to_photo, _ = frame(old)
_, from_photo = frame(new)


def mv(p):
    return from_photo(to_photo(p))


def poly(ps):
    return [mv(p) for p in ps]


for key in ('rooms',):
    if key in old:
        new[key] = [dict(r, shape=poly(r['shape']), **({'doors': [poly(d) for d in r['doors']]} if 'doors' in r else {}))
                    for r in old[key]]
for key in ('stairs',):
    if key in old:
        new[key] = [poly(s) for s in old[key]]
for key in ('drop', 'box', 'keep'):
    if key in old:
        new[key] = poly(old[key])
if 'merge' in old:
    new['merge'] = [poly(g) for g in old['merge']]
if 'doors' in old:
    d = dict(old['doors'])
    d['pairs'] = [[p[0], mv(p[1])] for p in old['doors']['pairs']]
    new['doors'] = d
for key in ('light', 'fill', 'red', 'treads'):
    if key in old:
        new[key] = old[key]
f = dict(old.get('floor', {}))
f['spaces'] = {k: mv(v) for k, v in f.get('spaces', {}).items()}
cuts = []
for c in f.get('split', []):
    at = mv(c['at'])
    axis = 'x' if 'x' in c else 'y'
    line_pt = [c['x'], c['at'][1]] if axis == 'x' else [c['at'][0], c['y']]
    moved = mv(line_pt)
    cuts.append({'at': at, axis: moved[0] if axis == 'x' else moved[1]})
if cuts:
    f['split'] = cuts
f['extra'] = {k: poly(v) for k, v in f.get('extra', {}).items()} if f.get('extra') else f.get('extra', {})
if not f['extra']:
    f.pop('extra')
new['floor'] = dict(new.get('floor', {}), **{k: v for k, v in f.items() if k not in ('building', 'code')})
json.dump(new, open(sys.argv[2], 'w'), indent=1)
print('carried over:', [k for k in ('rooms', 'stairs', 'drop', 'box', 'merge', 'doors') if k in old],
      'spaces', len(f.get('spaces', {})), 'splits', len(cuts))
