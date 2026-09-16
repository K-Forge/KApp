"""Turns docs/pensums/transcriptions/*.yaml into the semaphore-service seed.

    python3 -m pip install pyyaml
    python3 docs/pensums/tools/build_seed.py

Writes one JSON document per pensum, in the shape of the Pensum domain record, plus
programs.json, under semaphore-service/src/main/resources/db/seed/pensums/. The seed is
generated; edit the transcriptions or catalog.yaml, never the JSON.

What it decides, so the transcriptions do not have to:
  * weeklyHours is an integer in the model; a printed 4,5 or 1,5 rounds half up.
  * A missing credit or hour value is stored as 0. The pensum keeps the total the document
    prints, so the gap stays visible instead of being papered over.
  * A course is an elective slot when its name says so. A slot has no course code, so
    prerequisites between two slots cannot be stored and are reported.
  * Printed typos in course names are corrected from TYPOS below, and nowhere else.
"""
import json, math, re, sys, unicodedata
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[3]
DOCS = ROOT / "docs" / "pensums"
OUT = ROOT / "app/backend/microservices/semaphore-service/src/main/resources/db/seed/pensums"

# The Konrad palette (docs/K-COLORS.md). Sociedad e Interculturalidad is pink in every plan;
# the other areas take the remaining colours in the order the document lists them.
PINK = "#D51A65"
PALETTE = ["#539392", "#522567", "#C9D329", "#3E823E", "#B62325", "#592E2A"]

TYPOS = {
    "Internedio": "Intermedio", "Intermerdio": "Intermedio", "Sofware": "Software",
    "Aplicac Móviles": "Aplicaciones Móviles", "Matemàticas": "Matemáticas",
    "Metodologíca": "Metodológica", "Política Publica": "Política Pública",
    "Ingles ": "Inglés ", "Analitica de Datos": "Analítica de Datos",
    "Fundamento s ": "Fundamentos ",
}
ELECTIVE = re.compile(r"^(Electiva|Énfasis|Seminario Electivo)\b|: Electiva$|\(electiva\)$", re.IGNORECASE)
STOP = {"de", "e", "y", "la", "las", "del", "en", "a", "el", "los"}
MONTH = "septiembre de 2026"


def fold(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def area_code(name):
    words = [w for w in re.split(r"\s+", fold(name)) if w and w.lower() not in STOP and w.upper() != "AREA"]
    code = re.sub(r"[^A-Z]", "", words[0].upper()) if len(words) == 1 else "".join(w[0] for w in words).upper()
    return code[:20]


def fix(name):
    name = re.sub(r"\s+", " ", str(name)).strip()
    for wrong, right in TYPOS.items():
        name = name.replace(wrong, right)
    return name


def hours(value):
    return 0 if value is None else int(math.floor(float(value) + 0.5))


def build(entry, problems):
    src = yaml.safe_load((DOCS / "transcriptions" / entry["file"]).read_text())
    columns = src.get("columns")
    rows = []
    for level, items in src["levels"].items():
        for i, item in enumerate(items, start=1):
            cols = columns or (["name", "credits", "area"] if len(item) == 3 else ["name", "credits"])
            if len(item) != len(cols):
                problems.append(f"{entry['file']}: level {level} item {item} does not match columns {cols}")
                continue
            row = dict(zip(cols, item))
            row["level"] = int(level)
            row["index"] = i
            rows.append(row)

    # areas: declared (code -> name), inferred from names, or one area for a plan that prints none
    declared = src.get("areas") or {}
    by_name = {}
    if declared and all(isinstance(k, str) and k.isupper() and len(k) <= 20 for k in declared) and not any(
            r.get("area") in declared.values() for r in rows):
        by_name = {code: name for code, name in declared.items()}
        lookup = lambda r: r["area"]
    elif any("area" in r for r in rows):
        names = []
        for r in rows:
            label = declared.get(r["area"], r["area"]) if declared else r["area"]
            if label not in names:
                names.append(label)
        by_name = {area_code(n): n for n in names}
        rev = {n: area_code(n) for n in names}
        lookup = lambda r: rev[declared.get(r["area"], r["area"]) if declared else r["area"]]
    else:
        by_name = {"PLAN": "Plan de estudios"}
        lookup = lambda r: "PLAN"

    prefix = entry.get("prefix")
    courses, code_of_name = [], {}
    for r in rows:
        name = fix(r["name"])
        slot = bool(ELECTIVE.search(name))
        printed = str(r["code"]) if "code" in r else None
        generated = f"{prefix}-{r['level']}{r['index']:02d}" if prefix else None
        item_code = printed or generated
        if printed is None and generated is None:
            problems.append(f"{entry['file']}: {name} has neither a printed code nor a prefix to generate one")
            continue
        if isinstance(r.get("hours"), float) and not float(r["hours"]).is_integer():
            problems.append(f"note {entry['pensumCode']}: {name} prints {r['hours']} weekly hours, stored as {hours(r['hours'])}")
        courses.append({
            "code": None if slot else item_code,
            "pensumItemCode": item_code,
            "name": name,
            "level": r["level"],
            "credits": int(r["credits"]) if r.get("credits") is not None else 0,
            "weeklyHours": hours(r.get("hours")),
            "area": lookup(r),
            "isElectiveSlot": slot,
            "prerequisites": [],
            "sinuCode": printed,
        })
        code_of_name[str(r["name"])] = item_code
        code_of_name[name] = item_code

    fixed = {c["code"] for c in courses if c["code"]}
    items = {c["pensumItemCode"]: c for c in courses}
    for target, sources in (src.get("prerequisites") or {}).items():
        t = items.get(str(target)) or items.get(code_of_name.get(str(target), ""))
        if not t:
            problems.append(f"{entry['file']}: prerequisite target {target} is not a course")
            continue
        for s in sources:
            code = str(s) if str(s) in items else code_of_name.get(str(s))
            if code is None:
                problems.append(f"{entry['file']}: prerequisite {s} of {target} is not a course")
            elif code not in fixed:
                problems.append(f"note {entry['pensumCode']}: {items[code]['name']} -> {t['name']} dropped, the source is an elective slot")
            else:
                t["prerequisites"].append(code)

    colours, palette = {}, iter(PALETTE * 2)
    for code, name in by_name.items():
        colours[code] = PINK if fold(name).lower().startswith(("sociedad", "area sociedad")) else next(palette)
    areas = []
    for code, name in by_name.items():
        members = [c for c in courses if c["area"] == code]
        label = fix(name).replace("INTERCULTURARIDAD", "INTERCULTURALIDAD")
        label = label if not label.isupper() else " ".join(
            w.lower() if w.lower() in STOP else w.capitalize() for w in label.lower().split())
        areas.append({"code": code, "name": label, "color": colours[code],
                      "credits": sum(c["credits"] for c in members), "hours": sum(c["weeklyHours"] for c in members)})

    credits = sum(c["credits"] for c in courses)
    weekly = sum(c["weeklyHours"] for c in courses)
    declared_credits = src.get("declaredCredits")
    declared_hours = src.get("declaredHours")
    for label, printed, computed in (("credits", declared_credits, credits), ("hours", declared_hours, weekly)):
        if printed is not None and printed != computed:
            problems.append(f"note {entry['pensumCode']}: the document declares {printed} {label}, its courses add up to {computed}")

    period = " · cuatrimestres" if src.get("period") == "cuatrimestre" else ""
    reform = src.get("reform") or f"Plan publicado, consultado en {MONTH}{period}"
    pensum = {
        "pensumCode": entry["pensumCode"], "programCode": entry["programCode"],
        "programName": src["program"], "faculty": src["faculty"], "reform": reform,
        "status": entry["status"],
        "totalCredits": declared_credits if declared_credits is not None else credits,
        "totalHours": declared_hours if declared_hours is not None else weekly,
        "levels": max(c["level"] for c in courses), "areas": areas, "courses": courses,
    }
    program = {"code": entry["programCode"], "name": src["program"], "faculty": src["faculty"], "level": src["programLevel"]}

    for field, limit in (("pensumCode", 20), ("programCode", 20), ("programName", 100), ("reform", 100)):
        if len(pensum[field]) > limit:
            problems.append(f"{entry['file']}: {field} is longer than {limit}")
    for c in courses:
        if len(c["name"]) > 120 or len(c["pensumItemCode"]) > 30:
            problems.append(f"{entry['file']}: {c['name']} exceeds a length limit")
    if len(items) != len(courses):
        problems.append(f"{entry['file']}: duplicated item codes")
    return program, pensum


def main():
    catalog = yaml.safe_load((DOCS / "catalog.yaml").read_text())
    problems, programs, pensums = [], [], []
    for entry in catalog:
        program, pensum = build(entry, problems)
        programs.append(program)
        pensums.append(pensum)
    errors = [p for p in problems if not p.startswith("note ")]
    for p in problems:
        print(("  " if p.startswith("note ") else "ERROR ") + p)
    if errors:
        sys.exit(1)
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()
    (OUT / "programs.json").write_text(json.dumps(programs, ensure_ascii=False, indent=2) + "\n")
    for p in pensums:
        (OUT / f"pensum-{p['pensumCode']}.json").write_text(json.dumps(p, ensure_ascii=False, indent=2) + "\n")
    print(f"{len(pensums)} pensums, {sum(len(p['courses']) for p in pensums)} items -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
