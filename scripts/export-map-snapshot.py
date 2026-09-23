#!/usr/bin/env python3
"""
Writes the campus map, as the API serves it, into the snapshot a fresh database starts from.

    scripts/export-map-snapshot.py
    KAPP_GATEWAY=http://localhost:8090 scripts/export-map-snapshot.py
    scripts/export-map-snapshot.py --out /tmp/snapshot      # somewhere else, to compare

One file per building under
app/backend/microservices/map-service/src/main/resources/db/seed/map/, in the shape
V005_SurveyedCampus reads. Commit them: the M0 cluster keeps no backups, so what is drawn in the
floor editor exists only in Atlas until it is exported and committed.

Why the API and not mongoexport: no database credential leaves the services, which is the rule
for every database here, and the files keep the contract's shape rather than the storage's. Any
token works - the map is readable by every role - and the one on the portal's "My token" screen
is the easy one to get. It is read from KAPP_TOKEN or asked for without echoing; it is never
printed or written anywhere.

Standard library only, so it runs on any machine with python3.
"""
import argparse
import getpass
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OUT = os.path.join(REPO, "app", "backend", "microservices", "map-service", "src", "main",
                           "resources", "db", "seed", "map")


def fetch(gateway, token, path):
    request = urllib.request.Request(gateway.rstrip("/") + path,
                                     headers={"Authorization": f"Bearer {token}", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as e:
        hint = {401: "the token is missing or expired", 403: "the token may not read the map",
                429: "rate limited - wait a minute"}.get(e.code, "")
        sys.exit(f"GET {path} failed: HTTP {e.code}{' - ' + hint if hint else ''}")
    except urllib.error.URLError as e:
        sys.exit(f"Cannot reach the gateway at {gateway}: {e.reason}")


def present(value):
    """Absent, empty and blank are all "not set": they are left out so the files stay short."""
    return value is not None and value != [] and value != ""


def pick(source, keys, always=()):
    return {k: source[k] for k in keys if k in source and (k in always or present(source[k]))}


def number(value):
    return int(value) if float(value).is_integer() else value


def building_file(building, floors):
    out = pick(building, ["code", "name", "campus", "description", "aliases"], always=["aliases"])
    out["wings"] = [pick(w, ["code", "name", "doorSuffix", "note"]) for w in building.get("wings", [])]
    out["floors"] = []
    for floor in sorted(floors, key=lambda f: f["level"]):
        f = pick(floor, ["code"])
        f["level"] = number(floor["level"])
        f.update(pick(floor, ["name", "status", "accessibility", "note", "gridRows", "gridColumns"]))
        if present(floor.get("corridors")):
            f["corridors"] = [pick(c, ["code", "name", "color", "path"]) for c in floor["corridors"]]
        f["spaces"] = [
            pick(s, ["code", "doorCode", "wing", "name", "typeCode", "aliases", "gridRow", "gridColumn",
                     "rowSpan", "colSpan", "accessVia", "accessibility", "note", "capacity"],
                 always=["rowSpan", "colSpan"])
            for s in sorted(floor.get("spaces", []), key=lambda s: s["code"])
        ]
        out["floors"].append(f)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[1].strip())
    parser.add_argument("--out", default=DEFAULT_OUT, help="directory to write into (default: the seed)")
    args = parser.parse_args()

    gateway = os.environ.get("KAPP_GATEWAY", "http://localhost:8080")
    token = os.environ.get("KAPP_TOKEN") or getpass.getpass("Token (from the portal's My token screen): ")
    if not token.strip():
        sys.exit("No token given.")
    token = token.strip()

    buildings = fetch(gateway, token, "/api/map/buildings")
    os.makedirs(args.out, exist_ok=True)
    written = set()
    for building in sorted(buildings, key=lambda b: b["code"]):
        code = building["code"]
        floors = [fetch(gateway, token, "/api/map/buildings/{}/floors/{}".format(
                          urllib.parse.quote(code, safe=""), urllib.parse.quote(f["code"], safe="")))
                  for f in building.get("floors", [])]
        name = code.lower() + ".json"
        with open(os.path.join(args.out, name), "w", encoding="utf-8") as out:
            json.dump(building_file(building, floors), out, ensure_ascii=False, indent=2)
            out.write("\n")
        written.add(name)
        placed = sum(1 for f in floors for s in f.get("spaces", []) if s.get("gridRow") is not None)
        total = sum(len(f.get("spaces", [])) for f in floors)
        print(f"  {code:6} {len(floors)} floor(s), {total} space(s), {placed} placed")

    # A building deleted in the portal must not come back in the next fresh database.
    for stale in sorted(set(n for n in os.listdir(args.out) if n.endswith(".json")) - written):
        os.remove(os.path.join(args.out, stale))
        print(f"  removed {stale}: the building no longer exists")

    where = os.path.relpath(args.out, REPO) if os.path.abspath(args.out).startswith(REPO + os.sep) else args.out
    print(f"\n{len(written)} building(s) written to {where}")
    print("Review the diff and commit it: that is the map's backup.")


if __name__ == "__main__":
    main()
