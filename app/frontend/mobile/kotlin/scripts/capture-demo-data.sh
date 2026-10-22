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
# prerequisite passed.
python3 -I - "$(cd "$(dirname "$0")/../../../../.." && pwd)/app/backend/microservices/semaphore-service/src/main/resources/db/seed/pensums/pensum-$pensum.json" \
  "$out/get/api/catalog/pensums/$pensum.json" "$out/get/api/semaphore/me.json" "$out/get/api/semaphore/me/eligible.json" <<'PY'
import json, sys
seed_path, pensum_path, semaphore_path, eligible_path = sys.argv[1:]
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

for path, data in ((pensum_path, pensum), (semaphore_path, semaphore), (eligible_path, eligible)):
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

# Every room of the day and the week, so tapping a class finds its room offline. Those two rather
# than the full timetable because each class there carries its building and room together.
python3 -I - "$out/get/api/schedule/me/day.json" "$out/get/api/schedule/me/week.json" <<'PY' | while read -r building room; do
import json, sys
rooms = set()
def walk(node):
    if isinstance(node, dict):
        if node.get("buildingCode") and node.get("room"):
            rooms.add((node["buildingCode"], node["room"]))
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for value in node:
            walk(value)
for path in sys.argv[1:]:
    walk(json.load(open(path)))
for building, room in sorted(rooms):
    print(building, room)
PY
  save GET "api/map/spaces/$room?buildingCode=$building"
  mv "$out/get/api/map/spaces/$room.json" "$out/get/api/map/spaces/$building-$room.json"
done
