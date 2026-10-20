#!/usr/bin/env python3
"""
Draws a campus and a building's floors straight from map-service's seeds, as SVG, and as PNG when a
headless Chrome or rsvg-convert is at hand. Nothing is drawn by eye: every outline is a seed's, laid
out with the same geometry the portal uses (web-admin's features/data/ground/ground.ts).

    scripts/render-map-base.py                                  # campus, EC P1 and P2
    scripts/render-map-base.py --building EC --floors 1,2,3 --out /tmp/renders

By default the files go to docs/entrega/segunda-entrega/mapa-base/: campus.svg, ec-p1.svg and
ec-p2.svg, each with its .png. The seeds are the snapshot scripts/export-map-snapshot.py writes from
the API, so what is drawn is what the database held at the last export.

Standard library only, so it runs on any machine with python3.
"""

import argparse
import html
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCES = os.path.join(REPO, "app", "backend", "microservices", "map-service", "src", "main", "resources", "db")
SEEDS = os.path.join(RESOURCES, "seed", "map")
STRUCTURES = os.path.join(RESOURCES, "seed", "structures")
GROUND = os.path.join(RESOURCES, "ground")
DEFAULT_OUT = os.path.join(REPO, "docs", "entrega", "segunda-entrega", "mapa-base")

# ground.ts: a campus is small enough for the plane through its middle to stand in for the earth.
METRES_PER_DEGREE_LAT = 110574
METRES_PER_DEGREE_LON = 111320
COMPASS = ["NORTH", "EAST", "SOUTH", "WEST"]

# The categories and their fills, as the portal's floor editor draws them (spaces/space.model.ts),
# and which category each type of the catalogue belongs to (V004_MapModelV2's CATALOGUE).
CATEGORY_COLORS = {
    "TEACHING": "#cfe0ff", "PUBLIC_SERVICE": "#f6e2c8", "OFFICE": "#e6dcff", "SOCIAL": "#d6f0d8",
    "FACILITIES": "#dfe4ea", "CIRCULATION": "#fff3a8", "OTHER": "#ffd9d4",
}
CATEGORY_NAMES = {
    "TEACHING": "Docencia", "PUBLIC_SERVICE": "Atención", "OFFICE": "Oficina", "SOCIAL": "Bienestar y social",
    "FACILITIES": "Servicio", "CIRCULATION": "Circulación", "OTHER": "Sin identificar",
}
TYPE_CATEGORY = {
    **dict.fromkeys(["CLASSROOM", "LAB", "COMPUTER_LAB", "AUDITORIUM", "LIBRARY", "GESELL_CHAMBER"], "TEACHING"),
    **dict.fromkeys(["ADMIN_OFFICE", "RECEPTION", "WAITING_ROOM", "CONSULTATION_ROOM", "MEDICAL_OFFICE",
                     "NURSING", "PRINT_SHOP"], "PUBLIC_SERVICE"),
    **dict.fromkeys(["OFFICE", "MEETING_ROOM", "EVENT_HALL", "ARCHIVE"], "OFFICE"),
    **dict.fromkeys(["CAFETERIA", "SOCIAL_AREA", "GYM", "GAME_ROOM", "ARTS_ROOM", "TERRACE", "WELLBEING"], "SOCIAL"),
    **dict.fromkeys(["RESTROOM", "CLEANING_ROOM", "IT_ROOM", "ELECTRICAL_ROOM", "WASTE_ROOM", "LOCKER_ROOM",
                     "STORAGE", "PARKING"], "FACILITIES"),
    **dict.fromkeys(["ELEVATOR", "STAIRS", "RAMP", "CORRIDOR", "ENTRANCE"], "CIRCULATION"),
}
STATUS_NAMES = {"DRAFT": "borrador", "PUBLISHED": "publicado", "UNMAPPED": "sin dibujar"}

INK = "#2b2337"
BRAND = "#5b2a86"
FONT = "Helvetica, Arial, sans-serif"
WIDTH_PX = 1600          # the drawing area's width; the PNG is twice that, or less to stay light
PNG_LIMIT = 1_000_000    # docs/entrega's rule: every image under 1 MB
HEADER_PX = 74
FOOTER_PX = 64


# --- geometry -----------------------------------------------------------------------------------

def to_metres(origin, c):
    """Metres east and north of `origin` (ground.ts toMetres)."""
    return ((c[0] - origin["lon"]) * METRES_PER_DEGREE_LON * math.cos(math.radians(origin["lat"])),
            (c[1] - origin["lat"]) * METRES_PER_DEGREE_LAT)


def from_metres(origin, east, north):
    return (origin["lon"] + east / (METRES_PER_DEGREE_LON * math.cos(math.radians(origin["lat"]))),
            origin["lat"] + north / METRES_PER_DEGREE_LAT)


def drawing_to_ground(placement, p):
    b, m = math.radians(placement["bearing"]), placement["metresPerUnit"]
    east = m * (p[0] * math.cos(b) - p[1] * math.sin(b))
    north = -m * (p[0] * math.sin(b) + p[1] * math.cos(b))
    return from_metres(placement["origin"], east, north)


def ground_to_drawing(placement, c):
    b, m = math.radians(placement["bearing"]), placement["metresPerUnit"]
    east, north = to_metres(placement["origin"], c)
    u, v = east / m, -north / m
    return (u * math.cos(b) + v * math.sin(b), -u * math.sin(b) + v * math.cos(b))


def nearest_quarter(bearing):
    return COMPASS[round((bearing % 360) / 90) % 4]


def placement_for_floor(building, top, width, height):
    """The placement of a floor turned by quarters in the editor (ground.ts placementForFloor)."""
    if not top:
        return building
    quarters = (COMPASS.index(nearest_quarter(building["bearing"])) - COMPASS.index(top)) % 4
    placement = building
    w, h = (height, width) if quarters % 2 else (width, height)
    for _ in range(quarters):
        lon, lat = drawing_to_ground(placement, (0, h))
        placement = {**placement, "origin": {"lat": lat, "lon": lon}, "bearing": (placement["bearing"] - 90) % 360}
        w, h = h, w
    return placement


def reaches(part, level):
    """Whether a footprint part rises to the floor at `level` (ground.ts reaches)."""
    if level < 0:
        return part.get("basements", 0) >= -math.floor(level)
    floor = max(1, math.ceil(level))
    return part.get("floors", 0) >= floor and (part.get("lowestFloor") or 1) <= floor


def bbox(points):
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def area_and_centroid(points):
    a = cx = cy = 0.0
    for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]):
        cross = x0 * y1 - x1 * y0
        a += cross
        cx += (x0 + x1) * cross
        cy += (y0 + y1) * cross
    if abs(a) < 1e-9:
        x0, y0, x1, y1 = bbox(points)
        return 0.0, ((x0 + x1) / 2, (y0 + y1) / 2)
    return abs(a) / 2, (cx / (3 * a), cy / (3 * a))


def widest_row(points, rows=21):
    """The middle and width of the widest horizontal stretch inside a polygon, near its middle row:
    where a label fits, even in an L or an arc, whose centroid can fall outside it."""
    x0, y0, x1, y1 = bbox(points)
    best = None
    for i in range(1, rows):
        y = y0 + (y1 - y0) * i / rows
        xs = []
        for (ax, ay), (bx, by) in zip(points, points[1:] + points[:1]):
            if (ay <= y < by) or (by <= y < ay):
                xs.append(ax + (y - ay) * (bx - ax) / (by - ay))
        xs.sort()
        for left, right in zip(xs[::2], xs[1::2]):
            # Favour the rows near the middle: a label on the edge of a room reads as the next one's.
            score = (right - left) * (1 - 0.6 * abs(i / rows - 0.5))
            if best is None or score > best[0]:
                best = (score, ((left + right) / 2, y), right - left)
    return (best[1], best[2]) if best else (((x0 + x1) / 2, (y0 + y1) / 2), x1 - x0)


# --- SVG ----------------------------------------------------------------------------------------

def path(points):
    return "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in points) + " Z"


def text_width(text, size):
    return len(text) * size * 0.55


def fit_label(text, width, height, max_size=13, min_size=7):
    """The largest font size, and the lines, at which `text` fits a box; None when nothing fits."""
    words = text.split()
    candidates = [[text]]
    if len(words) > 1:
        # Two lines, broken at the space nearest the middle.
        best = min(range(1, len(words)), key=lambda i: abs(len(" ".join(words[:i])) - len(" ".join(words[i:]))))
        candidates.append([" ".join(words[:best]), " ".join(words[best:])])
    for size in range(max_size, min_size - 1, -1):
        for lines in candidates:
            if (max(text_width(line, size) for line in lines) <= width * 0.9
                    and len(lines) * size * 1.15 <= height * 0.85):
                return size, lines
    return None


def label(x, y, lines, size, weight="normal", rotate=0, fill=INK):
    tspans = "".join(
        f'<tspan x="{x:.1f}" dy="{(0 if i else -(len(lines) - 1) * 0.575 * size) + (size * 1.15 if i else 0):.1f}">'
        f"{html.escape(line)}</tspan>" for i, line in enumerate(lines))
    turn = f' transform="rotate({rotate} {x:.1f} {y:.1f})"' if rotate else ""
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" font-weight="{weight}" fill="{fill}" '
            f'text-anchor="middle" dominant-baseline="central"{turn}>{tspans}</text>')


def document(width, height, body, title, subtitle, footer, note=""):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width:.0f}" height="{height:.0f}" viewBox="0 0 {width:.0f} {height:.0f}" font-family="{FONT}">
<rect width="100%" height="100%" fill="#ffffff"/>
<text x="24" y="34" font-size="24" font-weight="bold" fill="{BRAND}">{html.escape(title)}</text>
<text x="24" y="58" font-size="13" fill="#6b6578">{html.escape(subtitle)}</text>
{body}
<text x="24" y="{height - 22:.0f}" font-size="11" fill="#6b6578">{html.escape(footer)}</text>
{f'<text x="24" y="{height - 42:.0f}" font-size="12" fill="{INK}">{html.escape(note)}</text>' if note else ""}
</svg>
"""


def north_arrow(x, y, dx, dy):
    """An arrow at (x, y) pointing along (dx, dy), the way north lies on the drawing."""
    n = math.hypot(dx, dy) or 1
    dx, dy = dx / n, dy / n
    tip = (x + dx * 14, y + dy * 14)
    notch = (x - dx * 8, y - dy * 8)
    left = (x - dx * 16 - dy * 10, y - dy * 16 + dx * 10)
    right = (x - dx * 16 + dy * 10, y - dy * 16 - dx * 10)
    letter = (x + dx * 25, y + dy * 25)
    return (f'<circle cx="{x}" cy="{y}" r="36" fill="#ffffff" fill-opacity="0.9" stroke="#d7d2de"/>'
            f'<path d="{path([tip, left, notch, right])}" fill="{BRAND}"/>'
            f'<text x="{letter[0]:.1f}" y="{letter[1]:.1f}" font-size="12" font-weight="bold" fill="{BRAND}" '
            f'text-anchor="middle" dominant-baseline="central">N</text>')


def scale_bar(x, y, px_per_metre):
    metres = next(m for m in (5, 10, 20, 25, 50, 100, 200) if m * px_per_metre >= 70)
    w = metres * px_per_metre
    return (f'<rect x="{x}" y="{y}" width="{w:.1f}" height="6" fill="{INK}"/>'
            f'<rect x="{x + w / 2:.1f}" y="{y}" width="{w / 2:.1f}" height="6" fill="#ffffff" stroke="{INK}"/>'
            f'<text x="{x}" y="{y - 6}" font-size="11" fill="{INK}">0</text>'
            f'<text x="{x + w:.1f}" y="{y - 6}" font-size="11" fill="{INK}" text-anchor="end">{metres} m</text>')


def legend(x, y, categories):
    items = []
    for i, category in enumerate(categories):
        items.append(f'<rect x="{x + i * 150}" y="{y}" width="14" height="14" fill="{CATEGORY_COLORS[category]}" '
                     f'stroke="#8b8496"/><text x="{x + i * 150 + 20}" y="{y + 11}" font-size="12" fill="{INK}">'
                     f"{CATEGORY_NAMES[category]}</text>")
    return "".join(items)


def ground_layers(ground, project, view):
    """The city under the drawing: blocks, lots, sidewalks, roadways and medians within `view`."""
    vx0, vy0, vx1, vy1 = view

    def within(points):
        x0, y0, x1, y1 = bbox(points)
        return x1 >= vx0 and y1 >= vy0 and x0 <= vx1 and y0 <= vy1

    layers = []
    for key, fill, stroke, extra in (
            ("blocks", "#f1efe9", "none", ""),
            ("sidewalks", "#e4e1d8", "none", ""),
            ("roadways", "#fbfbf9", "#d9d6cd", ' stroke-width="0.8"'),
            ("medians", "#d5e6c9", "none", "")):
        for ring in ground.get(key, []):
            points = [project(c) for c in ring]
            if len(points) >= 3 and within(points):
                layers.append(f'<path d="{path(points)}" fill="{fill}" stroke="{stroke}"{extra}/>')
    for lot in ground.get("lots", []):
        points = [project(c) for c in lot["ring"]]
        if len(points) >= 3 and within(points):
            layers.append(f'<path d="{path(points)}" fill="none" stroke="#cbc6bb" stroke-width="0.8" '
                          f'stroke-dasharray="4 3"/>')
    return layers


def street_labels(ground, project, view, min_px=140):
    vx0, vy0, vx1, vy1 = view
    out, seen = [], set()
    for street in ground.get("streets", []):
        name = street.get("label") or street.get("name")
        points = [project(c) for c in street.get("path", [])]
        inside = [p for p in points if vx0 + 40 <= p[0] <= vx1 - 40 and vy0 + 20 <= p[1] <= vy1 - 20]
        if not name or len(inside) < 2:
            continue
        segments = sorted(zip(inside, inside[1:]), key=lambda s: -math.dist(*s))
        (ax, ay), (bx, by) = segments[0]
        if math.dist((ax, ay), (bx, by)) < min_px or (name, round(ax, -2), round(ay, -2)) in seen:
            continue
        seen.add((name, round(ax, -2), round(ay, -2)))
        angle = math.degrees(math.atan2(by - ay, bx - ax))
        if angle > 90:
            angle -= 180
        elif angle < -90:
            angle += 180
        out.append(label((ax + bx) / 2, (ay + by) / 2, [name], 11, rotate=round(angle, 1), fill="#8b8579"))
    return out


def outlined(parts, fill, stroke, width):
    """Parts that share edges drawn as one: every part stroked thick, then every part filled over the
    strokes, so only the outside edge is left showing."""
    strokes = "".join(f'<path d="{path(p)}" fill="{stroke}" stroke="{stroke}" stroke-width="{width}" '
                      f'stroke-linejoin="round"/>' for p in parts)
    # A hairline of the fill's own colour hides the seams where two parts meet.
    fills = "".join(f'<path d="{path(p)}" fill="{fill}" stroke="{fill}" stroke-width="1.2"/>' for p in parts)
    return strokes + fills


# --- the two kinds of drawing -------------------------------------------------------------------

def load_campus(campus):
    buildings = []
    for name in sorted(os.listdir(SEEDS)):
        if name.endswith(".json"):
            with open(os.path.join(SEEDS, name), encoding="utf-8") as f:
                b = json.load(f)
            if b.get("campus") == campus:
                buildings.append(b)
    slug = campus.lower().replace(" ", "-")
    ground = structures = {}
    if os.path.exists(os.path.join(GROUND, slug + ".json")):
        with open(os.path.join(GROUND, slug + ".json"), encoding="utf-8") as f:
            ground = json.load(f)
    if os.path.exists(os.path.join(STRUCTURES, slug + ".json")):
        with open(os.path.join(STRUCTURES, slug + ".json"), encoding="utf-8") as f:
            structures = json.load(f)
    return buildings, ground, structures.get("structures", [])


def render_campus(campus, buildings, ground, structures):
    rings = [part["ring"] for b in buildings for part in b.get("footprint") or []]
    rings += [s["ring"] for s in structures]
    every = [c for ring in rings for c in ring]
    origin = {"lon": sum(c[0] for c in every) / len(every), "lat": sum(c[1] for c in every) / len(every)}
    metres = [to_metres(origin, c) for c in every]
    margin = 30
    x0, y0 = min(e for e, _ in metres) - margin, min(-n for _, n in metres) - margin
    x1, y1 = max(e for e, _ in metres) + margin, max(-n for _, n in metres) + margin
    scale = WIDTH_PX / (x1 - x0)
    height = (y1 - y0) * scale

    def project(c):
        e, n = to_metres(origin, c)
        return ((e - x0) * scale, (-n - y0) * scale + HEADER_PX)

    view = (0, HEADER_PX, WIDTH_PX, HEADER_PX + height)
    body = [f'<clipPath id="area"><rect x="0" y="{HEADER_PX}" width="{WIDTH_PX}" height="{height:.1f}"/></clipPath>',
            '<g clip-path="url(#area)">']
    body += ground_layers(ground, project, view)
    for s in structures:
        body.append(f'<path d="{path([project(c) for c in s["ring"]])}" fill="#dedad2" stroke="#bdb8ad"/>')
    labels = []
    unplaced = []
    for b in buildings:
        parts = [[project(c) for c in part["ring"]] for part in b.get("footprint") or []]
        if not parts:
            unplaced.append(f"{b['code']} ({b['name']})")
            continue
        body.append(outlined(parts, "#e6dcff", BRAND, 3))
        weighted = [area_and_centroid(p) for p in parts]
        total = sum(a for a, _ in weighted) or 1
        cx = sum(a * c[0] for a, c in weighted) / total
        cy = sum(a * c[1] for a, c in weighted) / total
        labels.append(label(cx, cy - 8, [b["code"]], 15, weight="bold", fill=BRAND))
        fitted = fit_label(b["name"], 220, 60, max_size=11, min_size=9)
        if fitted:
            labels.append(label(cx, cy + 12, fitted[1], fitted[0]))
    body += street_labels(ground, project, view)
    body += labels
    body.append("</g>")
    body.append(north_arrow(WIDTH_PX - 54, HEADER_PX + 54, 0, -1))
    body.append(scale_bar(24, HEADER_PX + height + 26, scale))
    body.append(f'<rect x="200" y="{HEADER_PX + height + 18}" width="14" height="14" fill="#e6dcff" stroke="{BRAND}" '
                f'stroke-width="2"/><text x="220" y="{HEADER_PX + height + 29}" font-size="12" fill="{INK}">'
                f'Edificios de la universidad</text>'
                f'<rect x="400" y="{HEADER_PX + height + 18}" width="14" height="14" fill="#dedad2" stroke="#bdb8ad"/>'
                f'<text x="420" y="{HEADER_PX + height + 29}" font-size="12" fill="{INK}">Otras construcciones</text>')
    footer = (f"Edificios: seeds de map-service (huella catastral de cada uno). Calles y manzanas: "
              f"{ground.get('source', 'sin fuente')}, consultado {ground.get('retrieved', '')}.")
    note = ("Sin huella en el mapa: " + ", ".join(unplaced) + ".") if unplaced else ""
    drawn = len(buildings) - len(unplaced)
    return document(WIDTH_PX, HEADER_PX + height + FOOTER_PX + (28 if note else 0), "\n".join(body),
                    f"{campus} · campus", f"{drawn} edificios con su huella · norte arriba", footer, note)


def render_floor(building, floor, ground):
    level, w, h = floor["level"], floor["width"], floor["height"]
    placement = placement_for_floor(building["placement"], floor.get("top"), w, h) if building.get("placement") else None
    pad = 0.08 * max(w, h)
    vx0, vy0, vx1, vy1 = -pad, -pad, w + pad, h + pad
    scale = WIDTH_PX / (vx1 - vx0)
    height = (vy1 - vy0) * scale

    def px(p):
        return ((p[0] - vx0) * scale, (p[1] - vy0) * scale + HEADER_PX)

    def project(c):
        return px(ground_to_drawing(placement, c))

    view = (0, HEADER_PX, WIDTH_PX, HEADER_PX + height)
    body = [f'<clipPath id="area"><rect x="0" y="{HEADER_PX}" width="{WIDTH_PX}" height="{height:.1f}"/></clipPath>',
            '<g clip-path="url(#area)">']
    if placement:
        body += ground_layers(ground, project, view)
        parts = [[project(c) for c in part["ring"]] for part in building.get("footprint") or [] if reaches(part, level)]
        if parts:
            body.append(outlined(parts, "#ffffff", "#8b8496", 4))
    categories = set()
    labels = []
    unplaced = []
    for space in floor["spaces"]:
        # A space with no shape is on the floor but not placed on its drawing yet.
        if len(space.get("shape") or []) < 3:
            unplaced.append(space["name"])
            continue
        points = [px((p["x"], p["y"])) for p in space["shape"]]
        if len(points) < 3:
            continue
        category = TYPE_CATEGORY.get(space["typeCode"], "OTHER")
        categories.add(category)
        body.append(f'<path d="{path(points)}" fill="{CATEGORY_COLORS[category]}" stroke="#6b6578" '
                    f'stroke-width="1" stroke-linejoin="round"/>')
        x0, y0, x1, y1 = bbox(points)
        unnamed = space["name"].strip().lower() in ("", "sin identificar")
        texts = [space["code"]] if unnamed else [space["name"], space["code"]]
        (cx, cy), row = widest_row(points)
        for text in texts:
            fitted = fit_label(text, row, y1 - y0)
            rotate = 0
            if not fitted and (y1 - y0) > 1.6 * (x1 - x0):
                # A tall, narrow space - a corridor, a stair - reads its name turned.
                fitted = fit_label(text, y1 - y0, x1 - x0)
                rotate, cx, cy = -90, (x0 + x1) / 2, (y0 + y1) / 2
            if fitted:
                labels.append(label(cx, cy, fitted[1], fitted[0], rotate=rotate))
                break
    if placement:
        body += street_labels(ground, project, view)
    body += labels
    body.append("</g>")
    if placement:
        # North on the drawing: east = 0, north = 1 m, which is u = 0, v = -1/m.
        b = math.radians(placement["bearing"])
        body.append(north_arrow(WIDTH_PX - 54, HEADER_PX + 54, -math.sin(b), -math.cos(b)))
        body.append(scale_bar(24, HEADER_PX + height + 26, scale / placement["metresPerUnit"]))
    order = [c for c in CATEGORY_COLORS if c in categories]
    body.append(legend(200, HEADER_PX + height + 18, order))
    status = STATUS_NAMES.get(floor.get("status"), floor.get("status", "").lower())
    subtitle = f"{len(floor['spaces'])} espacios · estado: {status} · los espacios sin nombre llevan su código"
    footer = ("Espacios y huella: seeds de map-service. Calles: "
              f"{ground.get('source', 'sin fuente')}, consultado {ground.get('retrieved', '')}." if placement
              else "Espacios: seeds de map-service.")
    note = (f"Sin ubicar en el plano ({len(unplaced)}): " + ", ".join(unplaced) + ".") if unplaced else ""
    return document(WIDTH_PX, HEADER_PX + height + FOOTER_PX + (28 if note else 0), "\n".join(body),
                    f"{building['name']} · {floor['name']}", subtitle, footer, note)


# --- PNG ----------------------------------------------------------------------------------------

def chrome():
    for candidate in ("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                      "/Applications/Chromium.app/Contents/MacOS/Chromium",
                      shutil.which("google-chrome"), shutil.which("chromium"), shutil.which("chromium-browser")):
        if candidate and os.path.exists(candidate):
            return candidate
    return None


def to_png(svg_path, png_path, width, height):
    """At twice the size, for a mockup to zoom into, or smaller until it is under PNG_LIMIT.
    rsvg-convert if there is one, else headless Chrome."""
    browser = None if shutil.which("rsvg-convert") else chrome()
    if not browser and not shutil.which("rsvg-convert"):
        return False
    for zoom in (2, 1.5, 1):
        if browser:
            with tempfile.TemporaryDirectory() as profile:
                subprocess.run([browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                                f"--user-data-dir={profile}", f"--force-device-scale-factor={zoom}",
                                f"--window-size={int(width)},{int(height)}", f"--screenshot={png_path}",
                                "file://" + os.path.abspath(svg_path)],
                               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        else:
            subprocess.run(["rsvg-convert", "-z", str(zoom), "-o", png_path, svg_path], check=True)
        if os.path.getsize(png_path) < PNG_LIMIT:
            break
    return True


def size_of(svg):
    head = svg.split(">", 1)[0]
    width = float(head.split('width="', 1)[1].split('"', 1)[0])
    height = float(head.split('height="', 1)[1].split('"', 1)[0])
    return width, height


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--building", default="EC", help="the building's code (default EC)")
    parser.add_argument("--floors", default="1,2", help="the floors' levels, comma separated (default 1,2)")
    parser.add_argument("--out", default=DEFAULT_OUT, help="where the files go")
    parser.add_argument("--no-png", action="store_true", help="SVG only")
    args = parser.parse_args()

    with open(os.path.join(SEEDS, args.building.lower() + ".json"), encoding="utf-8") as f:
        building = json.load(f)
    buildings, ground, structures = load_campus(building["campus"])
    os.makedirs(args.out, exist_ok=True)

    drawings = [("campus", render_campus(building["campus"], buildings, ground, structures))]
    for level in (int(x) for x in args.floors.split(",")):
        floor = next((f for f in building["floors"] if f["level"] == level), None)
        if floor is None:
            sys.exit(f"{building['code']} has no floor at level {level}")
        drawings.append((f"{building['code'].lower()}-{floor['code'].lower()}", render_floor(building, floor, ground)))

    missing_png = False
    for name, svg in drawings:
        svg_path = os.path.join(args.out, name + ".svg")
        with open(svg_path, "w", encoding="utf-8") as f:
            f.write(svg)
        made = not args.no_png and to_png(svg_path, os.path.join(args.out, name + ".png"), *size_of(svg))
        missing_png |= not args.no_png and not made
        print(os.path.relpath(svg_path, REPO) + (" + .png" if made else ""))
    if missing_png:
        print("No PNG: install rsvg-convert (librsvg) or Google Chrome.", file=sys.stderr)


if __name__ == "__main__":
    main()
