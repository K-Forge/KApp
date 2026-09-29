#!/usr/bin/env python3
"""
Writes the survey plan the portal's survey sheet shows: which distances to take on site round the
Edificio Central's block, with a phone's Measure app or a tape, in walking order.

    scripts/survey-plan.py

Two kinds of distance, both short enough for a phone (longer ones are taken in pieces):

  setback  from a wall at street level, straight out from it, to the curb: where it stands
  length   along a wall at street level, corner to corner, or a door: how wide it is

Every volume that shows on a street is asked for both, the wings and their connections from
outside, and the doors the buildings are entered by. The south wing is asked every 5 m besides,
because the cadastre draws it from above, where its upper floors come out.

The points are laid on the seed's outlines and the city's curbs, and written as [lon, lat], so the
sheet draws them over whatever the outlines are now. The expected lengths are the model's: they
tell a slip on site from a wall the model has wrong. A distance is saved under its id: never
renumber one that may have been taken; a new one gets a new id.
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/backend/microservices/map-service/src/main/resources')
OUT = os.path.join(ROOT, 'app/frontend/web-admin/src/app/features/data/survey/survey-plan.ts')
BLOCK = '008213024'


def load(path):
    with open(os.path.join(RES, path), encoding='utf-8') as f:
        return json.load(f)


GROUND = load('db/ground/sede-principal.json')
EC = load('db/seed/map/ec.json')
PLACEMENT = EC['placement']
ORIGIN = PLACEMENT['origin']
# The screen of the block editor and the survey sheet: Calle 63 up, Cra 9A left.
UP = math.radians(PLACEMENT['bearing'] - 90)
M_LON = 111320 * math.cos(math.radians(ORIGIN['lat']))
M_LAT = 110574


def to_view(c):
    east, north = (c[0] - ORIGIN['lon']) * M_LON, (c[1] - ORIGIN['lat']) * M_LAT
    return (east * math.cos(UP) - north * math.sin(UP), -east * math.sin(UP) - north * math.cos(UP))


def from_view(p):
    x, y = p
    east = x * math.cos(UP) - y * math.sin(UP)
    north = -x * math.sin(UP) - y * math.cos(UP)
    return [round(ORIGIN['lon'] + east / M_LON, 7), round(ORIGIN['lat'] + north / M_LAT, 7)]


def ring(r):
    r = r if isinstance(r[0][0], (int, float)) else r[0]
    pts = [to_view(c) for c in r]
    return pts[:-1] if len(pts) > 1 and pts[0] == pts[-1] else pts


ROADWAYS = [r for r in (ring(x) for x in GROUND['roadways']) if any(-90 < x < 25 and -20 < y < 110 for x, y in r)]

EAST, WEST, NORTH = (1, 0), (-1, 0), (0, -1)


def curb_foot(p, way):
    """Where a line straight out from the wall at p, along `way`, first meets a roadway's edge."""
    n = math.hypot(*way)
    wx, wy = way[0] / n, way[1] / n
    best = (math.inf, None)
    for r in ROADWAYS:
        for i in range(len(r)):
            (ax, ay), (bx, by) = r[i], r[(i + 1) % len(r)]
            ex, ey = bx - ax, by - ay
            den = wx * ey - wy * ex
            if abs(den) < 1e-12:
                continue
            t = ((ax - p[0]) * ey - (ay - p[1]) * ex) / den
            u = ((ax - p[0]) * wy - (ay - p[1]) * wx) / den
            if t > 0.05 and 0 <= u <= 1 and t < best[0]:
                best = (t, (p[0] + wx * t, p[1] + wy * t))
    if best[1] is None:
        raise SystemExit(f'no curb straight out from {p}')
    return best


PLAN = []


def setback(sid, street, at, way, text, building='EC'):
    d, q = curb_foot(at, way)
    PLAN.append(dict(id=sid, street=street, kind='setback', text=text, building=building,
                     a=from_view(at), b=from_view(q), expected=round(d, 2)))


def length(sid, street, a, b, text, building='EC'):
    PLAN.append(dict(id=sid, street=street, kind='length', text=text, building=building,
                     a=from_view(a), b=from_view(b), expected=round(math.dist(a, b), 2)))


# The corners and walls below are the seed's, in the screen's metres (x right, y down).

# ---- Carrera 9 Bis, south from the corner with Calle 63.
B = 'Carrera 9 Bis'
K = {1: (-4.19, 3.00), 2: (-4.22, 11.41), 3: (-6.29, 11.54), 4: (-6.26, 13.32), 5: (-11.15, 13.13),
     5.5: (-11.16, 17.95), 6: (-11.16, 28.24), 7: (-13.66, 28.26), 8: (-13.59, 36.15), 9: (-17.96, 36.20),
     18: (-21.99, 68.59)}
setback('bis-01', B, K[1], NORTH, 'North wing, corner with Calle 63: to the Calle 63 curb')
setback('bis-02', B, K[1], EAST, 'North wing, corner with Calle 63: to the Cra 9 Bis curb')
length('bis-03', B, K[1], K[2], 'North wing, its end on Cra 9 Bis: from the corner to where it steps in')
setback('bis-04', B, K[2], EAST, 'North wing, where its end steps in')
length('bis-05', B, K[2], K[3], 'How far it steps in')
length('bis-06', B, K[3], K[4], "The short wall after the step, to the north wing's corner on the plaza")
setback('bis-07', B, K[4], EAST, 'North wing, its corner on the plaza')
length('bis-08', B, K[4], K[5], "North wing's wall along the plaza, to the front behind it")
setback('bis-09', B, K[5], EAST, 'Where the north wing meets the front on the plaza')
length('bis-10', B, K[5], K[5.5], "North connection's front on the plaza, to where the central wing's front starts")
length('bis-11', B, K[5.5], K[6], "Central wing's front on the plaza, to where the wall steps back")
setback('bis-12', B, K[6], EAST, 'End of that front, where the wall steps back')
length('bis-13', B, (-11.16, 21.2), (-11.16, 24.4), "Reception's main door: its width")
length('bis-14', B, K[6], (-11.16, 24.4), "Reception's main door: from the corner where the front steps back to the door's nearest edge")
length('bis-15', B, K[6], K[7], 'How far the wall steps back')
setback('bis-16', B, K[7], EAST, 'After the step back')
length('bis-17', B, K[7], K[8], "Central wing's next front, to where it steps back again")
setback('bis-18', B, K[8], EAST, 'End of that front, where it steps back again')
length('bis-19', B, K[8], K[9], 'How far it steps back')
setback('bis-20', B, K[9], EAST, 'South wing, its first corner')


def south_wall_x(y):
    for y0, y1, x in ((36.2, 44.25, -17.96), (44.25, 49.83, -16.60), (49.83, 56.37, -18.99),
                      (56.37, 59.94, -19.90), (59.94, 68.6, -21.68)):
        if y0 <= y < y1:
            return x
    return -21.68


for i, y in enumerate((41.2, 46.2, 51.2, 56.2, 61.2, 66.2)):
    setback(f'bis-{21 + i}', B, (south_wall_x(y), y), EAST, f'South wing, {5 * (i + 1)} m along its wall from its first corner')
setback('bis-27', B, K[18], EAST, 'South wing, its last corner, beside the neighbour')
length('bis-28', B, (-17.96, 36.20), (-17.96, 68.59), "South wing, its whole front, from its first corner to the neighbour's wall")
length('bis-29', B, (-17.96, 39.5), (-17.96, 42.5), "Auditorium's public entrance: its width (none on this street? note it)")
length('bis-30', B, K[9], (-17.96, 39.5), "Auditorium's public entrance: from the south wing's first corner to its nearest edge")
length('bis-31', B, K[18], (-19.11, 68.59), "How far the neighbour's building comes out past the south wing")

# ---- Calle 62: the neighbour's curved corner, then the Tienda K.
C62 = 'Calle 62'
setback('c62-01', C62, (-19.11, 68.53), EAST, "Neighbour's building, its corner beside the south wing", 'Vecino')
setback('c62-02', C62, (-21.58, 73.01), (0.8, 0.6), "Neighbour's curved front, a third of the way round", 'Vecino')
setback('c62-03', C62, (-24.62, 76.52), (0.6, 0.8), "Neighbour's curved front, two thirds of the way round", 'Vecino')
setback('c62-04', C62, (-35.86, 83.94), (0.25, 0.97), "Neighbour's front on Calle 62, where it ends", 'Vecino')
T = {1: (-38.04, 82.68), 2: (-56.12, 87.53), 3: (-59.79, 72.75)}
length('c62-05', C62, (-35.86, 83.94), T[1], 'From the neighbour to the Tienda K', 'TK')
setback('c62-06', C62, T[1], (0.26, 0.97), 'Tienda K, its first corner on Calle 62', 'TK')
setback('c62-07', C62, (-47.08, 85.10), (0.26, 0.97), 'Tienda K, halfway along its front on Calle 62', 'TK')
length('c62-08', C62, T[1], T[2], 'Tienda K, its whole front on Calle 62', 'TK')
setback('c62-09', C62, T[2], (0.26, 0.97), 'Tienda K, corner with Cra 9A: to the Calle 62 curb', 'TK')

# ---- Carrera 9A, north: the Tienda K, the casa's garden wall, the exit, the wings.
A = 'Carrera 9A'
setback('a-01', A, T[2], (-0.97, 0.24), 'Tienda K, corner with Cra 9A: to the Cra 9A curb', 'TK')
length('a-02', A, T[2], T[3], 'Tienda K, its front on Cra 9A', 'TK')
setback('a-03', A, T[3], (-0.97, 0.1), 'Tienda K, its far corner on Cra 9A', 'TK')
G = {1: (-63.82, 69.45), 2: (-66.23, 66.00), 3: (-66.24, 45.42)}
length('a-04', A, T[3], G[1], "From the Tienda K to the casa's garden wall", 'Casa')
setback('a-05', A, G[2], WEST, 'Garden wall, its south end', 'Casa')
setback('a-06', A, (-66.23, 56.0), WEST, 'Garden wall, halfway', 'Casa')
setback('a-07', A, G[3], WEST, 'Garden wall, its north end', 'Casa')
length('a-08', A, G[2], G[3], 'Garden wall, its whole length on Cra 9A', 'Casa')
E = {1: (-64.48, 43.59), 2: (-64.52, 36.30)}
length('a-09', A, G[3], E[1], 'From the garden wall to the exit')
length('a-10', A, E[1], E[2], 'The exit: its width on the street, wall to wall')
setback('a-11', A, (-61.52, 40.0), WEST, 'The exit: from the curb to its gate or door, at its middle')
length('a-12', A, (-61.52, 38.5), (-61.52, 41.5), 'The back door (or gate) in the exit: its width')
length('a-13', A, E[2], (-61.52, 38.5), "The back door: from the central wing's corner beside the exit to its nearest edge")
setback('a-14', A, E[2], WEST, 'Central wing, its corner beside the exit')
setback('a-15', A, (-64.57, 26.3), WEST, 'About 10 m past the exit, towards Calle 63')
setback('a-16', A, (-64.66, 16.3), WEST, 'About 20 m past the exit, towards Calle 63')
length('a-17', A, E[2], (-64.66, 11.95), "Central wing's front on Cra 9A (one floor high), from the exit to the north wing")
length('a-18', A, (-64.66, 11.95), (-64.74, 3.00), 'North wing, its end on Cra 9A, to the corner with Calle 63')
setback('a-19', A, (-64.74, 3.00), WEST, 'North wing, corner with Calle 63: to the Cra 9A curb')

# ---- Calle 63, east, back to the start: the north wing's front in four stretches.
C63 = 'Calle 63'
setback('c63-01', C63, (-64.74, 3.00), NORTH, 'North wing, corner with Cra 9A: to the Calle 63 curb')
stations = [(-64.74, 3.00), (-49.74, 3.00), (-34.74, 3.00), (-19.74, 3.00)]
for i in range(1, 4):
    start = 'the corner with Cra 9A' if i == 1 else 'that point'
    length(f'c63-{2 * i:02d}', C63, stations[i - 1], stations[i], f'North wing on Calle 63: from {start} to the next point, about 15 m on')
    setback(f'c63-{2 * i + 1:02d}', C63, stations[i], NORTH, f'North wing, at that point, about {15 * i} m from the corner with Cra 9A')
length('c63-08', C63, stations[3], K[1], 'North wing on Calle 63: from that point to the corner with Cra 9 Bis')

for n, d in enumerate(PLAN, 1):
    d['n'] = n
assert len({d['id'] for d in PLAN}) == len(PLAN)


def ts(value):
    """A TypeScript literal as the portal's Prettier writes it: single quotes, unless the text has one."""
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False) if "'" in value else "'" + value.replace('\\', '\\\\') + "'"
    return json.dumps(value)


lines = [
    '// Written by scripts/survey-plan.py - edit the plan there and rerun it, not here.',
    "import type { PlannedDistance } from './survey.model';",
    '',
    "/** The block the plan walks round: the Edificio Central's. */",
    f"export const SURVEY_BLOCK = '{BLOCK}';",
    '',
    '/** The screen the plan was laid on: Calle 63 up, Cra 9A to the left. */',
    f"export const SURVEY_FRAME = {{ origin: {{ lat: {ORIGIN['lat']}, lon: {ORIGIN['lon']} }}, up: {round(PLACEMENT['bearing'] - 90, 2)} }};",
    '',
    '/** The distances to take, in walking order from the corner of Calle 63 and Cra 9 Bis. */',
    'export const SURVEY_PLAN: PlannedDistance[] = [',
]
for d in PLAN:
    lines.append('  {')
    for key in ('n', 'id', 'street', 'kind', 'building', 'text', 'a', 'b', 'expected'):
        lines.append(f'    {key}: {ts(d[key])},')
    lines.append('  },')
lines.append('];')
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
print(f'{len(PLAN)} distances -> {os.path.relpath(OUT, ROOT)}')
