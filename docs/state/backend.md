# Backend State

> Owner: Backend. As of 2026-11-05, `develop` at `942d46a`. Backend | Local: EC P1 verified on the dev server, the
> map 3.7.0 contract merged (#211), and its implementation in #212.

---

## Built and working in `develop`

| Service | Contract | What it does | Latest migration |
|---|---|---|---|
| auth-service | auth 1.1.0 | Microsoft sign-in through `POST /auth/microsoft`, checked by `MicrosoftIdTokenVerifier` (RS256, iss, aud, exp; #102, #104). The PORTAL client requires a permission. Rotating refresh tokens with family revocation on reuse (#82). `GET /auth/sign-in-options` (#141). Password sign-in only behind `KAPP_LOCAL_SIGN_IN`, and 404 when it is off (#83). One profile role (STUDENT, PROFESSOR or STAFF; STAFF when the directory says nothing) plus permissions (ADMIN, RECEPTION, MAINTENANCE, MODERATION, WELLBEING). `KAPP_FIRST_ADMINS` grants the first ADMIN. `PUT /auth/admin/accounts/{id}/roles`, synced to user-service (#103). Admin endpoints for accounts, temporary passwords (#116), invitation codes and visitor passes | V005 |
| user-service | user 1.1.0 | Profiles without identity document or phone. `academic` is read-only from the test SINU (#76–#80). `PUT /internal/users/{id}/roles` | V003 |
| semaphore-service | semaphore 2.0.1 | SINU course statuses, read-only through `SinuRecordPort` and `FakeSinuRecordAdapter` (#79). Editable plans at `/me/plans` (`plannedLevel` 1 to 12). The catalogue of the 23 pensums | V010 |
| schedule-service | schedule 2.0.1 | Current classes only: `/me`, `/me/day`, `/me/week`, `/me/periods`, from the test SINU (`SinuTimetablePort`, #78). No database | none |
| map-service | map 3.6.0 (contract 3.7.0 merged in #211; service in #212) | Buildings, floors, layouts, spaces, types, campuses, ground, structures and survey. ETag. `sinuSedes` (#77) | V016 (EC P1 from Brian's sketch, squared: #183, #193) |
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

## Done on 2026-11-05

- **EC floor 1** (#183, then #193): traced from Brian's sketch, with rooms squared by `scripts/plan-tracing/regular.py`
  (walls level or plumb, shared walls on one line, stacked stairs and lifts as anchors; the auditorium wing as
  trapezoids along its two measured facades, as Brian approved). V016 loads it and keeps the old floor in
  `map_replaced_floors`.
- **Re-registering P1 later** regenerates it from the sketch: copy any room edited in the portal into
  `traced/ec-p1-a-mano.json` first (after `export-map-snapshot.py`), or it is lost.
- **#155 closed:** Horario works on the dev server for the Microsoft-signed-in test student (#203: the dev server's
  compose gave schedule-service its neighbours' URLs, Feign timeouts are read again, the pensum is read once).
  Brian verified it on the emulator.

## In flight

- **map 3.7.0, `labelPoint` (#195):**
  - #211, merged: the contract. `Space.labelPoint`, the `CentralGroundFloor` example (EC P1 as the dev server
    serves it) first in `getFloor`, so the mocks serve it, and the CHANGELOG entry.
  - #212 (`feature/map-label-point-service`): `LabelPoint` (polylabel, precision 1, then the best whole point
    around it) in every response with a `shape`. Tests: every seeded space's label is inside its shape, and the
    contract example equals what the service computes. Until it merges, the dev server sends no `labelPoint`.
- **`verticalCode` (#213):** issue open, not started. Contract, service, seed data and portal, one PR each.
- **EC P1 on the dev server, verified 2026-11-05:** the API returns exactly what V016 seeds (46 spaces, 42 drawn,
  4 corridors, same doors), and the portal draws it whole. To check the dev server through the portal or the API,
  Brian signs in as `administrativo.prueba`, the test account that holds ADMIN there; the other test accounts have
  no portal permission.

## Map direction (Brian, 2026-11-04)

- **Visual reference:** Mappedin and similar flat 2D indoor maps, not 3D. The shared style is
  `docs/design/map-style.json`, and the renderer plan is issue #194.
- **Goal after the shapes: point-to-point routes across floors.** Data must grow toward walkways as real spaces,
  openings that connect units to walkways, and vertical connections between levels. IMDF (OGC CS 20-094) is the
  reference model, not adopted wholesale. Backend's proposal for this is the last comment on #183.
- **Inputs:** the sketches and the survey live only on Brian's Mac (see `core.md`).

### Other

- `feature/kubernetes-kind` (#59) is stale: one commit from 2026-09-23, from before Eureka was removed.

---

## Phases

| Phase | Issue | Status |
|---|---|---|
| 0–2 | #54, #55 | Done |
| 3 · Microsoft sign-in, refresh tokens, roles and permissions | #56 | Done and closed (#141, #142, #169, #172, #179, #148, #156) |
| 4 · End-to-end journeys per profile | #57 | Not started |
| 5 · EC floors faithful to reality | #58 | P1 done (#183, #193). P2 to P8 wait for Brian's sketches |
| 6 · Kubernetes with kind, and production | #59 | Branch stale. Production waits on IT's server decision |
| 7 · notification-service | #60 | Not started. Needs Firebase, and APNs needs the Apple account |
| 8 · Points, voice, damage reports, lost and found | #61 | Not started |

---

## Known debt

- #157: the gateway should answer 503, not 500.
- #143: one program name; the catalogue and SINU spell it differently.
- Not in an issue yet:
  - the team's development accounts have ADMIN but no profile role (legacy data; fixable with the editor);
  - local password sign-in opens an APP session (30 days) even from the portal;
  - `render-map-base`'s campus PNG drops to 1x to stay under 1 MB.

---

## Next

1. #212 merged once its CI is green.
2. #157 (gateway 503), in a new Backend | Local session. Then #143 (one program name).
3. #213 (`verticalCode`), contract first.
4. EC P2 as soon as Brian sketches it. Otherwise, start phase 4 (#57).
