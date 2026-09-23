# KApp · Requirements

> Version 2.0 · 5 September 2026
> K-Forge Development Club · Fundación Universitaria Konrad Lorenz

> **This replaces version 1.0 (February 2026)**, which described courses, assignments and grading.
> None of that is being built. The scope was narrowed in August 2026 for a reason worth stating
> plainly: **the university's academic data is not available**, so the product cannot depend on it.
> Everything below is either built or explicitly out of scope — there are no pending requirements
> here that nobody intends to implement.

---

## 1. What KApp is

A mobile platform that centralises the university services a student actually uses day to day:
finding a room, seeing their timetable, and tracking their progress through their programme.

Accounts are created from scratch inside KApp. Nothing is imported from SINU or from any other
institutional system, because nothing is available to import.

**Delivery targets Android and iOS directly.** There is a web portal, but it is an administration
and development console for the six of us, not a product surface.

---

## 2. Actors

| Actor | What they can reach |
|---|---|
| **Student** | Their own profile, timetable and semáforo, plus the campus map and the catalogue |
| **Professor** | Their own profile and timetable, plus the campus map and the catalogue. **No semáforo** — a lecturer has no academic record of their own |
| **Administrator** | Everything above, plus the catalogue, the map, users, invitation codes and visitor passes |
| **Visitor** | The campus map, for 24 hours, holding a day pass issued at reception. No account |

`ROLE_ADMIN` is **never** grantable through an invitation code. The seeded codes ship in a public
repository; a code that could mint an administrator would mean anyone who can read the repository
can escalate.

---

## 3. Functional requirements — in scope, and built

### 3.1 Identity (AUTH)

| # | Requirement | State |
|---|---|---|
| AUTH-01 | Registration with an institutional address and an invitation code, which decides the role | Built |
| AUTH-02 | E-mail verification, behind a flag while no SMTP relay exists | Built, blocked on SMTP |
| AUTH-03 | Sign-in returning an RS256 access token, verified by every service against a published JWKS | Built |
| AUTH-04 | Administration of invitation codes: list, create, activate/deactivate, delete | Built |
| AUTH-05 | Visitor day passes: issue, redeem against an identity document, revoke, and a register | Built |
| AUTH-06 | An `IdentityProviderPort` so Entra ID can replace local credentials without touching a caller | Built, pending the university's application registration |

### 3.2 Profiles (USER)

| # | Requirement | State |
|---|---|---|
| USER-01 | A profile per account, addressed only by the token's subject — never by a path parameter | Built |
| USER-02 | Accent-insensitive directory search over name, e-mail and student code | Built |
| USER-03 | Administrative listing and activation state | Built |
| USER-04 | A guest has no profile at all; `ROLE_GUEST` is refused on the profile endpoints | Built |

### 3.3 Catalogue and semáforo (SEM)

| # | Requirement | State |
|---|---|---|
| SEM-01 | Programs and pensums (pensums) with full administrative CRUD | Built |
| SEM-02 | A student's progress per course, materialised lazily on first read | Built |
| SEM-03 | Prerequisite-aware eligibility: which courses the student may take next | Built |
| SEM-04 | Elective slots resolved to real courses | Built |
| SEM-05 | **Personal academic plans**: the pensum is immutable — the *Semáforo Original* — and a plan stores only the courses the student moved | Built |
| SEM-06 | Moving a course **never** affects eligibility. Planning is not passing | Built |
| SEM-07 | No cap on courses per level: students take more or fewer than the nominal six | Built |
| SEM-08 | Bulk import of pensums from CSV, validating the whole file before writing anything | Built |
| SEM-09 | Deleting never cascades: a program with pensums, or a pensum with students, is refused with `409` naming what blocks it | Built |

### 3.4 Timetable (SCHED)

| # | Requirement | State |
|---|---|---|
| SCHED-01 | A preloaded timetable the student customises: enrolments and weekly meetings | Built |
| SCHED-02 | Overlap detection between meetings | Built |
| SCHED-03 | The day's agenda, ordered, so the client can show "next class" from its own clock | Built |

### 3.5 Campus map (MAP)

| # | Requirement | State |
|---|---|---|
| MAP-01 | Buildings and floors, each floor a **grid** the client draws — not a plan image | Built |
| MAP-02 | Spaces occupying a rectangle of cells, with corridors as polylines through the grid | Built |
| MAP-03 | Wings as a field: `301`, `301-N` and `301-S` are three different rooms | Built |
| MAP-04 | `accessVia`, so the app can say "piso 4, sube por el ascensor central" | Built |
| MAP-05 | A basement at level −1 | Built |
| MAP-06 | Accent-insensitive search over name, code and aliases | Built |
| MAP-07 | One round trip from a room code to the space, its floor and its building | Built |
| MAP-08 | Readable by a visitor holding a day pass | Built |
| MAP-09 | A floor editor in the admin portal, usable on an iPad standing in the floor, that keeps unsaved changes on the device | Built |

### 3.6 Platform (INFRA)

| # | Requirement | State |
|---|---|---|
| INFRA-01 | Six services behind one gateway; service ports unreachable from outside | Built |
| INFRA-02 | One database **and one account** per service, isolation enforced by the engine | Built |
| INFRA-03 | Versioned, audited migrations (Mongock) | Built |
| INFRA-04 | Contract-first OpenAPI, linted in CI and served as Prism mocks | Built |
| INFRA-05 | Integration tests on Testcontainers, with a full role-by-endpoint authorization matrix per service | Built |
| INFRA-06 | Rate limiting on the credential endpoints | Built, in-memory — correct for one instance |

### 3.7 Administration portal (PORTAL)

| # | Requirement | State |
|---|---|---|
| PORTAL-01 | CRUD over everything administrable, with the contract's rules respected and a `409` shown with its reason | Built |
| PORTAL-02 | An API console driven by the OpenAPI specs themselves | Built |
| PORTAL-03 | A role inspector showing what each role may reach | Built |

---

## 4. Non-functional requirements

| # | Requirement |
|---|---|
| NFR-01 | Every service validates the token itself. Identity never comes from a header the gateway sets |
| NFR-02 | Every endpoint carries an explicit authorization rule, asserted per role in tests — including every combination that must be refused |
| NFR-03 | An authenticated caller lacking the role gets `403`, never `404` — except where a `404` deliberately avoids confirming that a resource exists |
| NFR-04 | Errors share one envelope, with field-level details a client can map to a form |
| NFR-05 | TLS before anything is reachable from outside a laptop. iOS blocks plaintext HTTP and Android has since API 28 |
| NFR-06 | Personal data has a stated purpose, a retention period, and automatic deletion — see §6 |
| NFR-07 | Spanish is the language of everything a student reads |

---

## 5. Constraints

- **No official academic data.** Accounts, pensums and timetables are created inside KApp.
- **No deployment.** It runs on the lead developer's machine until university hardware exists.
- **November 2026.** The thesis deadline is fixed; scope is what moves.
- **Six people, part time.** Every operational cost is paid by somebody's evening.

---

## 6. Personal data

KApp stores no institutional or government-issued data **except one thing**: the identity document a
visitor presents when redeeming a day pass.

That is personal data under **Ley 1581 de 2012** (habeas data). Its purpose is reception knowing who
was in the building; it is readable only by `ROLE_ADMIN` under `/auth/admin/visitor-passes`; and it
is **deleted automatically after 30 days** by a MongoDB TTL index rather than by a scheduled job
that could stop running.

Dirección de TI has not yet been told. Tracked as S12 in `SECURITY-AUDIT.md`.

---

## 7. Explicitly out of scope

Not "pending". Not being built for this thesis.

| Item | Why |
|---|---|
| Courses, assignments, grading | Needs official academic data, which does not exist for us |
| Enrolment (matrícula) | Belongs to the university's own system |
| `course-service`, `assignment-service` | Frozen in the tree, out of the reactor, compose and CI. They target PostgreSQL/JPA |
| The 17 services in `MICROSERVICES-IDEAS.md` | Ideas, not commitments |
| The old web client | It was a prototype of the mobile layout, not a product surface |
| Push notifications, chat, payments | Never in scope |

## 8. Deferred to a later version

Known, deliberately postponed, and recorded so it reads as a decision rather than an oversight.

| Item | Note |
|---|---|
| Refresh tokens, logout, revocation | Tokens expire in one hour (S7) |
| Distributed rate limiting | Needs Redis. **Revisit before running a second gateway instance** |
| Sharing a semáforo | The client draws it and exports a PNG; no backend involved |
| Real floor plans behind the schematic grid | Possible later without either model owning the coordinates |
| SINU integration | The CSV import format is what an export would be mapped onto |
| Entra ID sign-in | `IdentityProviderPort` exists; needs the university's application registration |
