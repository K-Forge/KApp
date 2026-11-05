# Backend State

> Owner: Backend. As of 2026-11-05, `develop` at `3966192`. Built from Backend's report of 2026-10-29, the merges of
> 2026-11-04, and the work on #183, #193 and #155 since.

---

## Built and working in `develop`

| Service | Contract | What it does | Latest migration |
|---|---|---|---|
| auth-service | auth 1.1.0 | Microsoft sign-in through `POST /auth/microsoft`, checked by `MicrosoftIdTokenVerifier` (RS256, iss, aud, exp; #102, #104). The PORTAL client requires a permission. Rotating refresh tokens with family revocation on reuse (#82). `GET /auth/sign-in-options` (#141). Password sign-in only behind `KAPP_LOCAL_SIGN_IN`, and 404 when it is off (#83). One profile role (STUDENT, PROFESSOR or STAFF; STAFF when the directory says nothing) plus permissions (ADMIN, RECEPTION, MAINTENANCE, MODERATION, WELLBEING). `KAPP_FIRST_ADMINS` grants the first ADMIN. `PUT /auth/admin/accounts/{id}/roles`, synced to user-service (#103). Admin endpoints for accounts, temporary passwords (#116), invitation codes and visitor passes | V005 |
| user-service | user 1.1.0 | Profiles without identity document or phone. `academic` is read-only from the test SINU (#76–#80). `PUT /internal/users/{id}/roles` | V003 |
| semaphore-service | semaphore 2.0.1 | SINU course statuses, read-only through `SinuRecordPort` and `FakeSinuRecordAdapter` (#79). Editable plans at `/me/plans` (`plannedLevel` 1 to 12). The catalogue of the 23 pensums | V010 |
| schedule-service | schedule 2.0.1 | Current classes only: `/me`, `/me/day`, `/me/week`, `/me/periods`, from the test SINU (`SinuTimetablePort`, #78). No database | none |
| map-service | map 3.6.0 | Buildings, floors, layouts, spaces, types, campuses, ground, structures and survey. ETag. `sinuSedes` (#77) | V015 (V016 is in #183; #193 squares its rooms) |
| api-gateway | none | Routes by name, with no registry (#64). Answers 500 when a service is down (#157) | none |

- **Local helpers:** fake OIDC (mock-oauth2-server on `:8090`, with test users for student, professor and staff;
  #102) and the Prism mocks on one port, `:4000` (#40).
- **Admin portal (`web-admin`):**
  - Microsoft PKCE sign-in plus `/auth/callback` (#141);
  - renewal across tabs, and sign-out (#142);
  - entry by any permission, a menu by permission, and `sectionGuard` (#169);
  - the permissions editor in Users (#172);
  - a CSP and security headers, guarded by `check-security-headers` in `pnpm test` (#179);
  - bilingual, guarded by `check-i18n`.
- **Tests:** about 900 in the full backend reactor, 143 in map-service, and 234 in the portal. All green on the last
  full run.
- **Dependencies on 2026-11-04:**
  - springdoc 2.10.0 (#150);
  - Angular 22.2.4/22.2.5 and other npm patches (#152);
  - vitest 5 is pending in #153.

---

## Infrastructure

- **Compose:**
  - project `kapp-<KAPP_STACK>` (#146);
  - profiles `mock`, `core`, `academic`, `map`, `full` and `dev`;
  - every service reaches the shared Atlas development cluster;
  - fake OIDC by default.
- **Dev server (the-hive):**
  - follows `develop` through GHCR images (`dev-images` publishes 7 services, schedule included; #156) and the agent
    in `deploy/dev-server/`;
  - runs the gateway, auth, user, semaphore, schedule, map and the portal;
  - Microsoft-only sign-in against the test tenant (#62, #148), with `KAPP_LOCAL_SIGN_IN=false`;
  - four real sign-ins passed on 2026-10-20, and `verify-hive` reports 0 failures.
- **Map data:** edited in the portal, and backed up into the seeds with `export-map-snapshot.py`.

---

## In progress

### #183: EC floor 1 from Brian's sketch, plus V016 (`feature/central-building-floors-1-and-2`)

- **What it does:**
  - traces the 26 spaces of Brian's floor 1 sketch under their existing codes, with Brian's answers applied: one
    cafeteria plus a north zone, "Ascensor central", the open patio outside the surveyed footprint, the "?" rooms as
    "Sin identificar", and Parking as the ramp;
  - registers the sketch onto the building as measured with the tape (`ec-surveyed.json`). The RS plan stays the
    source for doors;
  - V016 redraws P1 over the saved floor and keeps the old floor whole in `map_replaced_floors`, so the change can
    be undone.
- **Brian's review on 2026-11-04:** the shapes came out irregular, worst next to the Carrera 9 Bis. Fixed in #193.
- **#193, stacked on #183 (`feature/central-floor-one-square-rooms`), ready for review:**
  - `scripts/plan-tracing/regular.py`, run by `register.py` after the fit: walls level or plumb, steps under 0.9 m
    gone, neighbours' walls within 0.23 m on one line, stacked stairs and lifts as fixed anchors;
  - the auditorium's wing is squared between its two measured facades (`askew`), so it comes out as trapezoids.
    **Brian approved the trapezoids on 2026-11-05**;
  - P1 goes from 526 corners and 209 slanted edges to 200 and 43; the same 12 doors; no overlaps; 143 map-service
    tests green.
- **Merge path:** #183 first, then #193 (the restack bot retargets it). Both merge cleanly into `develop`.
- **Re-registering P1 later** regenerates it from the sketch: copy any room edited in the portal into
  `traced/ec-p1-a-mano.json` first (after `export-map-snapshot.py`), or it is lost.
- **Visual reference** (Brian, 2026-11-04): Mappedin and similar flat 2D indoor maps, not 3D ones like
  MapsIndoors. Their traits:
  - rectilinear units that share walls;
  - white walkways;
  - a small set of category colours;
  - a label point inside each unit;
  - amenity icons;
  - a level switcher.
- **The goal after the shapes:** **point-to-point routes**, from one space to another, across floors. So the data
  must grow toward that:
  - walkways as real spaces;
  - openings that connect units to walkways;
  - vertical connections (stairs, elevators, ramps) between levels.
  - IMDF (OGC CS 20-094) is the reference model. Use it as a reference, not adopted wholesale.
- **Inputs:** the sketches and the survey are only on Brian's Mac (see `core.md`). A cloud session needs Brian to
  attach them, or a local session to do the rendering.

### Other

- `feature/kubernetes-kind` (#59) is stale: one commit from 2026-09-23, from before Eureka was removed.

---

## Phases

| Phase | Issue | Status |
|---|---|---|
| 0–2 | #54, #55 | Done |
| 3 · Microsoft sign-in, refresh tokens, roles and permissions | #56 | Done and closed (#141, #142, #169, #172, #179, #148, #156) |
| 4 · End-to-end journeys per profile | #57 | Not started |
| 5 · EC floors faithful to reality | #58 | P1 in #183, under revision. P2 to P8 wait for Brian's sketches |
| 6 · Kubernetes with kind, and production | #59 | Branch stale. Production waits on IT's server decision |
| 7 · notification-service | #60 | Not started. Needs Firebase, and APNs needs the Apple account |
| 8 · Points, voice, damage reports, lost and found | #61 | Not started |

---

## Known debt

- #155: on the dev server every `GET /api/schedule/me*` answered 504 after 10 s. Fixed in
  `bugfix/timetable-waits-on-slow-neighbours`: the Feign timeouts were under a prefix OpenFeign 4 ignores, the
  pensum was read once per class, and the dev server's compose gave schedule-service no address for its
  neighbours. Brian confirms Horario on the-hive after the merge, then closes #155 by hand.
- #157: the gateway should answer 503, not 500.
- #143: one program name; the catalogue and SINU spell it differently.
- Not in an issue yet:
  - the team's development accounts have ADMIN but no profile role (legacy data; fixable with the editor);
  - local password sign-in opens an APP session (30 days) even from the portal;
  - `render-map-base`'s campus PNG drops to 1x to stay under 1 MB.

---

## Next

1. Merge #183, then #193. Let V016 run on the dev server, and verify P1 through the API.
2. Merge the #155 fix, redeploy the dev server, and have Brian open Horario as the test student.
3. #195: `labelPoint` (and `verticalCode` if it stays small) in map 3.7.0, contract first.
4. #157 (gateway 503) and #143 (one program name).
5. EC P2 as soon as Brian sketches it. Otherwise, start phase 4 (#57).
