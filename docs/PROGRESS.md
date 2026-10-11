# Implementation Status

> Read this before proposing large changes. Last updated: 11 October 2026.

Brian hands in the thesis proposal (_anteproyecto_) at the end of the 2026-2 term, and the product is presented in
**2027-2**. Between 2 and 5 October 2026 the product was decided again:

- **KApp reads SINU and never copies it.** Timetables, the catalogue, programmes, pensums, levels and course statuses
  are read from SINU, read-only, cached for minutes at most. A student only edits their plans. Until the university
  opens SINU, a **test SINU adapter** serves invented data with its shape.
- **Members sign in with Microsoft** (Entra ID), in the apps and the portal. auth-service issues KApp's own tokens,
  with rotating refresh tokens. Roles are one profile role plus permissions. Password sign-in is development only.
- **The scope grew**: Kubernetes, notifications, and the community features — student voice, damage reports, lost
  and found, citizenship points ([`MICROSERVICES-IDEAS.md`](MICROSERVICES-IDEAS.md)).

Backend: Brian. Android: Iván, Alejandro and Santiago. iOS: Brian. Four chats, one area each, coordinate through
issues labelled `area/*`.

---

## Summary

| Area | Status | Notes |
|---|---|---|
| Platform foundation | Complete | Boot 3.5, MongoDB Atlas, RS256/JWKS, Mongock, Testcontainers. No service registry (#64) |
| API contracts | **The 2.0 set** | semaphore 2.0, schedule 2.0, auth 1.0, user 1.0, map 3.6 (#42). Mocks on one port, 4000 (#40). History in [`api/CHANGELOG.md`](api/CHANGELOG.md) |
| Database isolation | Complete | One database and one account per service that stores data. Verified by `scripts/verify-db-isolation.sh` |
| Academic data | **Read-only, from SINU** | Behind the test SINU adapter (#77–#80). semaphore keeps only the student's plans; schedule has no database |
| Identity | **In progress** | Microsoft sign-in, rotating refresh tokens, profile roles and permissions: backend phase 3 (#56). Refresh tokens in #82, password sign-in closed outside development in #83 |
| User profiles | Merged | user 1.0 (#80): names, picture, roles, active. No identity document, phone or student code. A student's programme is read from SINU |
| Campus map | Merged | Buildings mapped to their SINU sedes, floors drawn as polygons from the evacuation plans, cacheable reads (#77) |
| Admin and developer portal | Merged | Angular, bilingual. CRUD, an API console driven by the specs, a role inspector, and the floor editor |
| Visitor day pass | Merged | A one-day token that opens the map and nothing else. Identity documents deleted after 30 days by a TTL index |
| Android (Kotlin) | **In progress** | Merged: the mocks (#69), Microsoft sign-in and session (#70), profile (#71), semáforo with plans (#72), timetable (#73) |
| iOS (Swift) | In progress | Login, Home, Timetable and Semáforo in its worktree, not merged yet |
| Dev server | Running | the-hive follows `develop`, with per-branch images on GHCR |
| Kubernetes and production | Planned | Backend phase 6 (#59) |
| Notifications and community | Planned | Backend phases 7 and 8 (#60, #61) |

---

## The October plan

One issue per backend phase. The clients follow each contract through `area/kotlin` and `area/swift` issues (#43–#47).

| Phase | Issue | Status |
|---|---|---|
| 1 · Remove Eureka and the frozen services | #54 | Code merged (#64, #65); the plan material and the two `.docx` remain |
| 2 · Read-only academic data behind a test SINU | #55 | Merged (#77–#80) |
| 3 · Microsoft sign-in, rotating refresh tokens, roles and permissions | #56 | In progress (#82, #83). The Entra ID test tenant is #62 |
| 4 · End-to-end journeys per profile | #57 | Planned |
| 5 · EC floors faithful to the plans | #58 | Blocked on Brian's sketches |
| 6 · Kubernetes with kind, the guide, and production | #59 | Planned |
| 7 · `notification-service`: inbox, push and an outbox | #60 | Planned |
| 8 · Points, voice, damage reports, lost and found | #61 | Planned |

Events, clubs and recreation bookings wait for a questionnaire to the offices.

### The September plan (closed)

Kept as the record of how the backend got here. Phases 1 to 7 closed on 5 September 2026.

| Phase | What it delivered |
|---|---|
| 1 · Per-service credentials | Each service holds its own MongoDB account with `readWrite` on one database, so the separation between services is enforced by the engine rather than respected by the code. Development accounts are created by `scripts/create-dev-accounts.sh`, which generates their passwords locally |
| 2 · Admin endpoints | The eight operations the contracts already promised. Deleting never cascades, and every `409` names what blocks it |
| 3 · Plans and bulk import | Personal academic plans stored as deltas over the immutable pensum, and a CSV import that validates the whole file before writing anything |
| 4 · Schematic map | A floor drawn by the client, not a photograph with pins on it, with `accessVia` so the app can say "sube por el ascensor central". **This removed the project's longest-lead item**, obtaining architectural plans. Floors have since moved from grids to polygons traced from the evacuation plans |
| 5 · Visitor day pass | Reception issues a code; a visitor redeems it with an identity document and gets 24 hours of map-only access, with no account. **KApp stores personal data under Ley 1581 from here on** — 30-day retention, enforced by MongoDB rather than by a job |
| 6 · Admin portal | CRUD over everything administrable, with a `409` shown as its reason. Also closed a real defect: `accessVia` was never validated, and a code matching nothing produced directions to a lift that is not there |
| 7 · Documentation | `REQUIREMENTS.md`, `DESIGN.md` and the ADRs rewritten to what existed. `README.md` corrected: it claimed PostgreSQL, HS512, and that role-based authorization was not enforced |

---

## What exists

**Platform.** Spring Boot 3.5 and Spring Cloud 2025.0, on the 3.x line deliberately: Mongock publishes no Boot 4
artifact and Spring Cloud 2025.1.x targets Boot 4. All versions are centralised in the parent POM so parallel
branches never edit it. Services address each other by name, which Docker Compose and Kubernetes resolve; there is
no registry.

**Security.** Every service is an OAuth2 resource server validating RS256 against auth-service's JWKS. This is what
closes S1: identity comes from a signed token, not a header the gateway sets, so reaching a service port directly
gains nothing. Members sign in with Microsoft; auth-service verifies that sign-in and issues KApp's own tokens, so no
other service changes.

**Contract first.** `docs/api/*.openapi.yaml` are hand-written and served by Prism, all five on one port, so the
clients work without waiting for the backend. CI lints them on every push.

**Tests.** Integration tests on Testcontainers in every service, from a repository that had none in August 2026, plus
the portal's and the Android app's own. Every service asserts its full role-by-endpoint authorization matrix,
including every combination that must be refused. Beyond the matrices, they have earned their keep by catching real
defects:

- In `auth-service`, the role check ran before the `try` block, so an invitation code carrying a rejected role
  consumed its slot permanently — the `release()` in the `catch` never ran.
- A shared static Testcontainers instance was being stopped by the first test class to finish, while sibling classes
  still depended on it. That is the kind of failure that looks random.
- Writing the bulk import surfaced that the **seeded Ingeniería de Sistemas plan did not add up**: it declared 142
  credits and 194 weekly hours where its 48 courses give 144 and 197. The import now refuses a file whose declared
  totals disagree with its own courses.

**And one class of defect they could not catch.** Reviewing the admin portal screen by screen in September turned up
a bug that lived *between* two services: "Deactivate" wrote `active = false` on the profile in `user-service` while
the credential in `auth-service` stayed `ACTIVE`, so the account kept signing in. Both suites were green — the failure
only existed end to end. It is S13 in `SECURITY-AUDIT.md`, closed by the `user → auth` call that suspends the
credential. A test per service proves each service, and nothing yet proves the seam between them: that is backend
phase 4.

**Two findings worth carrying forward.** MongoDB 7 reports `IXSCAN` for an unanchored, case-insensitive regex while
walking the index over its full key range — the cost of a collection scan under a reassuring name. Any assertion
about query plans must check narrowed bounds and documents examined, not the stage name. And a security regression
guard must not claim a path a service might legitimately want, or it collides with the real chain and stops the
context from starting.

---

## Security findings

Tracked in `docs/SECURITY-AUDIT.md`.

| ID | Severity | Status |
|---|---|---|
| S1 | Critical | Resolved. Per-service token validation; service ports unpublished |
| S2 | High | Resolved. Every endpoint carries an explicit rule, asserted per role in tests |
| S3 | Moderate | Resolved. CORS allow-list replaces the wildcard |
| S4 | Moderate | Resolved. No registry since #64; the gateway routes only to explicit addresses, and `/internal/**` has no route |
| S5 | Moderate | Resolved. Actuator exposes health and info only, without details; the Eureka dashboard is gone |
| S6 | Low | Resolved. Debug logging off by default |
| S7 | Low | Open. Tokens expire in one hour with no refresh or revocation; rotating refresh tokens are in #82 |
| S8 | Informational | Open. HS512 key length — superseded in practice: signing is RS256 now |
| S9 | Informational | Open. Sample data credentials |
| S10 | High | Resolved. Each service holds its own MongoDB account with `readWrite` on one database |
| S11 | Moderate | Open. The development accounts hold `ROLE_ADMIN` and two seeded invitation codes ship in the repository. Correct while nothing is reachable from outside; #83 closes password sign-in and invitation codes outside development |
| H1 | High | Open. Two development credentials remain readable in git history. Rotation belongs to backend phase 6 |

---

## Blocked on somebody else

| Item | Who | Blocks |
|---|---|---|
| Access to SINU | The university's IT director | Real academic data. Until then, and for the presentation if it never comes, the test SINU adapter |
| Entra ID application registration | The university's IT director | Institutional Microsoft sign-in. Development uses a test tenant (#62) and a fake identity server |
| Floor sketches of the EC building | Brian | Backend phase 5 (#58) |
| An Apple Developer account | Funding being sought | Push notifications on iOS |
| Production hardware | The university's IT director | Production. The fallback is Brian's Mac running kind |
| Telling the IT director that KApp stores visitors' identity documents | Brian | Nothing technical: the summary shared earlier says KApp stores no institutional records |

---

## Out of scope, and deferred

### Out of scope

Recorded so they read as decisions rather than oversights. [`SRS.md`](SRS.md) gives the full list.

| Item | Note |
|---|---|
| Anything that writes to SINU, or copies it | KApp reads SINU; it is not a second copy of it |
| Editing grades, enrolment, payments, personal documents, chat | They belong to SINU or to other systems |
| `course-service`, `assignment-service` | Deleted in October 2026 (#65); git keeps them |
| The 2025 HTML prototype and its PostgreSQL schema | Deleted in October 2026 (#51). A better public demo is #92 |

### Deferred engineering

| Item | Note |
|---|---|
| Distributed rate limiting | The gateway limits credential endpoints in memory. Correct for one instance; **revisit before running a second** |
| Spring Cloud Config Server | Configuration is per-service environment variables |
| Distributed tracing | No Zipkin |
| A cluster per service | One database **and one account** per service, on one cluster. Moving one service to its own cluster is a change to one connection string |

### Deployment

Backend phase 6 (#59) covers it: Kubernetes with kind locally and in production, TLS — required, since iOS blocks
plaintext HTTP and Android has since API 28 — and backups copied off the host. Images are already built for amd64 and
arm64 and published per branch to GHCR.

---

## Repository governance

| Item | Note |
|---|---|
| Branch protection | Was one ruleset with an administrator bypass set to "always", 0 approvals, no required checks, and linear history alongside merge-commit-only merging, so nobody but the bypass could merge. `develop` fell 143 commits behind `main`. Now two rulesets: `develop` takes squash and merge commits, `main` merge commits only; one approval, a code owner review and five green checks on both. Admins bypass only when merging a pull request, so not even they can push to either branch - verified against a throwaway branch carrying the same ruleset. Git Flow's release and hotfix merges need merge commits, which is why linear history went rather than merge commits |
| `CODEOWNERS` | `.github/CODEOWNERS` makes the lead the owner of every path, so the ruleset's code owner review has something to require. GitHub never counts an author's own approval, which is why the lead's pull requests need the admin bypass, scoped to pull requests only |
| Git conventions | `CONTRIBUTING.md` copied from the organization, enforced by `scripts/check-git-conventions.sh` in CI and in a commit hook |
| Stacked pull requests | `.github/workflows/restack.yml` rebases them after a squash merge, pushing as the K-Forge restack App so their checks run again (#85) |
| Secret scanning, push protection | Enabled on 2026-10-11 (#53). The first scan raised eight alerts, all documentation placeholders (`...`, two letters, the word `password`; Atlas requires eight characters), and they were closed as false positives |
| Dependabot | `.github/dependabot.yml` (#53): weekly, minor and patch grouped per ecosystem, into `develop`. Boot 4, Spring Cloud 2025.1 and JDK or Node majors are ignored on purpose. Dependabot reads the file from the default branch, so it starts once the file reaches `main` |
| Closing issues | A pull request into `develop` does not close its issue; only `main`, the default branch, does that. Issues are closed by hand after the merge |
| Social preview | `portfolio-cover.png` is already 1200x630 but must be uploaded through the GitHub UI |
| Credential rotation (H1) | Two development credentials readable in git history |
