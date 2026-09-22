"""Reads the Psicología 2020 grid: a name box, and beside it a narrow box with C (credits), code, H (hours)."""
import json, re, sys, math
import pdfplumber
from collections import Counter

def cx(o): return (o["x0"] + o["x1"]) / 2
def cy(o): return (o["top"] + o["bottom"]) / 2

pdf = pdfplumber.open(sys.argv[1]); page = pdf.pages[0]
words = page.extract_words(x_tolerance=0.8, y_tolerance=1)
sem = sorted((w["x0"], int(n["text"])) for w in words if w["text"] == "Semestre"
             for n in words if abs(n["top"] - w["top"]) < 1 and 0 < n["x0"] - w["x1"] < 6 and n["text"].isdigit())
print("semesters", sem, file=sys.stderr)

tall = [r for r in page.rects if 20 < r["height"] < 40]
narrow = [r for r in tall if 10 < r["width"] < 20]
courses = []
for r in narrow:
    inside = [w for w in words if r["x0"] - 1 <= cx(w) <= r["x1"] + 1 and r["top"] - 1 <= cy(w) <= r["bottom"] + 1]
    code = [w for w in inside if re.fullmatch(r"P?\d{4,5}", w["text"])]
    def after(letter):
        ls = [w for w in inside if w["text"] == letter]
        if not ls: return None
        l = ls[0]
        vals = [w for w in words if abs(w["top"] - l["top"]) < 1 and 0 < w["x0"] - l["x1"] < 16]
        return vals[0]["text"] if vals else None
    namebox = [q for q in tall if q["width"] > 30 and abs(q["x1"] - r["x0"]) < 2.5 and abs(q["top"] - r["top"]) < 3]
    nb = namebox[0] if namebox else None
    name_words = sorted([w for w in words if nb and nb["x0"] - 1 <= cx(w) <= nb["x1"] + 1 and nb["top"] - 1 <= cy(w) <= nb["bottom"] + 1],
                        key=lambda w: (round(w["top"] / 2), w["x0"]))
    level = max((s for s in sem if s[0] <= (nb or r)["x0"] + 8), default=(0, 0))[1]
    courses.append(dict(code=code[0]["text"] if code else "?" + " ".join(w["text"] for w in inside),
                        name=" ".join(w["text"] for w in name_words), credits=after("C"), hours=after("H"),
                        level=level, top=round(r["top"], 1), box=[(nb or r)["x0"], r["top"], r["x1"], r["bottom"]]))

areas = []
for w in words:
    if w["text"] == "ÁREA" and w["x1"] < 110:
        near = sorted([v for v in words if v["x1"] < 110 and -1 < v["top"] - w["top"] < 9 and v["text"].isupper()],
                      key=lambda v: (round(v["top"]), v["x0"]))
        areas.append((cy(w), " ".join(v["text"] for v in near)))
# each area box is framed by a vertical rule on its left edge
bands = sorted((r["top"], r["bottom"]) for r in page.rects if r["x1"] < 60 and r["width"] < 2 and r["height"] > 30)
for c in courses:
    y = (c["box"][1] + c["box"][3]) / 2
    band = min(bands, key=lambda t: 0 if t[0] - 2 <= y <= t[1] + 2 else min(abs(y - t[0]), abs(y - t[1])))
    c["area"] = next(name for top, name in areas if band[0] <= top <= band[1])
courses.sort(key=lambda c: (c["level"], c["top"]))
tot_c = sum(int(c["credits"]) for c in courses if c["credits"])
tot_h = sum(float(c["hours"].replace(",", ".")) for c in courses if c["hours"])
print(len(courses), "courses", tot_c, "credits", tot_h, "hours")
for c in courses:
    print(f"  L{c['level']} {c['code']:8} C{c['credits']!s:>3} H{c['hours']!s:>4} {c['area'][:28]:28} {c['name']}")
json.dump(courses, open(sys.argv[2], "w"), ensure_ascii=False, indent=1)

# ---- connectors: plain lines between boxes, no arrowheads; direction comes from the semesters --------
def inside_any(o, pad=1.2):
    return any(c["box"][0] - pad <= o["x0"] and o["x1"] <= c["box"][2] + pad and c["box"][1] - pad <= o["top"] and o["bottom"] <= c["box"][3] + pad
               for c in courses)
segs = []
for r in page.rects:
    if r["x0"] < 110 or r["top"] < 70 or r["top"] > 505:
        continue
    thin_h, thin_v = r["height"] < 1.6 and r["width"] > 2.5, r["width"] < 1.6 and r["height"] > 2.5
    if not (thin_h or thin_v) or inside_any(r):
        continue
    if (round(r["width"]), round(r["height"])) in ((4, 1), (1, 4), (3, 1), (1, 3)):
        continue  # dashes of the dashed box borders
    segs.append((r["x0"], cy(r), r["x1"], cy(r)) if thin_h else (cx(r), r["top"], cx(r), r["bottom"]))

def pdist(p, s):
    x0, y0, x1, y1 = s; dx, dy = x1 - x0, y1 - y0
    t = 0 if dx == dy == 0 else max(0, min(1, ((p[0] - x0) * dx + (p[1] - y0) * dy) / (dx * dx + dy * dy)))
    return math.dist(p, (x0 + t * dx, y0 + t * dy))

def box_at(p, tol=2.0):
    return [c for c in courses if c["box"][0] - tol <= p[0] <= c["box"][2] + tol and c["box"][1] - tol <= p[1] <= c["box"][3] + tol]

def free(p): return not box_at(p)

comp_of = {}
comps = []
for s in segs:
    if s in comp_of: continue
    comp, frontier = [s], [s]
    comp_of[s] = len(comps)
    while frontier:
        a = frontier.pop()
        for b in segs:
            if b in comp_of: continue
            ends_a, ends_b = ((a[0], a[1]), (a[2], a[3])), ((b[0], b[1]), (b[2], b[3]))
            if any(free(p) and pdist(p, b) <= 1.3 for p in ends_a) or any(free(p) and pdist(p, a) <= 1.3 for p in ends_b):
                comp_of[b] = len(comps); comp.append(b); frontier.append(b)
    comps.append(comp)
for comp in comps:
    touched = []
    for s in comp:
        for p in ((s[0], s[1]), (s[2], s[3])):
            for c in box_at(p):
                if c not in touched: touched.append(c)
    if len(touched) >= 2:
        print("  link:", " | ".join(f"L{c['level']} {c['code']} {c['name'][:28]}" for c in sorted(touched, key=lambda c: c["level"])))
    elif comp and max(abs(s[2]-s[0]) + abs(s[3]-s[1]) for s in comp) > 8:
        print("  loose:", [c["code"] for c in touched], [tuple(round(v) for v in s) for s in comp][:4])
