#!/usr/bin/env bash
# Copies the contracts' examples into the demo variant's assets, by asking the Prism mocks for
# every call the app makes. Run it after a docs/api/*.openapi.yaml example changes, with the mocks
# up (README, "Running against the mocks"):
#
#   app/frontend/mobile/kotlin/scripts/capture-demo-data.sh
#
# A file lands at <method>/<path>.json - get/api/semaphore/me.json - which is where
# DemoBackend looks for it. /api/users/me gets one file per profile: me.student.json and so on.
set -euo pipefail

base="${KAPP_MOCKS:-http://localhost:4000}"
out="$(cd "$(dirname "$0")/.." && pwd)/app/src/demo/assets/demo"
auth='Authorization: Bearer demo-access-token-not-a-real-one'

save() { # <method> <path> [extra curl args...]
  local method="$1" path="$2"; shift 2
  local file="$out/$(echo "$method" | tr '[:upper:]' '[:lower:]')/${path%%\?*}.json"
  mkdir -p "$(dirname "$file")"
  curl -sf -X "$method" -H "$auth" -H 'Content-Type: application/json' "$@" "$base/$path" \
    | python3 -I -m json.tool --no-ensure-ascii > "$file"
  echo "$file"
}

rm -rf "$out"

save POST auth/microsoft -d '{"idToken":"demo-id-token-0123456789","client":"APP"}'
save POST auth/refresh -d '{"refreshToken":"demo-refresh-token"}'
# The contract's example pass is the one the demo accepts; any other code has no file, and the
# demo answers 404 - "Ese código no existe" on Invitación.
save POST auth/visitor-passes/KL-V-7K2M-QX9P/redeem \
  -d '{"documentType":"CC","documentNumber":"1032456789","visitorName":"Pepita Perez"}'

for example in student professor staffAdmin; do
  save GET api/users/me -H "Prefer: example=$example"
  mv "$out/get/api/users/me.json" "$out/get/api/users/me.$example.json"
done

save GET api/semaphore/me
save GET api/semaphore/me/summary
save GET api/semaphore/me/eligible
save GET api/semaphore/me/plans
pensum=$(python3 -I -c 'import json,sys; print(json.load(sys.stdin)["pensumCode"])' < "$out/get/api/semaphore/me.json")
save GET "api/catalog/pensums/$pensum"
save GET "api/catalog/pensums/$pensum/electives"

# Semáforo's pensum. The contract's example has ten of its courses, all passed or in progress, so a
# demo plan had nothing to move (issue #176). The demo's pensum is the whole published one, out of
# semaphore-service's seed, and the student's semáforo is made to agree with it: the example's own
# entries stay as they are - passed, failed, postponed, in progress - a course of levels 1 to 7 it
# does not mention is passed, since the student is in level 8, and levels 8 and 9 are what is left.
# "Puedes tomarla" is worked out again: whatever is not passed or in progress and has every
# prerequisite passed. And the summary Inicio and Semáforo show is summed from that semáforo, so
# both screens give the same credits.
python3 -I - "$(cd "$(dirname "$0")/../../../../.." && pwd)/app/backend/microservices/semaphore-service/src/main/resources/db/seed/pensums/pensum-$pensum.json" \
  "$out/get/api/catalog/pensums/$pensum.json" "$out/get/api/semaphore/me.json" "$out/get/api/semaphore/me/eligible.json" \
  "$out/get/api/semaphore/me/summary.json" <<'PY'
import json, sys
seed_path, pensum_path, semaphore_path, eligible_path, summary_path = sys.argv[1:]
seed = json.load(open(seed_path, encoding="utf-8"))
fields = ("pensumItemCode", "sinuCode", "name", "level", "credits", "weeklyHours", "totalHours",
          "area", "isElectiveSlot", "prerequisites")
courses = []
for c in seed["courses"]:
    c = dict(c, totalHours=round(c["weeklyHours"] * 16))  # 16 weeks, as the contract counts hours
    courses.append({k: c[k] for k in fields if k in c})
pensum = {k: seed[k] for k in seed if k != "courses"}
pensum["courses"] = courses

semaphore = json.load(open(semaphore_path, encoding="utf-8"))
current = semaphore["currentLevel"]
known = {e["pensumItemCode"] for e in semaphore["courses"]}
# The period each level was passed in, counting back from the example's level 7 in 2026-1.
periods = {1: "20191", 2: "20192", 3: "20201", 4: "20202", 5: "20211", 6: "20252", 7: "20261"}
for c in courses:
    if c["pensumItemCode"] in known or c["level"] >= current:
        continue
    semaphore["courses"].append({
        "pensumItemCode": c["pensumItemCode"],
        "status": "PASSED",
        "sinuStatus": "APROBADA",
        "period": periods.get(c["level"], "20261"),
        "grade": 35 + sum(map(ord, c["pensumItemCode"])) % 14,  # 3,5 to 4,8, the same every run
        "resolvedSinuCode": None,
        "resolvedName": None,
    })
status = {e["pensumItemCode"]: e["status"] for e in semaphore["courses"]}
eligible = [c for c in courses
            if status.get(c["pensumItemCode"], "PENDING") not in ("PASSED", "IN_PROGRESS")
            and all(status.get(p) == "PASSED" for p in c["prerequisites"])]

def credits(courses, *statuses):
    return sum(c["credits"] for c in courses if status.get(c["pensumItemCode"], "PENDING") in statuses)

summary = json.load(open(summary_path, encoding="utf-8"))
total = sum(c["credits"] for c in courses)
summary["creditsPassed"] = credits(courses, "PASSED")
summary["creditsInProgress"] = credits(courses, "IN_PROGRESS")
summary["creditsRemaining"] = total - summary["creditsPassed"] - summary["creditsInProgress"]
summary["totalCredits"] = total
summary["percentComplete"] = round(100 * summary["creditsPassed"] / total, 1)
summary["byArea"] = [
    {"area": area["code"],
     "creditsPassed": credits([c for c in courses if c["area"] == area["code"]], "PASSED"),
     "creditsTotal": sum(c["credits"] for c in courses if c["area"] == area["code"])}
    for area in pensum["areas"]
]
summary["currentLevel"] = current

for path, data in ((pensum_path, pensum), (semaphore_path, semaphore), (eligible_path, eligible),
                   (summary_path, summary)):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=4)
        f.write("\n")
PY

# Planning answers with the example plan whatever it is asked: the demo keeps no state. The ids in
# a path are not part of the file name, so any plan and any course find these two. "any" rather
# than "_" because the Android build leaves out asset folders whose name starts with one.
save POST api/semaphore/me/plans -d "{\"name\":\"Mi plan\",\"pensumCode\":\"$pensum\"}"
plan=$(python3 -I -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])' < "$out/get/api/semaphore/me/plans.json")
save PUT "api/semaphore/me/plans/$plan/placements/17070" -d '{"plannedLevel":9}'
mkdir -p "$out/put/api/semaphore/me/plans/any/placements"
mv "$out/put/api/semaphore/me/plans/$plan/placements/17070.json" "$out/put/api/semaphore/me/plans/any/placements/any.json"
rm -r "$out/put/api/semaphore/me/plans/$plan"

save GET api/schedule/me
# The examples are one day and one week, whichever date is asked for; DemoBackend serves them
# for every date.
save GET "api/schedule/me/day?date=2026-10-23"
save GET "api/schedule/me/week?date=2026-10-19"

# Mapa's list. The contract's example has a single building, so this one is the campus as
# map-service serves it, out of its seeds (db/seed/map, one file per building), with the fields a
# building has in the API and without the drawing, which the list does not use. Ordered by code,
# as the API answers.
mkdir -p "$out/get/api/map"
python3 -I - "$(cd "$(dirname "$0")/../../../../.." && pwd)/app/backend/microservices/map-service/src/main/resources/db/seed/map" \
  > "$out/get/api/map/buildings.json" <<'PY'
import json, pathlib, sys
fields = ("code", "name", "campus", "description", "address", "aliases")
buildings = []
for seed in sorted(pathlib.Path(sys.argv[1]).glob("*.json")):
    b = json.loads(seed.read_text(encoding="utf-8"))
    building = {k: b[k] for k in fields if k in b}
    building["wings"] = [{k: w[k] for k in ("code", "name", "note") if k in w} for w in b.get("wings", [])]
    building["floors"] = [{k: f[k] for k in ("code", "level", "name") if k in f} for f in b.get("floors", [])]
    buildings.append(building)
buildings.sort(key=lambda b: b["code"])
print(json.dumps(buildings, ensure_ascii=False, indent=4))
PY
echo "$out/get/api/map/buildings.json"

# Every floor, as GET /api/map/buildings/{code}/floors/{floorCode} answers it (FloorDetail), and
# every room the day and the week name, as GET /api/map/spaces/{room}?buildingCode= answers it
# (SpaceDetail), out of the same seeds. The contract has one example floor and one example room,
# so the mocks answered every room with that one: Horario opened 508 and showed 302. A room the
# seeds do not have gets no file, and the demo answers 404, as map-service does.
# A space's typeName and category come from the type catalogue map-service seeds itself
# (V004_MapModelV2.CATALOGUE), and its bounds and labelPoint from its shape, as the service works
# them out (map 3.7.0; label_point is map-service's LabelPoint, step by step).
python3 -I - "$(cd "$(dirname "$0")/../../../../.." && pwd)/app/backend/microservices/map-service/src/main" "$out/get/api/map" \
  "$out/get/api/schedule/me/day.json" "$out/get/api/schedule/me/week.json" <<'PY'
import heapq, json, math, pathlib, re, sys

def signed_distance(x, y, shape):
    """How far (x, y) is from the shape's walls: positive inside, negative outside."""
    inside, nearest = False, math.inf
    for i in range(len(shape)):
        a, b = shape[i], shape[i - 1]
        if (a["y"] > y) != (b["y"] > y) and x < (b["x"] - a["x"]) * (y - a["y"]) / (b["y"] - a["y"]) + a["x"]:
            inside = not inside
        px, py, dx, dy = a["x"], a["y"], b["x"] - a["x"], b["y"] - a["y"]
        if dx or dy:
            t = ((x - px) * dx + (y - py) * dy) / (dx * dx + dy * dy)
            if t > 1:
                px, py = b["x"], b["y"]
            elif t > 0:
                px, py = px + dx * t, py + dy * t
        nearest = min(nearest, (x - px) ** 2 + (y - py) ** 2)
    return math.sqrt(nearest) if inside else -math.sqrt(nearest)

def label_point(shape):
    """The point inside the shape farthest from its walls (polylabel, precision 1), in whole units."""
    def cell(x, y, half, order):
        d = signed_distance(x, y, shape)
        return (-(d + half * math.sqrt(2)), order, x, y, half, d)
    xs, ys = [p["x"] for p in shape], [p["y"] for p in shape]
    width, height = max(xs) - min(xs), max(ys) - min(ys)
    size = min(width, height)
    if size == 0:
        pole = (min(xs), min(ys))
    else:
        queue, order, h = [], 0, size / 2
        x = min(xs)
        while x < max(xs):
            y = min(ys)
            while y < max(ys):
                heapq.heappush(queue, cell(x + h, y + h, h, order)); order += 1
                y += size
            x += size
        area = cx = cy = 0
        for i in range(len(shape)):
            a, b = shape[i], shape[i - 1]
            f = a["x"] * b["y"] - b["x"] * a["y"]
            cx, cy, area = cx + (a["x"] + b["x"]) * f, cy + (a["y"] + b["y"]) * f, area + f * 3
        best = cell(cx / area, cy / area, 0, -1) if area else cell(shape[0]["x"], shape[0]["y"], 0, -1)
        box = cell(min(xs) + width / 2, min(ys) + height / 2, 0, -1)
        if box[5] > best[5]:
            best = box
        while queue:
            c = heapq.heappop(queue)
            if c[5] > best[5]:
                best = c
            if -c[0] - best[5] <= 1:
                continue
            half = c[4] / 2
            for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                heapq.heappush(queue, cell(c[2] + sx * half, c[3] + sy * half, half, order)); order += 1
        pole = (best[2], best[3])
    x, y = math.floor(pole[0]), math.floor(pole[1])
    candidates = [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)]
    best = max(candidates, key=lambda p: (signed_distance(p[0], p[1], shape), -candidates.index(p)))
    return {"x": best[0], "y": best[1]}

service, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
timetables = sys.argv[3:]
source = (service / "java/co/edu/konradlorenz/kapp/map/migration/V004_MapModelV2.java").read_text(encoding="utf-8")
# The entries read: a category, then code and name pairs until the next category.
catalogue, category, strings = {}, None, []
for category_token, string in re.findall(r'SpaceCategory\.([A-Z_]+)|"([^"]*)"', source.split("CATALOGUE = catalogue(")[1].split(");")[0]):
    if category_token:
        category = category_token
        continue
    strings.append(string)
    if len(strings) == 2:
        catalogue[strings[0]] = (strings[1], category)
        strings = []
rooms = {}
for seed in sorted((service / "resources/db/seed/map").glob("*.json")):
    b = json.loads(seed.read_text(encoding="utf-8"))
    summary = {k: b[k] for k in ("code", "name", "campus", "description", "aliases", "wings", "sinuSedes") if k in b}
    summary["id"] = f"demo-{b['code']}"
    for f in b["floors"]:
        spaces = []
        for s in sorted(f.get("spaces", []), key=lambda s: s["code"]):
            type_name, type_category = catalogue.get(s["typeCode"], (s["typeCode"], "OTHER"))
            space = dict(s, id=f"demo-{b['code']}-{s['code']}", typeName=type_name, category=type_category,
                         buildingId=f"demo-{b['code']}", buildingCode=b["code"], campus=b["campus"],
                         floorCode=f["code"], floorLevel=f["level"], doors=s.get("doors", []),
                         effectiveAccessibility=s.get("accessibility", f.get("accessibility", "UNKNOWN")))
            if s.get("shape"):
                xs, ys = [p["x"] for p in s["shape"]], [p["y"] for p in s["shape"]]
                space["bounds"] = {"x": min(xs), "y": min(ys), "width": max(xs) - min(xs), "height": max(ys) - min(ys)}
                if len(s["shape"]) >= 3:
                    space["labelPoint"] = label_point(s["shape"])
            spaces.append(space)
        floor = {k: f[k] for k in ("code", "level", "name", "status", "accessibility", "note", "width", "height", "top") if k in f}
        floor.update(outline=f.get("outline", []), corridors=f.get("corridors", []), version=0,
                     buildingId=f"demo-{b['code']}", buildingCode=b["code"], buildingName=b["name"], campus=b["campus"],
                     wings=b.get("wings", []), spaces=spaces)
        for space in spaces:
            rooms[(b["code"], space["code"])] = dict(space, building=summary, floor={
                k: floor[k] for k in ("code", "level", "name", "status", "accessibility", "note", "width", "height",
                                      "top", "outline", "corridors", "version") if k in floor})
        path = out / "buildings" / b["code"] / "floors" / f"{f['code']}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        # One line per file: a floor is thousands of points that nobody reads in a diff.
        path.write_text(json.dumps(floor, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        print(path)

asked = set()
def walk(node):
    if isinstance(node, dict):
        if node.get("buildingCode") and node.get("room"):
            asked.add((node["buildingCode"], node["room"]))
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for value in node:
            walk(value)
for timetable in timetables:
    walk(json.load(open(timetable, encoding="utf-8")))
for building, room in sorted(asked):
    if (building, room) not in rooms:
        print(f"no {building} {room} in the map seeds: the demo answers 404", file=sys.stderr)
        continue
    path = out / "spaces" / f"{building}-{room}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(rooms[(building, room)], ensure_ascii=False, indent=4) + "\n", encoding="utf-8")
    print(path)
PY

