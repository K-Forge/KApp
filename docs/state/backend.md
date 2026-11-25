# Backend State

> Owner: Backend. As of 2026-11-25, v0.3.0 tagged on `main`. Backend | Local: four PRs open and waiting for review (#266, #268, #271, #272).

---

## Built and working in `develop`

| Service | Contract | What it does | Latest migration |
|---|---|---|---|
| auth-service | auth 1.2.0 | Microsoft sign-in through `POST /auth/microsoft`, checked by `MicrosoftIdTokenVerifier` (RS256, iss, aud, exp; #102, #104). The PORTAL client requires a permission. Rotating refresh tokens with family revocation on reuse (#82). `GET /auth/sign-in-options` (#141). Password sign-in only behind `KAPP_LOCAL_SIGN_IN`, and 404 when it is off (#83). One profile role (STUDENT, PROFESSOR, STAFF, or UNASSIGNED when the directory says nothing, #252) plus permissions (ADMIN, RECEPTION, MAINTENANCE, MODERATION, WELLBEING). `KAPP_FIRST_ADMINS` grants the first ADMIN, at every sign-in until #266 merges. `PUT /auth/admin/accounts/{id}/roles`, synced to user-service (#103). Admin endpoints for accounts, temporary passwords (#116), invitation codes (hidden in the portal, #257; removed by #271) and visitor passes | V005 |
| user-service | user 1.2.0 | Profiles without identity document or phone. `academic` is read-only from the test SINU (#76–#80). `PUT /internal/users/{id}/roles` | V003 |
| semaphore-service | semaphore 2.0.2 | SINU course statuses, read-only through `SinuRecordPort` and `FakeSinuRecordAdapter` (#79). Editable plans at `/me/plans` (`plannedLevel` 1 to 12; a pending course not before the current level, a failed one only in the next period, #253). The catalogue of the 23 pensums | V010 |
| schedule-service | schedule 2.0.2 | Current classes only: `/me`, `/me/day`, `/me/week`, `/me/periods`, from the test SINU (`SinuTimetablePort`, #78). No database | none |
| map-service | map 3.8.0 (#252) | Buildings, floors, layouts, spaces, types, campuses, ground, structures and survey. ETag. `sinuSedes` (#77). `labelPoint` on every drawn space | V017 (EC P1's auditorium, outline and Carrera 9A exit, #221) |
| api-gateway | none | Routes by name, with no registry (#64). Answers 500 when a service is down until #272 merges (#157) | none |

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
  - fake OIDC by default; with #268 only with `local-db`, and auth-service refuses it on Atlas;
  - `local-db` (ADR 0010, #240): a local MongoDB for evaluation and offline runs only, started by
    `scripts/run-locally.sh` or `.ps1` with secrets generated on that computer. Walkthrough in `docs/RUN-LOCALLY.md`.
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
  (walls level or plumb, shared walls on one line, stacked stairs and lifts as anchors). V016 loads it and keeps the
  old floor in `map_replaced_floors`. #218 redraws its auditorium wing (below).
- **Re-registering P1 later** regenerates it from the sketch: copy any room edited in the portal into
  `traced/ec-p1-a-mano.json` first (after `export-map-snapshot.py`), or it is lost.
- **#155 closed:** Horario works on the dev server for the Microsoft-signed-in test student (#203: the dev server's
  compose gave schedule-service its neighbours' URLs, Feign timeouts are read again, the pensum is read once).
  Brian verified it on the emulator.

## Done on 2026-11-19

- **#221 (#218):** EC P1's auditorium redrawn from its plan's walls (`scripts/plan-tracing/wip/aud_walls.py`), the
  floor's outline, and the Carrera 9A exit. `P1-9A-PASILLO`, `P1-9A-RAMPA` and `P1-9A-ESCALERA` and the booth's
  place are **provisional** until Brian takes `in-09` to `in-19` (84 to 94 in the survey sheet). The gate is #248.
- **#252 (#249):** `ROLE_UNASSIGNED`, read access to the map and `/api/users/me` only; the portal shows "Sin rol".
- **#253 (#250):** the two plan level rules in `AcademicPlanService.place`, from `PlanLevels`.
- **#257 (#255):** the portal's invitation codes page hidden.

## In flight

Four PRs from fresh `develop`, opened ready on 2026-11-25. CI runs the Testcontainers tests: Docker Desktop was quit
on the Mac, not paused, so only compiles and the Docker-free tests ran locally.

| PR | Issue | What | Contract | Migration |
|---|---|---|---|---|
| #266 | #241 | `KAPP_FIRST_ADMINS` grants ADMIN only when the account is created, or while it holds no permission and no administrator set its roles; startup logs which listed addresses it still affects | auth 1.2.1 | none |
| #268 | #246 | Fake OIDC only with `local-db`; `StandInSignInGuard` stops auth-service with a stand-in issuer on Atlas; `create-dev-accounts.sh` removed; ADR 0010 amended | none | auth V006, user V004 delete the `@kforge.dev` accounts and profiles |
| #271 | #255 | Invitation codes removed everywhere; `/auth/register` stays for local development, with no code, and gives `ROLE_UNASSIGNED` | auth 1.3.0 | auth V007 drops `invitation_codes` |
| #272 | #157 | The gateway answers 503 when a service is down and 504 when it is too slow, with `ApiError`; `docs/api/README.md` | none (CHANGELOG note) | none |

- **Before #268 merges:** the-hive follows `develop`, so merging runs V006 and V004 on Atlas. The PR has a read-only
  `mongosh` query for Brian to list what goes first.
- **#266 and #271 both bump auth** (1.2.1 and 1.3.0): whichever merges second rebases onto the other's version.
  #268 and #271 touch nearby lines of `RUNBOOK.md` and `SECURITY-AUDIT.md`. Backend rebases the second.
- **Kotlin still names "invitation"** in `ui/invitation/` and its strings (the visitor pass screen). That is
  Santiago's, outside #271.
- **`verticalCode` (#213):** issue open, not started. Contract, service, seed data and portal, one PR each.

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
| 5 · EC floors faithful to reality | #58 | P1 done (#183, #193; auditorium from its plan in #218). P2 to P8 wait for Brian's sketches |
| 6 · Kubernetes with kind, and production | #59 | Branch stale. Production waits on IT's server decision |
| 7 · notification-service | #60 | Not started. Needs Firebase, and APNs needs the Apple account |
| 8 · Points, voice, damage reports, lost and found | #61 | Not started |

---

## Known debt

- #143: one program name; the catalogue and SINU spell it differently.
- Not in an issue yet:
  - the team's `@kforge.dev` development accounts have ADMIN on Atlas, until #268's migrations delete them;
  - local password sign-in opens an APP session (30 days) even from the portal;
  - `render-map-base`'s campus PNG drops to 1x to stay under 1 MB.

---

## Next

1. Review and merge #266, #268, #271 and #272. Then close #241, #246, #255 and #157 by hand, since PRs into
   `develop` do not close issues.
2. #256 (outlines on every floor), #220 (P2's gallery), the 9A measures, then #143 (one program name).
3. #213 (`verticalCode`), contract first.
4. EC P2 as soon as Brian sketches it. Otherwise, start phase 4 (#57).
