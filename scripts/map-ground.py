#!/usr/bin/env python3
"""The ground around a campus - city blocks, sidewalks, roadways, medians and named streets - cut
from Bogota's reference map and written where map-service serves it from.

    scripts/map-ground.py [--campus "Sede Principal"] [--around 4.6486,-74.0615] [--radius 350]

The source is the Mapa de Referencia para Bogota D.C. that IDECA publishes (UAECD, IDU and others),
under CC BY 4.0: its ArcGIS REST service, layers Manzana, Anden, Calzada, Separador and Malla Vial.
Everything within `radius` metres of `around` is kept, each outline simplified to a quarter of a
metre - finer than any plan on a wall is drawn - and the street axes of one name joined into as
few lines as they make. Standard library only.
"""
import argparse
import datetime
import json
import math
import os
import re
import urllib.parse
import urllib.request

SERVICE = 'https://serviciosgis.catastrobogota.gov.co/arcgis/rest/services/Mapa_Referencia/Mapa_Referencia/MapServer'
LAYERS = {'blocks': 40, 'sidewalks': 16, 'roadways': 15, 'medians': 17, 'streets': 13}
SOURCE = ('Mapa de Referencia para Bogotá D.C., Infraestructura de Datos Espaciales para el Distrito '
          'Capital (IDECA) - UAECD, IDU y otras entidades. CC BY 4.0.')
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'backend', 'microservices', 'map-service',
                   'src', 'main', 'resources', 'db', 'ground')
KINDS = {'CL': 'Calle', 'KR': 'Carrera', 'AK': 'Avenida Carrera', 'AC': 'Avenida Calle', 'DG': 'Diagonal',
         'TV': 'Transversal', 'AV': 'Avenida'}
TOLERANCE = 0.25   # metres


def fetch(layer, box):
    params = urllib.parse.urlencode({
        'geometry': ','.join(str(v) for v in box), 'geometryType': 'esriGeometryEnvelope', 'inSR': 4326,
        'spatialRel': 'esriSpatialRelIntersects', 'outFields': '*', 'outSR': 4326, 'f': 'geojson',
        'resultRecordCount': 2000})
    request = urllib.request.Request(f'{SERVICE}/{layer}/query?{params}',
                                     headers={'User-Agent': 'KApp map-ground script'})
    with urllib.request.urlopen(request, timeout=120) as answer:
        features = json.load(answer).get('features', [])
    if len(features) >= 2000:
        raise SystemExit(f'layer {layer}: the service stopped at 2000 features; ask for a smaller radius')
    return features


class Plane:
    """Metres east and north of a point, near enough the earth for a few hundred metres."""

    def __init__(self, lat, lon):
        self.lat, self.lon = lat, lon
        self.kx = math.cos(math.radians(lat)) * 111320.0
        self.ky = 110574.0

    def to(self, c):
        return ((c[0] - self.lon) * self.kx, (c[1] - self.lat) * self.ky)

    def back(self, p):
        return [round(p[0] / self.kx + self.lon, 7), round(p[1] / self.ky + self.lat, 7)]


def simplify(points, tolerance):
    """Douglas-Peucker on an open line."""
    if len(points) < 3:
        return points
    (x0, y0), (x1, y1) = points[0], points[-1]
    length = math.hypot(x1 - x0, y1 - y0) or 1e-9
    far, index = 0.0, 0
    for i, (x, y) in enumerate(points[1:-1], 1):
        d = abs((x1 - x0) * (y0 - y) - (x0 - x) * (y1 - y0)) / length
        if d > far:
            far, index = d, i
    if far <= tolerance:
        return [points[0], points[-1]]
    return simplify(points[:index + 1], tolerance)[:-1] + simplify(points[index:], tolerance)


def ring(points, tolerance):
    """A closed ring simplified from its two farthest points, first point repeated at the end."""
    if points[0] == points[-1]:
        points = points[:-1]
    if len(points) < 3:
        return None
    start = points[0]
    far = max(range(len(points)), key=lambda i: math.dist(points[i], start))
    kept = simplify(points[:far + 1], tolerance)[:-1] + simplify(points[far:] + [start], tolerance)[:-1]
    return kept + [kept[0]] if len(kept) >= 3 else None


def rings(feature):
    g = feature['geometry']
    if not g:
        return []
    if g['type'] == 'Polygon':
        return [g['coordinates'][0]]
    if g['type'] == 'MultiPolygon':
        return [polygon[0] for polygon in g['coordinates']]
    return []


def lines(feature):
    g = feature['geometry']
    if not g:
        return []
    return [g['coordinates']] if g['type'] == 'LineString' else g['coordinates'] if g['type'] == 'MultiLineString' else []


def street_name(label):
    """'KR 9BIS' -> 'Carrera 9 Bis', 'CL 63A' -> 'Calle 63A', 'AC 63' -> 'Avenida Calle 63'."""
    kind, _, number = label.partition(' ')
    number = re.sub(r'BIS', ' Bis ', number).replace('  ', ' ').strip()
    number = re.sub(r'Bis ([A-Z])$', r'Bis \1', number)
    return f'{KINDS.get(kind, kind)} {number}'.strip()


def join(segments, snap=0.5):
    """Chains the segments of one street that meet end to end into as few lines as they make."""
    pieces = [list(s) for s in segments]
    joined = True
    while joined:
        joined = False
        for i in range(len(pieces)):
            for j in range(len(pieces)):
                if i == j:
                    continue
                a, b = pieces[i], pieces[j]
                if math.dist(a[-1], b[0]) < snap:
                    pieces[i] = a + b[1:]
                elif math.dist(a[-1], b[-1]) < snap:
                    pieces[i] = a + b[::-1][1:]
                else:
                    continue
                del pieces[j]
                joined = True
                break
            if joined:
                break
    return pieces


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--campus', default='Sede Principal')
    parser.add_argument('--around', default='4.6486,-74.0615', help='lat,lon of the middle of the campus')
    parser.add_argument('--radius', type=float, default=350, help='metres around it to keep')
    args = parser.parse_args()
    lat, lon = (float(v) for v in args.around.split(','))
    plane = Plane(lat, lon)
    dlat, dlon = args.radius / plane.ky, args.radius / plane.kx
    box = (lon - dlon, lat - dlat, lon + dlon, lat + dlat)

    ground = {'campus': args.campus, 'source': SOURCE, 'retrieved': datetime.date.today().isoformat()}
    for key in ('blocks', 'sidewalks', 'roadways', 'medians'):
        areas = []
        for feature in fetch(LAYERS[key], box):
            for coordinates in rings(feature):
                kept = ring([plane.to(c) for c in coordinates], TOLERANCE)
                if kept:
                    areas.append([plane.back(p) for p in kept])
        ground[key] = areas
        print(f'{key}: {len(areas)}')

    by_label = {}
    for feature in fetch(LAYERS['streets'], box):
        label = (feature['properties'].get('MVIETIQUET') or '').strip()
        if not label:
            continue
        for coordinates in lines(feature):
            by_label.setdefault(label, []).append([plane.to(c) for c in coordinates])
    streets = []
    for label in sorted(by_label):
        for line in join(by_label[label]):
            streets.append({'name': street_name(label), 'label': label,
                            'path': [plane.back(p) for p in simplify(line, TOLERANCE)]})
    ground['streets'] = streets
    print(f'streets: {len(streets)} lines of {len(by_label)} names')

    os.makedirs(OUT, exist_ok=True)
    slug = re.sub(r'[^a-z0-9]+', '-', args.campus.lower()).strip('-')
    path = os.path.normpath(os.path.join(OUT, f'{slug}.json'))
    with open(path, 'w', encoding='utf-8') as out:
        json.dump(ground, out, ensure_ascii=False, separators=(',', ':'))
        out.write('\n')
    print(f'wrote {path} ({os.path.getsize(path) // 1024} KB)')


if __name__ == '__main__':
    main()
