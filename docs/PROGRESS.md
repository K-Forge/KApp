# Implementation Status

> Read this before proposing large changes. Last updated: 5 September 2026.

KApp is in the thesis pre-proposal phase (_anteproyecto_). The scope was deliberately narrowed in
August 2026: because the university's academic data is not available, the product cannot depend on
it. Accounts are created from scratch, and the MVP covers **users, campus map, a customisable
preloaded schedule and a customisable preloaded career semaforo**. Everything else is deferred.

The backend runs on the lead developer's machine until university hardware exists. There is no
production deployment.

---

## Summary

| Area | Status | Tests | Notes |
|---|---|---|---|
| Platform foundation | Complete | — | Boot 3.5, MongoDB, RS256/JWKS, Mongock, Testcontainers |
| API contract | Complete | — | Hand-written OpenAPI 3.1 specs, linted in CI, served as mocks |
| **Database isolation** | **Complete** | — | One MongoDB account per service, `readWrite` on one database. Verified by `scripts/verify-db-isolation.sh` |
| **Auth: sign-in and registration** | **Merged** | **99** | Registration, verification, invitation codes with full admin CRUD, `IdentityProviderPort` |
| **User profiles** | **Merged** | **97** | Profiles, internal upsert, accent-insensitive indexed search |
| **Catalogue and semáforo** | **Merged** | **214** | Programs and pensums with full CRUD, student progress, personal academic plans, bulk CSV import |
| **Timetables** | **Merged** | **140** | Enrolments, meetings, overlap detection |
| **Campus map** | **Merged** | **50** | Schematic floors on a grid, wings, corridors, a basement, and an offline grid editor. No floor plan images |
| **Admin and developer portal** | **Merged** | **30** | Angular, from a compose `dev` profile. Full CRUD, bulk import, an API console driven by the specs, and a role inspector |
| **Visitor day pass** | **Merged** | **23** | A one-day token that opens the map and nothing else. No account, no e-mail. Identity documents deleted after 30 days by a TTL index |
| Android (Kotlin) | In progress | 13 | Login and Inicio, drawn against the mockups. No network layer yet |
| iOS (Swift) | Not started | — | The product. Unblocked by the mocks |
| Deployment | Not started | — | Runs locally; university hardware pending |

**635 integration tests** in the backend and **54 in the admin portal**, from a repository that
had none in August. Every service asserts its full role-by-endpoint authorization matrix with one
assertion per case, including every combination that must be refused — those are the ones that
matter.

### Phases closed since the August rebuild

| Phase | What it delivered |
|---|---|
| 1 · Per-service credentials | Each service holds its own MongoDB account with `readWrite` on one database. The separation between services is now enforced by the engine rather than respected by the code — 25 of 25 checks. Four development accounts are created by `scripts/create-dev-accounts.sh`, which generates their passwords locally. **Atlas is pending Brian creating the cluster** — see `ATLAS-SETUP.md` |
| 2 · Admin endpoints | The eight operations the contracts already promised: program create/replace/delete, pensum delete, and invitation-code list/create/activate/delete. Deleting never cascades, and every `409` names what blocks it |
| 3 · Plans and bulk import | Personal academic plans stored as deltas over the immutable pensum, and a CSV import that validates the whole file before writing anything |
| 4 · Schematic map | A floor is a grid the client draws, not a photograph with pins on it. Wings as a field, corridors with the colour they are painted, a basement at level −1, and `accessVia` so the app can say "sube por el ascensor central". **This removed the longest-lead item on the project** — obtaining architectural plans was human latency, and a schematic floor is captured by walking it with `/admin/grid-editor.html` |
| 5 · Visitor day pass | Reception issues a code; a visitor redeems it with an identity document and gets 24 hours of map-only access. No account is created. The token is an ordinary `ROLE_GUEST` one, so "map only" is the matrix every service already enforces rather than a second mechanism that could drift. Open guest registration is gone. **KApp now stores personal data under Ley 1581** — 30-day retention, enforced by MongoDB rather than by a job |
| 6 · Admin portal | CRUD over everything administrable, with a `409` shown as its reason rather than a generic error. Fixed a regression the map change caused — the portal's models still carried `planImageUrl` and pin percentages, so editing a building or space through it would have failed. Two pages said a capability did not exist; both were true when written and had stopped being so. Also closed a real defect: `accessVia` was never validated, and a code matching nothing produces directions to a lift that is not there |
| 7 · Documentation | `REQUIREMENTS.md` rewritten to the real MVP — every entry built or explicitly out of scope. `DESIGN.md` rewritten with current diagrams, its decision table replaced by links to `adr/`. Four new ADRs. `README.md` corrected: it claimed PostgreSQL, HS512, and that role-based authorization was not enforced |

---

## What exists

**Platform.** Spring Boot 3.5.16 and Spring Cloud 2025.0.3, on the 3.x line deliberately: Mongock
publishes no Boot 4 artifact and Spring Cloud 2025.1.x targets Boot 4. All versions are centralised
in the parent POM so parallel branches never edit it.

**Security.** Every service is an OAuth2 resource server validating RS256 against auth-service's
JWKS. This is what closes S1: identity comes from a signed token, not a header the gateway sets,
so reaching a service port directly gains nothing. Service ports are unpublished; only the gateway
is reachable. RS256 rather than a shared secret because a symmetric key given to seven services is
seven places that can mint an administrator token — and because Entra ID signs RS256 with a JWKS,
making that migration a change of property value.

**Contract first.** `docs/api/*.openapi.yaml` are hand-written and served by Prism containers, so
the mobile team works without waiting for the backend. CI lints them on every push.

**Tests.** 635 integration tests on Testcontainers, from a repository that had none, plus 54 in
the portal. Beyond the authorization matrices, they have already earned their keep by catching
real defects:

- In `auth-service`, the role check ran before the `try` block, so an invitation code carrying a
  rejected role consumed its slot permanently — the `release()` in the `catch` never ran. A student
  would have burned an invitation on a registration that failed.

**And one class of defect they could not catch.** Reviewing the admin portal screen by screen in
September turned up a bug that lived *between* two services: "Deactivate" wrote `active = false`
on the profile in `user-service` while the credential in `auth-service` stayed `ACTIVE`, so the
account kept signing in. Both services were individually correct and both suites were green —
the failure only exists end to end, from the button. It is S13 in `SECURITY-AUDIT.md`. The lesson
is recorded here rather than in a commit message: a test per service proves each service, and
nothing yet proves the seam between them.
- A shared static Testcontainers instance was being stopped by the first test class to finish, while
  sibling classes still depended on it. That is the kind of failure that looks random.
- Writing the bulk import surfaced that the **seeded Ingeniería de Sistemas plan does not add up**:
  it declares 142 credits and 194 weekly hours where its 48 courses give 144 and 197. Nobody had
  checked, because nothing had ever added them. The import now refuses a file whose declared totals
  disagree with its own courses, so the next transcription cannot repeat it silently.

**Two findings worth carrying forward.** MongoDB 7 reports `IXSCAN` for an unanchored,
case-insensitive regex while walking the index over its full unbounded key range — the same cost as
a collection scan under a reassuring name. Any assertion about query plans must check narrowed
bounds and documents examined, not the stage name. And a security regression guard must not claim a
path a service might legitimately want, or it collides with the real chain and stops the context
from starting.

---

## Security findings

Tracked in `docs/SECURITY-AUDIT.md`.

| ID | Severity | Status |
|---|---|---|
| S1 | Critical | Resolved. Per-service token validation; service ports unpublished |
| S2 | High | Resolved in the merged services. Every endpoint carries an explicit rule, asserted per role in tests |
| S3 | Moderate | Resolved. CORS allow-list replaces the wildcard |
| S4 | Moderate | Resolved. Eureka discovery locator off; `/internal/**` has no route |
| S5 | Moderate | Resolved. Actuator exposes health and info only, without details |
| S6 | Low | Resolved. Debug logging off by default |
| S7 | Low | Open. Refresh tokens and revocation are deferred; tokens expire in one hour |
| S8 | Informational | Open. HS512 key length — superseded in practice: signing is RS256 now |
| S9 | Informational | Open. Sample data credentials |
| S10 | High | Resolved. MongoDB ran without authentication, so the separation between service databases was a convention the code respected rather than a rule the engine imposed. Each service now holds its own account with `readWrite` on one database |
| S11 | Moderate | Open. The four development accounts hold `ROLE_ADMIN` and the two seeded invitation codes ship in the repository. Both are correct while the stack runs on one laptop and **must be revoked before KApp is reachable from outside** |
| H1 | High | Open. Two development credentials remain readable in git history |

---

## Next

**All seven phases of the September plan are closed.** What remains is not backend work.

**The mobile clients.** They are the product and they have not been started. They are unblocked: the
the contracts are served as Prism mocks, so Kotlin and Swift work does not wait on anything here.

**The data.** The 23 plans the university publishes are loaded (`docs/pensums/`); what the PDFs could
not answer — mostly institutional codes, and credits or hours on some brochures — is listed there.
The roughly 40 floors are still transcription, and the grid editor exists so the team can do it in
parallel without touching code.

### Blocked on somebody else

| Item | Who | Blocks |
|---|---|---|
| Atlas M0 cluster and its connection strings | Brian | Shared development database. `ATLAS-SETUP.md` has the steps |
| SMTP relay | Dirección de TI | E-mail verification. Behind a flag, so nothing else waits |
| Entra ID application registration | Dirección de TI | Institutional sign-in |
| A sketch or photo of one floor | Brian | Modelling the first floor; the rest are captured with the editor |
| Institutional codes, and the credits and hours some brochures omit | Dirección de TI (a SINU export) | Brochure plans stay `DRAFT` until their course codes are real |
| Mobile clients | Iván, Alejandro, Santiago, Brian | Unblocked — the contracts and mocks are ready |
| Telling Dirección de TI that KApp now stores identity documents | Brian | Nothing technical. The summary shared with Gabriel says KApp stores no institutional records, and that stopped being accurate with Phase 5 |

---

## Deferred work

Everything below is known and deliberately postponed. Recorded so it reads as a decision rather
than an oversight.

### Product

| Item | Note |
|---|---|
| `course-service`, `assignment-service` | Frozen. Still in the tree, out of the reactor, compose and CI. They target PostgreSQL/JPA |
| Enrollment, assignments, grading | Out of MVP scope |
| The 17 services in `MICROSERVICES-IDEAS.md` | Out of MVP scope |
| Web client | Frozen. It was a prototype of the mobile layout, not a product surface |

### Engineering

| Item | Note |
|---|---|
| Refresh tokens, logout, revocation | Tokens simply expire (S7) |
| Distributed rate limiting | The gateway limits credential endpoints in memory. Correct for one instance; **revisit before running a second** |
| Spring Cloud Config Server | Configuration is per-service environment variables |
| Distributed tracing | No Zipkin |
| Database per service | One database **and one account** per service, on one MongoDB instance. Moving one service to its own cluster — or to a different engine — is a change to one connection string |
| `app/database/init.sql` | Legacy PostgreSQL schema, reference only |

### Deployment

| Item | Note |
|---|---|
| University hardware | Needs 8 GB RAM minimum, 16 GB recommended, 4 vCPU |
| TLS | Required, not optional: iOS blocks plaintext HTTP and Android has since API 28 |
| Multi-architecture images | Built on Apple Silicon; a typical x86 server needs `docker buildx` |
| Backups | Neon did this invisibly. On-premise needs `mongodump` on a schedule, copied off the host |
| Entra ID | Needs an application registration from the university |

### Repository governance

| Item | Note |
|---|---|
| Branch protection | Was one ruleset with an administrator bypass set to "always", 0 approvals, no required checks, and linear history alongside merge-commit-only merging, so nobody but the bypass could merge. `develop` fell 143 commits behind `main`. Now two rulesets: `develop` takes squash and merge commits, `main` merge commits only; one approval, a code owner review and five green checks on both. Admins bypass only when merging a pull request, so not even they can push to either branch - verified against a throwaway branch carrying the same ruleset. Git Flow's release and hotfix merges need merge commits, which is why linear history went rather than merge commits |
| `CODEOWNERS` | `.github/CODEOWNERS` makes the lead the owner of every path, so the ruleset's code owner review has something to require. GitHub never counts an author's own approval, which is why the lead's pull requests need the admin bypass, scoped to pull requests only |
| Git conventions | `CONTRIBUTING.md` copied from the organization, enforced by `scripts/check-git-conventions.sh` in CI and in a commit hook. Open branches predating it need their commits reworded before they merge |
| Secret scanning, push protection | Disabled |
| Dependabot | Disabled, no `.github/dependabot.yml` |
| Social preview | `portfolio-cover.png` is already 1200x630 but must be uploaded through the GitHub UI |
| Credential rotation (H1) | Two development credentials readable in git history |
