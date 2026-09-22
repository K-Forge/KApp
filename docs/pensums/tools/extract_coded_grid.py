"""Reads a Konrad coded pensum grid (Sistemas 1015, Matemáticas 1017, Industrial 1020) by geometry.

Cells come in two shapes:
  A (1015, 1017):  HTD CODE HTI  over  credits
  B (1020):        H   CODE C    over  "H ... C"
Integrity check on A: credits x 3 = HTD + HTI.
Prerequisites are the drawn arrows, followed through the connector segments back to a box.
"""
import json, math, re, sys
import pdfplumber

ROMANS = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
CODE = re.compile(r"^(?=(?:[^0-9]*[0-9]){4})[0-9][0-9a-z]{4}$")


def cx(o): return (o["x0"] + o["x1"]) / 2
def cy(o): return (o["top"] + o["bottom"]) / 2


def parse(path):
    pdf = pdfplumber.open(path)
    page = pdf.pages[0]
    words = page.extract_words(keep_blank_chars=False, x_tolerance=0.8, y_tolerance=1)

    area_word = min((w for w in words if w["text"] == "AREA"), key=lambda w: w["top"])
    heads = [w for w in words if w["text"] in ROMANS and abs(w["top"] - area_word["top"]) < 4]
    centers = sorted((cx(w), ROMANS.index(w["text"]) + 1) for w in heads)
    col_w = (centers[-1][0] - centers[0][0]) / (len(centers) - 1)
    first_left = centers[0][0] - col_w / 2
    header_bottom = area_word["bottom"] + 2

    thin = [r for r in page.rects if r["height"] < 1.6 or r["width"] < 1.6]

    code_words = [w for w in words if CODE.match(w["text"]) and cx(w) >= first_left]
    gaps = [abs(a["top"] - b["top"]) for a in code_words for b in code_words
            if abs(cx(a) - cx(b)) < col_w / 3 and abs(a["top"] - b["top"]) > 8]
    # the most common row pitch: a single tight pair (a legend, a squeezed cell) must not shrink every box
    from collections import Counter
    PITCH = Counter(round(g) for g in gaps if g < 60).most_common(1)[0][0]

    # ---- courses -------------------------------------------------------------------------
    courses = []
    for w in words:
        if not CODE.match(w["text"]) or cx(w) < first_left:
            continue
        line = [v for v in words if abs(v["top"] - w["top"]) < 2.5 and v is not w]
        left = [v for v in line if v["x1"] <= w["x0"] + 0.5 and w["x0"] - v["x1"] < 30 and v["text"].isdigit()]
        right = [v for v in line if v["x0"] >= w["x1"] - 0.5 and v["x0"] - w["x1"] < 30 and v["text"].isdigit()]
        if not left or not right:
            print("  !! code without neighbours", w["text"], round(w["x0"]), round(w["top"]), file=sys.stderr)
            continue
        lw = max(left, key=lambda v: v["x1"])
        rw = min(right, key=lambda v: v["x0"])
        c = cx(w)
        below = [v for v in words if 3 < v["top"] - w["top"] < 16 and lw["x0"] - 8 <= cx(v) <= rw["x1"] + 8]
        shape_b = any(v["text"] == "H" for v in below) and any(v["text"] == "C" for v in below)
        if shape_b:
            hours, credits, indep = int(lw["text"]), int(rw["text"]), None
        else:
            nums = [v for v in below if v["text"].isdigit()]
            credits = int(min(nums, key=lambda v: abs(cx(v) - c))["text"]) if nums else None
            hours, indep = int(lw["text"]), int(rw["text"])

        # box: the horizontal rules around this cell, within one row pitch
        # shape B prints the code on the bottom line of its box, shape A in the middle
        above, below = (0.95 * PITCH, 0.35 * PITCH) if shape_b else (0.6 * PITCH, 0.4 * PITCH)
        rules = [r for r in thin if r["height"] < 1.6 and 30 < r["width"] < 90
                 and r["x0"] - 3 <= c <= r["x1"] + 3 and -above < r["top"] - w["top"] < below]
        if rules:
            bx0, bx1 = min(r["x0"] for r in rules), max(r["x1"] for r in rules)
            by0 = min(min(r["top"] for r in rules), w["top"] - above + 2)
            by1 = max(max(r["bottom"] for r in rules), w["top"] + below - 2)
        else:
            bx0, bx1, by0, by1 = lw["x0"] - 4, rw["x1"] + 4, w["top"] - above, w["top"] + below
        name_words = [v for v in words if max(by0 - 1, header_bottom) <= v["top"] < w["top"] - 1.5
                      and bx0 - 1 <= cx(v) <= bx1 + 1 and not v["text"].isdigit()
                      and not CODE.match(v["text"]) and v["text"] not in ("H", "C")]
        name_words += [v for v in words if v["text"].isdigit() and any(
            abs(v["top"] - n["top"]) < 1.5 and 0 <= v["x0"] - n["x1"] < 5 for n in name_words)]
        name_words.sort(key=lambda v: (round(v["top"] / 2), v["x0"]))
        name = re.sub(r"\s+", " ", " ".join(v["text"] for v in name_words)).strip()
        level = min(centers, key=lambda t: abs(t[0] - c))[1]
        courses.append(dict(code=w["text"], name=name, level=level, credits=credits, hours=hours,
                            independent=indep, codeTop=w["top"], box=[bx0, by0, bx1, by1]))

    # ---- areas ---------------------------------------------------------------------------
    label_words = [w for w in words if w["x1"] <= first_left + 2 and w["top"] > header_bottom
                   and not w["text"].isdigit() and w["text"] not in ROMANS]
    # drop the rotated one-letter-per-word column (Industrial "ÁREA DISCIPLINAR")
    xs = [round(w["x0"]) for w in label_words]
    label_words = [w for w in label_words if not (len(w["text"]) <= 2 and xs.count(round(w["x0"])) > 4)]
    lab_left = min(w["x0"] for w in label_words) - 4
    def group(ws):
        ws = sorted(ws, key=lambda v: (round(v["top"] / 3), v["x0"]))
        return " ".join(v["text"] for v in ws)

    verticals = [r for r in page.rects + page.lines
                 if r["width"] < 1.6 and r["height"] > 8 and lab_left - 40 <= r["x0"] <= lab_left + 5
                 and r["top"] >= header_bottom - 2]
    smallest = {}
    for w in label_words:
        hold = [r for r in verticals if r["top"] - 1 <= cy(w) <= r["bottom"] + 1]
        if hold:
            r = min(hold, key=lambda r: r["height"])
            smallest.setdefault((round(r["top"]), round(r["bottom"])), []).append(w)
    bands = [dict(top=k[0], bottom=k[1], name=group(v)) for k, v in sorted(smallest.items())]
    if len(bands) < 3:
        marks = []
        for r in page.rects + page.lines:
            if r["x1"] < lab_left - 8 or r["x0"] > first_left or r["top"] < header_bottom:
                continue
            if r["height"] < 1.6 and r["width"] > 15:
                marks.append(r["top"])
            elif r["width"] < 1.6 and r["height"] > 8:
                marks += [r["top"], r["bottom"]]
        edges = []
        for m in sorted(marks):
            if not edges or m - edges[-1] > 3:
                edges.append(m)
        bands = []
        for a, b in zip(edges, edges[1:]):
            inside = [w for w in label_words if a <= cy(w) <= b]
            if inside:
                bands.append(dict(top=a, bottom=b, name=group(inside)))
    for crs in courses:
        y = crs["codeTop"]
        hit = [b for b in bands if b["top"] - 1 <= y <= b["bottom"] + 1]
        crs["area"] = (hit[0] if hit else min(bands, key=lambda b: min(abs(y - b["top"]), abs(y - b["bottom"]))))["name"]
        crs["areaGuessed"] = not hit

    # ---- prerequisites -------------------------------------------------------------------
    def inside_box(o, pad=0.6):
        return any(b[0] - pad <= o["x0"] and o["x1"] <= b[2] + pad and b[1] - pad <= o["top"] and o["bottom"] <= b[3] + pad
                   for b in (c["box"] for c in courses))

    segs = []
    for r in thin:
        if r["top"] < header_bottom or r["x1"] < first_left or inside_box(r, pad=1.8):
            continue
        if r["height"] < 1.6:
            segs.append((r["x0"], cy(r), r["x1"], cy(r)))
        else:
            segs.append((cx(r), r["top"], cx(r), r["bottom"]))
    for l in page.lines:
        if l["top"] >= header_bottom and not inside_box(l):
            segs.append((l["x0"], l["top"], l["x1"], l["bottom"]))
    heads = []
    for c in page.curves:
        pts = []
        for p in c["pts"]:
            if not any(math.dist(p, q) < 0.3 for q in pts):
                pts.append(p)
        if c.get("fill") and len(pts) == 3 and c["x1"] - c["x0"] < 7 and c["bottom"] - c["top"] < 7:
            heads.append(pts)
        elif c["top"] >= header_bottom and not inside_box(c):
            for a, b in zip(c["pts"], c["pts"][1:]):
                if math.dist(a, b) > 0.8:
                    segs.append((a[0], a[1], b[0], b[1]))

    def pdist(p, s):
        (x0, y0, x1, y1) = s
        dx, dy = x1 - x0, y1 - y0
        t = 0 if dx == dy == 0 else max(0, min(1, ((p[0] - x0) * dx + (p[1] - y0) * dy) / (dx * dx + dy * dy)))
        return math.dist(p, (x0 + t * dx, y0 + t * dy))

    def free(p):
        # an endpoint resting on a course box belongs to that box: connectors do not pass through it
        return not box_at(p)

    def touching(s, t, tol=1.2):
        return any(free(p) and pdist(p, t) <= tol for p in ((s[0], s[1]), (s[2], s[3]))) or \
               any(free(p) and pdist(p, s) <= tol for p in ((t[0], t[1]), (t[2], t[3])))

    def box_at(p, tol=1.8, exclude=None):
        found = []
        for crs in courses:
            b = crs["box"]
            if crs is exclude:
                continue
            if b[0] < p[0] < b[2] and b[1] < p[1] < b[3]:
                found.append(crs)
                continue
            on_v = (abs(p[0] - b[0]) <= tol or abs(p[0] - b[2]) <= tol) and b[1] - tol <= p[1] <= b[3] + tol
            on_h = (abs(p[1] - b[1]) <= tol or abs(p[1] - b[3]) <= tol) and b[0] - tol <= p[0] <= b[2] + tol
            if on_v or on_h:
                found.append(crs)
        return found

    arrows, seen = [], set()
    for pts in heads:
        tip = None
        for i, p in enumerate(pts):
            o = [q for j, q in enumerate(pts) if j != i]
            if abs(o[0][0] - o[1][0]) < 0.6 and abs(p[0] - o[0][0]) > 1:
                tip, base, d = p, ((o[0][0] + o[1][0]) / 2, (o[0][1] + o[1][1]) / 2), "h"
            elif abs(o[0][1] - o[1][1]) < 0.6 and abs(p[1] - o[0][1]) > 1:
                tip, base, d = p, ((o[0][0] + o[1][0]) / 2, (o[0][1] + o[1][1]) / 2), "v"
        if tip is None:
            continue
        key = (round(tip[0]), round(tip[1]))
        if key in seen:
            continue
        seen.add(key)
        # target: nearest box edge in front of the tip
        cand = []
        for crs in courses:
            b = crs["box"]
            if d == "h":
                gap = (b[0] - tip[0]) if tip[0] > base[0] else (tip[0] - b[2])
                if -4.5 <= gap <= 13 and b[1] - 2 <= tip[1] <= b[3] + 2:
                    cand.append((gap, crs))
            else:
                gap = (b[1] - tip[1]) if tip[1] > base[1] else (tip[1] - b[3])
                if -4.5 <= gap <= 13 and b[0] - 2 <= tip[0] <= b[2] + 2:
                    cand.append((gap, crs))
        target = min(cand, key=lambda t: t[0])[1] if cand else None
        # walk the connectors from the base
        start = [s for s in segs if pdist(base, s) <= 2.5]
        reached, frontier = list(start), list(start)
        while frontier:
            s = frontier.pop()
            for t in segs:
                if t not in reached and touching(s, t):
                    reached.append(t)
                    frontier.append(t)
        sources = []
        for s in reached:
            for p in ((s[0], s[1]), (s[2], s[3])):
                for crs in box_at(p, exclude=target):
                    if crs not in sources:
                        sources.append(crs)
        if not reached:
            sources = box_at(base, tol=4, exclude=target)
        arrows.append(dict(tip=[round(tip[0]), round(tip[1])], target=target and target["code"],
                           sources=[s["code"] for s in sources], segments=len(reached)))

    for crs in courses:
        crs["prerequisites"] = sorted({s for a in arrows if a["target"] == crs["code"] for s in a["sources"]})
    return dict(courses=courses, bands=bands, arrows=arrows)


if __name__ == "__main__":
    out = parse(sys.argv[1])
    cs = out["courses"]
    print("bands:", [(round(b["top"]), round(b["bottom"]), b["name"]) for b in out["bands"]])
    by = {c["code"]: c for c in cs}
    print(f"{len(cs)} courses, credits {sum(c['credits'] or 0 for c in cs)}, hours {sum(c['hours'] for c in cs)}")
    for c in sorted(cs, key=lambda c: (c["level"], c["codeTop"])):
        rule = "" if c["independent"] is None or (c["credits"] or 0) * 3 == c["hours"] + c["independent"] else "  <-- credit rule"
        print(f"  L{c['level']} {c['code']:6} cr{c['credits']!s:>3} h{c['hours']:>3} i{c['independent']!s:>4} "
              f"{c['area'][:22]:22}{'?' if c['areaGuessed'] else ' '} {c['name'][:44]:44} <- {','.join(c['prerequisites'])}{rule}")
    print("arrows without target or source:")
    for a in out["arrows"]:
        if not a["target"] or not a["sources"]:
            print("  ", a)
    if len(sys.argv) > 2:
        json.dump(out, open(sys.argv[2], "w"), ensure_ascii=False, indent=1, default=str)
