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
TK = load('db/seed/map/tk.json')
STRUCTURES = load('db/seed/structures/sede-principal.json')
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
BUILT = ([ring(p['ring']) for p in EC['footprint'] + TK['footprint'] if p['floors'] > 0]
         + [ring(x['ring']) for x in STRUCTURES['structures'] if x['floors'] > 0])


def point_in(p, r):
    x, y = p
    inside = False
    for i in range(len(r)):
        (x1, y1), (x2, y2) = r[i], r[(i + 1) % len(r)]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            inside = not inside
    return inside


BLOCK_RING = next(r for r in (ring(x) for x in GROUND['blocks']) if point_in((-40, 40), r))

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


def length(sid, street, a, b, text, building='EC', along=None, through=None):
    """From a to b; or, with `along`, only as far as they are apart that way - a front measured
    along the street - with its dimension line drawn `through` a point, clear of the others."""
    d = dict(id=sid, street=street, kind='length', text=text, building=building, a=from_view(a), b=from_view(b),
             expected=round(abs((b[0] - a[0]) * along[0] + (b[1] - a[1]) * along[1]) if along else math.dist(a, b), 2))
    if along:
        d['along'], d['through'] = along, through
    PLAN.append(d)


# The corners and walls below are the seed's, in the screen's metres (x right, y down).

# ---- Carrera 9 Bis, south from the corner with Calle 63. A setback is asked only where it is
# not the sum of others on its line: from the corner of a step, the setback of the corner ahead
# plus the step's length already says it.
B = 'Carrera 9 Bis'
# The first floor's corners, as Brian measured them on 2026-09-29 and the outline now draws them:
# the north wing, stepped in to the north connection, the central wing's whole front, stepped in to
# the south connection, then the south wing over the auditorium.
K = {1: (-12.37, 7.20), 2: (-12.37, 12.59), 3: (-13.75, 12.59), 4: (-13.76, 17.97), 5: (-13.59, 36.15),
     6: (-17.96, 36.20), 7: (-17.97, 43.67)}
setback('bis-01', B, K[1], NORTH, 'North wing, corner with Calle 63: to the Calle 63 curb')
setback('bis-02', B, K[1], EAST, 'North wing, corner with Calle 63: to the Cra 9 Bis curb')
length('bis-03', B, K[1], K[2], 'North wing, its end on Cra 9 Bis: from the corner to where it steps in')
setback('bis-04', B, K[2], EAST, 'North wing, where its end steps in')
length('bis-05', B, K[2], K[3], 'How far it steps in')
length('bis-10', B, K[3], K[4], "North connection's front on the plaza, to where the central wing's front starts")
length('bis-11', B, K[4], K[5], "Central wing's front on the plaza, to where the wall steps back")
setback('bis-12', B, K[5], EAST, 'End of that front, where the wall steps back')


def on_front(y):
    """The central wing's front at `y`: it leans 17 cm over its length."""
    (x0, y0), (x1, y1) = K[4], K[5]
    return (x0 + (x1 - x0) * (y - y0) / (y1 - y0), y)


# Where the door is along that front is not known yet: in its middle, as wide as it was measured.
length('bis-13', B, on_front(23.2), on_front(30.93), "Reception's main door: its width")
length('bis-15', B, K[5], K[6], 'How far the wall steps back')
length('bis-17', B, K[6], K[7], "South connection's front on the plaza, to where the south wing starts")
setback('bis-18', B, K[7], EAST, 'End of that front, where it steps back again')
# The auditorium, as the street shows it: past the south connection a glass entrance stands in beside
# a green column, then one flat blue wall runs along Cra 9 Bis, parallel to its curb; before the
# neighbour's, the wall steps in to a grey stretch with a door, a small window over it and a stair.
# Until it is measured, the blue wall is the straight line that best fits what the cadastre drew as
# steps, and the grey stretch a metre behind it, four before the neighbour.
SLOPE = -0.288  # the curb's, in x per metre south
BLUE = lambda y: -16.92 + SLOPE * (y - 46.0)
ALONG = math.hypot(1, SLOPE)
FROM_BLUE = (1, -SLOPE)  # straight out from the blue wall, towards the curb
INWARD = (-1 / ALONG, SLOPE / ALONG)
AU = {1: (BLUE(46.0), 46.0), 2: (BLUE(64.5), 64.5)}
AU[3] = (AU[2][0] + INWARD[0], AU[2][1] + INWARD[1])  # the grey stretch's corner, a metre in
AU[4] = (AU[3][0] + SLOPE * (68.5 - AU[3][1]), 68.5)  # its end at the neighbour's wall
grey = lambda d: (AU[3][0] + SLOPE * d / ALONG, AU[3][1] + d / ALONG)  # d metres along the grey stretch
setback('bis-19', B, (-17.96, 44.85), EAST, "Auditorium's entrance, at its glass: to the curb")
for i in range(3):
    y = 46.0 + 5 * (i + 1) / ALONG
    setback(f'bis-{21 + i}', B, (BLUE(y), y), FROM_BLUE, f"Auditorium's blue wall, {5 * (i + 1)} m from its north end: to the curb")
setback('bis-24', B, grey(2.0), FROM_BLUE, "The grey stretch past the blue wall, at its door: to the curb")
length('bis-28', B, AU[1], AU[2], "Auditorium's blue wall, its whole length, from the green column to where it steps in")
length('bis-32', B, AU[2], AU[3], 'How far the wall steps in after the blue wall')
length('bis-33', B, AU[3], AU[4], "The grey stretch past the blue wall, its whole front, to the neighbour's wall")
length('bis-35', B, AU[3], grey(0.8), "The grey stretch's door: from the blue wall's corner to the door's nearest edge")
length('bis-34', B, grey(0.8), grey(2.6), "The grey stretch's door: its width")
length('bis-29', B, K[7], (-17.96, 46.0), "Auditorium's entrance: its width, from the south connection to the green column")
length('bis-30', B, (-17.96, 46.0), AU[1], "How far the auditorium's blue wall stands out past the entrance")
length('bis-31', B, AU[4], (-19.11, 68.59), "How far the neighbour's building comes out past the auditorium")

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
# The casa's front, straight along Cra 9A from its south corner to the north end of its garden
# wall: its house almost on the sidewalk, then the wall. A brick wall runs from the Tienda K's far
# corner straight to that south corner, across the end of the Tienda K's forecourt (Street View,
# 2023), so 40 ends where 41 is taken.
G = {2: (-66.23, 66.00), 3: (-66.24, 45.42)}
length('a-04', A, T[3], G[2], "From the Tienda K to the casa's corner, along the brick wall", 'Casa')
setback('a-05', A, G[2], WEST, 'Garden wall, its south end', 'Casa')
setback('a-06', A, (-66.23, 56.0), WEST, 'Garden wall, halfway', 'Casa')
setback('a-07', A, G[3], WEST, 'Garden wall, its north end', 'Casa')
length('a-08', A, G[2], G[3], 'Garden wall, its whole length on Cra 9A', 'Casa')
# The exit, as the street shows it: from the pillar that ends the casa's garden wall, an opening
# about 4 m wide to the guard's booth; behind it, set back inside, the gate; past the booth, the
# planter in front of the central wing. Where the booth and the gate stand is not measured yet.
E = {1: (-64.48, 43.59), 2: (-61.63, 36.29)}
BOOTH = {1: (-62.80, 37.90), 2: (-62.80, 39.50), 3: (-61.30, 39.50)}  # its front's ends, and its back by the gate
GATE = -61.52  # the gate's line, inside the opening
length('a-09', A, G[3], E[1], 'From the garden wall to the exit')
length('a-10', A, E[1], BOOTH[2], "The exit: its width on the street, from the garden wall's pillar to the guard's booth",
       along=(0, 1), through=(-63.6, 0))
setback('a-11', A, (GATE, 40.75), WEST, 'The exit: from the curb to its gate, inside, at its middle')
length('a-12', A, (GATE, 39.50), (GATE, 42.01), 'The gate, inside the exit: its width')
length('a-13', A, (GATE, 42.01), (GATE, 43.55), "The gate: from its far edge to the casa's wall")
length('a-20', A, BOOTH[1], BOOTH[2], "The guard's booth beside the exit: its front on the street")
setback('a-21', A, (BOOTH[1][0], 38.70), WEST, "The guard's booth: from the curb to its front")
length('a-22', A, BOOTH[2], BOOTH[3], "The guard's booth: how far it goes in, along its side by the gate")
setback('a-14', A, E[2], WEST, 'Central wing, its corner beside the exit')
setback('a-15', A, (-61.63, 26.3), WEST, 'About 10 m past the exit, towards Calle 63')
setback('a-16', A, (-61.70, 16.3), WEST, 'About 20 m past the exit, towards Calle 63')
length('a-17', A, E[2], (-61.70, 12.59), "Central wing's front on Cra 9A, along the small garden, from the exit to the north wing")
length('a-18', A, (-62.35, 12.59), (-62.35, 7.20), 'North wing, its end on Cra 9A, to the corner with Calle 63')
setback('a-19', A, (-62.35, 7.20), WEST, 'North wing, corner with Calle 63: to the Cra 9A curb')

# ---- Calle 63, east, back to the start: the north wing's front, from the corner to the admissions
# door, past the door to the stairs, and past the stairs to the corner on the plaza.
C63 = 'Calle 63'
W = 7.20  # the first floor's wall, 30 cm behind the shrubs Brian's tape stopped at
setback('c63-01', C63, (-62.35, W), NORTH, 'North wing, corner with Cra 9A: to the Calle 63 curb')
door, past, stairs, top = (-33.61, W), (-30.64, W), (-21.11, W), (-13.19, W)
length('c63-02', C63, (-62.35, W), door, 'North wing on Calle 63: from the corner with Cra 9A to the admissions door')
setback('c63-03', C63, door, NORTH, 'North wing, at the admissions door')
length('c63-04', C63, door, past, 'The admissions door on Calle 63: its width')
setback('c63-05', C63, past, NORTH, 'North wing, past the admissions door')
length('c63-06', C63, past, stairs, 'North wing on Calle 63: from the admissions door to the stairs')
setback('c63-07', C63, stairs, NORTH, 'North wing, where the stairs start')
length('c63-08', C63, stairs, top, 'The stairs on Calle 63: their width')
length('c63-09', C63, top, K[1], 'North wing on Calle 63: from the stairs to the corner with Cra 9 Bis')

NUMBERED = ['bis-01', 'bis-02', 'bis-03', 'bis-04', 'bis-05', 'bis-06', 'bis-07', 'bis-08', 'bis-10', 'bis-11',
            'bis-12', 'bis-13', 'bis-14', 'bis-15', 'bis-17', 'bis-18', 'bis-19', 'bis-21', 'bis-22', 'bis-23',
            'bis-24', 'bis-25', 'bis-26', 'bis-28', 'bis-29', 'bis-30', 'bis-31', 'c62-01', 'c62-02', 'c62-03',
            'c62-04', 'c62-05', 'c62-06', 'c62-07', 'c62-08', 'c62-09', 'a-01', 'a-02', 'a-03', 'a-04', 'a-05',
            'a-06', 'a-07', 'a-08', 'a-09', 'a-10', 'a-11', 'a-12', 'a-13', 'a-14', 'a-15', 'a-16', 'a-17', 'a-18',
            'a-19', 'c63-01', 'c63-02', 'c63-03', 'c63-04', 'c63-05', 'c63-06', 'c63-07', 'c63-08', 'c63-09',
            'bis-32', 'bis-33', 'bis-34', 'bis-35', 'a-20', 'a-21', 'a-22']
# The numbers people wrote down on site: a distance taken off the plan leaves its number unused, and a
# new one is added at the end of this list, never in between.
for d in PLAN:
    d['n'] = NUMBERED.index(d['id']) + 1
assert len({d['id'] for d in PLAN}) == len(PLAN)

# What Street View shows at a distance that is easy to take wrong, written under it on the sheet: what
# stands there and where the tape goes. Only what the imagery shows plainly; its date is in the text.
HINTS = {
    'bis-19': 'Street View (2025): beside the lime-green panel there is a glass entrance raised on a few steps. Measure from its glass, at street level, past the steps.',
    'a-04': "Take it again: the sketch changed here on 1 October. From the Tienda K's far corner, along the brick wall that closes its forecourt, to the casa's corner on the sidewalk, where 41 is taken.",
    'a-05': "Take it again: the sketch changed here on 1 October. The casa's corner on the sidewalk, where the brick wall from the Tienda K (40) meets it.",
    'a-08': 'Street View (2023): the house, then its white garden wall with grilles and the trees behind, up to a pillar beside the exit.',
    'a-10': "Take it again: the sketch changed here on 1 October. From the pillar that ends the casa's garden wall to the guard's booth, on the street.",
    'a-11': 'Take it again: the sketch changed here on 1 October. The gate stands inside, behind the line of the wall: measure to the gate itself.',
    'a-12': 'Take it again: the sketch changed here on 1 October. The gate itself, inside the exit, beside the guard\'s booth.',
    'a-13': "Take it again: the sketch changed here on 1 October. From the gate's far edge to the casa's wall, on the gate's line.",
    'a-20': "Street View (2023): a small white booth with a window, between the gate and the planter.",
    'a-18': 'Street View (2023): raised planters run along this corner. Measure along the wall behind them.',
    'a-19': 'Street View (2023): raised planters stand between the wall and the sidewalk. Measure from the wall, behind them.',
    'c63-01': 'Street View (2023): the planters wrap round the corner. Measure from the wall, behind them.',
}
for d in PLAN:
    if d['id'] in HINTS:
        d['hint'] = HINTS[d['id']]
assert set(HINTS) <= {d['id'] for d in PLAN}


# ---- Laying the distances out on the sketch, as a plan is dimensioned: a setback runs from the
# wall to the curb; a length is drawn beside its wall, on the building's side, the shorter ones
# closer and the longer ones further out, with extension lines back to the wall; and every number
# stands clear of every other number and every other line.

def unit(a, b):
    L = math.dist(a, b) or 1
    return ((b[0] - a[0]) / L, (b[1] - a[1]) / L)


def seg_dist(p, a, b):
    ax, ay = a
    dx, dy = b[0] - ax, b[1] - ay
    L2 = dx * dx + dy * dy
    t = 0 if L2 == 0 else max(0, min(1, ((p[0] - ax) * dx + (p[1] - ay) * dy) / L2))
    return math.hypot(p[0] - ax - t * dx, p[1] - ay - t * dy)


def overlap(p, q):
    """How many metres two segments run on top of each other."""
    (p1, p2), (q1, q2) = p, q
    ux, uy = unit(p1, p2)
    vx, vy = unit(q1, q2)
    if abs(ux * vy - uy * vx) > math.sin(math.radians(4)):
        return 0
    off = lambda r: abs((r[0] - p1[0]) * uy - (r[1] - p1[1]) * ux)
    if off(q1) > 0.35 or off(q2) > 0.35:
        return 0
    along = sorted(((q1[0] - p1[0]) * ux + (q1[1] - p1[1]) * uy, (q2[0] - p1[0]) * ux + (q2[1] - p1[1]) * uy))
    return max(0, min(math.dist(p1, p2), along[1]) - max(0, along[0]))


CENTRE = (sum(x for x, _ in BLOCK_RING) / len(BLOCK_RING), sum(y for _, y in BLOCK_RING) / len(BLOCK_RING))

for d in PLAN:
    d['va'], d['vb'] = to_view(d['a']), to_view(d['b'])
lines = {d['id']: (d['va'], d['vb']) for d in PLAN if d['kind'] == 'setback'}

for d in sorted((d for d in PLAN if d['kind'] == 'length'), key=lambda d: d['expected']):
    a, b = d['va'], d['vb']
    ux, uy = unit(a, b)
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    # The building's side of the wall: where a step off it is built; else the block's inside.
    sides = [(-uy, ux), (uy, -ux)]
    built = [n for n in sides if any(point_in((mid[0] + n[0] * 0.6, mid[1] + n[1] * 0.6), r) for r in BUILT)]
    n = built[0] if len(built) == 1 else min(sides, key=lambda n: math.dist((mid[0] + n[0], mid[1] + n[1]), CENTRE))
    if 'along' in d:
        # Drawn where it was told, square to the way it is measured.
        (ux, uy), c = d['along'], d['through']
        foot = lambda p: (c[0] + ux * ((p[0] - c[0]) * ux + (p[1] - c[1]) * uy), c[1] + uy * ((p[0] - c[0]) * ux + (p[1] - c[1]) * uy))
        d['vda'], d['vdb'], d['side'] = foot(a), foot(b), n
        lines[d['id']] = (d['vda'], d['vdb'])
        continue
    for level in range(1, 6):
        off = 0.8 + 0.9 * (level - 1)
        da, db = (a[0] + n[0] * off, a[1] + n[1] * off), (b[0] + n[0] * off, b[1] + n[1] * off)
        if all(overlap((da, db), other) < 0.2 and overlap(other, (da, db)) < 0.2 for other in lines.values()):
            break
    else:
        raise SystemExit(f"{d['id']}: no room beside its wall for its dimension line")
    d['vda'], d['vdb'], d['side'] = da, db, n
    lines[d['id']] = (da, db)

tags = {}
for d in PLAN:
    a, b = lines[d['id']]
    ux, uy = unit(a, b)
    L = math.dist(a, b)
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    if d['kind'] == 'setback':
        wanted = [(b[0] + ux * r, b[1] + uy * r) for r in (1.3, 2.3, 3.3)]
    else:
        n = d['side']
        wanted = [(mid[0] + ux * f * L + n[0] * r, mid[1] + uy * f * L + n[1] * r)
                  for r in (0, 0.9) for f in (0, 0.25, -0.25, 0.4, -0.4)]
    # Then anywhere round the line, a little further each time.
    wanted += [(mid[0] + ux * f * L + (-uy) * s * r, mid[1] + uy * f * L + ux * s * r)
               for r in (1.0, 1.6, 2.2) for s in (1, -1) for f in (0, 0.3, -0.3)]
    for t in wanted:
        if (all(math.dist(t, o) >= 1.5 for o in tags.values())
                and all(seg_dist(t, *seg) >= 0.5 for i, seg in lines.items() if i != d['id'])):
            tags[d['id']] = t
            break
    else:
        raise SystemExit(f"{d['id']}: no room for its number on the sketch")
    d['tag'] = from_view(tags[d['id']])
    if d['kind'] == 'length':
        d['dim'] = [from_view(d['vda']), from_view(d['vdb'])]

# The check a person would make: no two lines on top of each other, no two numbers touching.
problems = []
ids = list(lines)
for i, p in enumerate(ids):
    for q in ids[i + 1:]:
        o = overlap(lines[p], lines[q])
        if o >= 0.2:
            problems.append(f'{p} and {q} run {o:.1f} m on top of each other')
for i, p in enumerate(ids):
    for q in ids[i + 1:]:
        if math.dist(tags[p], tags[q]) < 1.5:
            problems.append(f'the numbers of {p} and {q} touch')
if problems:
    raise SystemExit('The sketch would not read:\n  ' + '\n  '.join(problems))


def ts(value):
    """A TypeScript literal as the portal's Prettier writes it: single quotes, unless the text has one."""
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False) if "'" in value else "'" + value.replace('\\', '\\\\') + "'"
    return json.dumps(value)


out = [
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
    out.append('  {')
    for key in ('n', 'id', 'street', 'kind', 'building', 'text', 'hint', 'a', 'b', 'dim', 'tag', 'expected'):
        if key in d:
            # The description and the hint are shown translated: marked, so the i18n check finds their Spanish.
            out.append(f'    {key}: {"/* i18n */ " if key in ("text", "hint") else ""}{ts(d[key])},')
    out.append('  },')
out.append('];')
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(out) + '\n')
print(f'{len(PLAN)} distances -> {os.path.relpath(OUT, ROOT)}')
