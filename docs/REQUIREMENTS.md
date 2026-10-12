# KApp · Requirements

> Version 3.0 · 11 October 2026
> K-Forge Development Club · Fundación Universitaria Konrad Lorenz

> **This replaces version 2.0 (September 2026)**, which created accounts inside KApp, let students build their own
> timetable and drew floors as grids. Between 2 and 5 October 2026 the product was decided again: **KApp reads SINU
> and never copies it**, members sign in with Microsoft, and notifications and community features join the plan.
> [`SRS.md`](SRS.md) gives the product's shape; this file lists the requirements and the state of each.

States: **Built** (merged into `develop`), **In progress** (its backend phase has started), **Planned** (in a phase
that has not started), **Depends on SINU** (shaped by what the university's system provides).

---

## 1. What KApp is

A mobile application, for Android and iOS, that shows a student their timetable, their progress through their
pensum and the campus map, and gives the community notifications, a student voice, damage reports and lost and
found. A web portal serves the staff who administer it; it is not a product surface.

**Everything academic is read from SINU, read-only, and nothing academic is stored.** A student only edits their own
plans. Until the university opens SINU, a test SINU adapter serves invented data with its shape.

---

## 2. Actors

Every member has **one profile role**, taken from the university directory at sign-in. Permissions are granted on top
by an administrator and only open the portal.

| Actor | Role | What they can reach |
|---|---|---|
| **Student** | `ROLE_STUDENT` | Their own profile, timetable, semáforo and plans; the map; notifications; the community features |
| **Professor** | `ROLE_PROFESSOR` | Their own profile and sections; the map; notifications; the community features. No semáforo |
| **Administrative staff** | `ROLE_STAFF` | The map. Also the default when the directory names no role |
| **Visitor** | `ROLE_GUEST` | The map, for 24 hours, with a day pass from reception. No account |

| Permission | Manages |
|---|---|
| `ROLE_ADMIN` | Accounts, roles and permissions, and the campus map |
| `ROLE_RECEPTION` | Visitor passes, and lost-and-found hand-ins and claims |
| `ROLE_MAINTENANCE` | Damage reports |
| `ROLE_MODERATION` | Student voice and comments |
| `ROLE_WELLBEING` | Citizenship points, and the wellbeing features once they exist |

---

## 3. Functional requirements

### 3.1 Identity (AUTH)

| # | Requirement | State |
|---|---|---|
| AUTH-01 | Members sign in with their university Microsoft account: the client sends the ID token to `POST /auth/microsoft`, and auth-service verifies issuer, audience, expiry and signature | In progress — phase 3 (#56) |
| AUTH-02 | KApp issues its own RS256 access tokens, verified by every service against a published JWKS | Built |
| AUTH-03 | Rotating refresh tokens: 30 days, sliding, in the apps; 12 hours in the portal. Reusing a refresh token revokes the whole session | Built (#82) |
| AUTH-04 | Sign-out, and suspending an account revokes all its sessions | In progress — phase 3 (#56) |
| AUTH-05 | The profile role comes from a configurable directory claim; permissions are granted by an administrator (`PUT /auth/admin/accounts/{userId}/roles`) | In progress — phase 3 (#56) |
| AUTH-06 | Visitor day passes: issue, redeem against an identity document, revoke, and a register | Built |
| AUTH-07 | Password sign-in, registration, verification and invitation codes answer only when local sign-in is enabled, for development and tests | Built (#83) |

### 3.2 Profiles (USER)

| # | Requirement | State |
|---|---|---|
| USER-01 | A profile per account, addressed only by the token's subject — never by a path parameter | Built |
| USER-02 | Accent-insensitive directory search over name and e-mail | Built |
| USER-03 | Administrative listing and activation state; deactivating a profile suspends its credential | Built |
| USER-04 | A profile keeps names, a picture, roles and the active flag — **no identity document, phone or student code** | Built (#80) |
| USER-05 | A student's programme, pensum and level are read from SINU when they ask for their own profile | Built (#80) |
| USER-06 | A visitor has no profile at all; `ROLE_GUEST` is refused on the profile endpoints | Built |

### 3.3 Catalogue, semáforo and plans (SEM)

| # | Requirement | State |
|---|---|---|
| SEM-01 | The catalogue — programmes, pensums, their items and elective banks — read from SINU. The published plans are kept as the catalogue's backup, loadable from CSV | Built (#79) |
| SEM-02 | A student's semáforo: SINU's status for each course — passed, in progress, pending, failed or postponed (*aplazada*) — never stored | Built (#79) |
| SEM-03 | A progress summary for the student | Built |
| SEM-04 | Prerequisite-aware eligibility: which courses the student may take next | Built |
| SEM-05 | **Personal plans**: several per student, one primary. A plan moves pending courses between levels and stores only the moves; the pensum stays immutable | Built |
| SEM-06 | Moving a course **never** affects eligibility. Planning is not passing | Built |
| SEM-07 | A course has a `pensumItemCode`, its fixed slot in the pensum, and the `sinuCode` a student sees. No invented codes | Built (#79) |
| SEM-08 | A student's grades, read-only, when SINU provides them | Depends on SINU |

### 3.4 Timetable (SCHED)

| # | Requirement | State |
|---|---|---|
| SCHED-01 | The student's timetable for a period, read from SINU and **not edited in KApp**; schedule-service stores nothing | Built (#78) |
| SCHED-02 | Day and week views, so the client can show "next class" from its own clock | Built |
| SCHED-03 | Times in SINU's 45-minute blocks; each room resolved to its building through the SINU sede, since room numbers repeat across buildings | Built (#77, #78) |
| SCHED-04 | A professor sees their own sections | Planned |

### 3.5 Campus map (MAP)

| # | Requirement | State |
|---|---|---|
| MAP-01 | Buildings and floors, each floor drawn as **polygons** traced from the university's evacuation plans — not a plan image | Built |
| MAP-02 | Wings as a field: `301`, `301-N` and `301-S` are three different rooms | Built |
| MAP-03 | `accessVia`, so the app can say "piso 4, sube por el ascensor central" | Built |
| MAP-04 | A basement at level −1 | Built |
| MAP-05 | Accent-insensitive search over name, code and aliases | Built |
| MAP-06 | One round trip from a room code to the space, its floor and its building | Built |
| MAP-07 | Readable by any signed-in member and by a visitor holding a day pass | Built |
| MAP-08 | Each building names the SINU sedes it corresponds to | Built (#77) |
| MAP-09 | Map reads a client can cache and revalidate | Built (#77) |
| MAP-10 | The campus ground, the structures on its blocks, and the survey the floors were drawn from | Built |
| MAP-11 | A floor editor in the portal, usable on an iPad standing in the floor, that keeps unsaved changes on the device | Built |
| MAP-12 | The EC building's floors faithful to its plans | Planned — phase 5 (#58), blocked on sketches |

### 3.6 Notifications (NOTIF)

| # | Requirement | State |
|---|---|---|
| NOTIF-01 | An in-app inbox per member | Planned — phase 7 (#60) |
| NOTIF-02 | Push notifications: Firebase on Android, APNs on iOS once an Apple Developer account exists | Planned — phase 7 (#60) |
| NOTIF-03 | Per-topic preferences | Planned — phase 7 (#60) |
| NOTIF-04 | Every service notifies through `notification-service`, via a transactional outbox | Planned — phase 7 (#60) |

### 3.7 Community (VOICE, REPORT, LOST, POINTS)

All planned in backend phase 8 (#61). The roadmap is [`MICROSERVICES-IDEAS.md`](MICROSERVICES-IDEAS.md).

| # | Requirement |
|---|---|
| VOICE-01 | A student posts a complaint or a suggestion. Not anonymous: the author chooses to show their name or their programme, and moderation always sees who wrote it |
| VOICE-02 | Posts may not name people. A word filter, reports from users and human moderation; content is hidden once reports pass a threshold, starting at 5 |
| VOICE-03 | Votes, and comments of 20 to 200 words. No official replies |
| REPORT-01 | A damage report with its place on the map, a photo and a short description, with the same votes, comments and reports |
| REPORT-02 | Maintenance validates and closes reports |
| LOST-01 | Reception keeps the objects. Hand-in with a one-time code reception generates, valid 15 minutes |
| LOST-02 | Claim with a one-time code the app generates once the server confirms the match, valid 10 minutes |
| LOST-03 | The history is never deleted. No photos of documents, IDs or cards |
| POINTS-01 | Citizenship points as a ledger, visible to their owner and to the university, not redeemable yet, and allowed to go negative |
| POINTS-02 | Points for handing in an object, a validated report, a well-received post and votes (capped per day); lost when a report a member filed is overturned |

Events, clubs, recreation bookings and the wellbeing services wait for a questionnaire to the offices that run them.

### 3.8 Platform (INFRA)

| # | Requirement | State |
|---|---|---|
| INFRA-01 | Five services behind one gateway; service ports unreachable from outside; services addressed by name, with no registry | Built |
| INFRA-02 | One database **and one account** per service that stores data, isolation enforced by the engine | Built |
| INFRA-03 | Versioned, audited migrations (Mongock) | Built |
| INFRA-04 | Contract-first OpenAPI, linted in CI and served as Prism mocks on one port | Built |
| INFRA-05 | Integration tests on Testcontainers, with a full role-by-endpoint authorization matrix per service | Built |
| INFRA-06 | Rate limiting on the credential endpoints | Built, in memory — correct for one instance |
| INFRA-07 | A development server that follows `develop`, with per-branch images for amd64 and arm64 | Built |
| INFRA-08 | Kubernetes: kind locally, and in production on hardware agreed with the university | Planned — phase 6 (#59) |
| INFRA-09 | End-to-end journeys per profile, proving the seams between services | Planned — phase 4 (#57) |

### 3.9 Administration portal (PORTAL)

| # | Requirement | State |
|---|---|---|
| PORTAL-01 | CRUD over KApp's own data — accounts, roles, the map, visitor passes — with a `409` shown with its reason | Built |
| PORTAL-02 | An API console driven by the OpenAPI specs themselves | Built |
| PORTAL-03 | A role inspector showing what each role may reach | Built |
| PORTAL-04 | Bilingual, Spanish first | Built |
| PORTAL-05 | Sign-in with Microsoft; only members with a permission enter | In progress — phase 3 (#56) |
| PORTAL-06 | It never shows students' academic data | Built |

---

## 4. Non-functional requirements

| # | Requirement |
|---|---|
| NFR-01 | Every service validates the token itself. Identity never comes from a header the gateway sets |
| NFR-02 | Every endpoint carries an explicit authorization rule, asserted per role in tests — including every combination that must be refused |
| NFR-03 | An authenticated caller lacking the role gets `403`, never `404` — except where a `404` deliberately avoids confirming that a resource exists |
| NFR-04 | Errors share one envelope, with field-level details a client can map to a form |
| NFR-05 | **Read SINU, never copy it.** Academic data is read, cached for minutes at most, and never stored |
| NFR-06 | **No member password in production.** Members sign in with Microsoft |
| NFR-07 | TLS before anything is reachable from outside. iOS blocks plaintext HTTP and Android has since API 28 |
| NFR-08 | Personal data has a stated purpose, a retention period and, where it applies, automatic deletion — see §6 |
| NFR-09 | Spanish is the language of everything a student reads |
| NFR-10 | Photos are resized and stripped of location metadata before they are stored |

---

## 5. Constraints

- **SINU access is the university's IT director's decision.** The test SINU adapter stands in until then, and for
  the presentation if access never comes.
- **An Entra ID application registration** from the university is needed for institutional sign-in. Development uses
  a test tenant (#62) and a fake identity server.
- **Dates.** The thesis proposal is handed in at the end of the 2026-2 term; the product is presented in 2027-2.
- **A small team, part time.** Backend: Brian. Android: Iván, Alejandro and Santiago. iOS: Brian.

---

## 6. Personal data

KApp keeps as little personal data as it can, under **Ley 1581 de 2012** (habeas data):

| Data | Purpose | Retention |
|---|---|---|
| Member profile: names, picture, roles | Showing who a member is in the community features | While the account exists |
| Identity document, phone or student code of members | — | **Never stored** |
| Visitor's identity document | Reception knowing who entered with a pass. Readable only under `/auth/admin/visitor-passes` | **Deleted automatically after 30 days** by a MongoDB TTL index |
| Author of a voice post or a report | Moderation acting on content that is not anonymous | With the post |
| Lost-and-found history | Keeping every object handed in and claimed traceable | Never deleted |
| Photos in damage reports | Showing the damage | With the report |

The IT director has not yet been told that KApp stores visitors' identity documents. Tracked as S12 in
`SECURITY-AUDIT.md`.

---

## 7. Explicitly out of scope

Not "pending". Not being built.

| Item | Why |
|---|---|
| Anything that writes to SINU, or copies it | KApp complements SINU; it is not a second copy of it |
| Editing grades, enrolment (matrícula) | They belong to SINU |
| Payments, personal documents, chat, library loans | They belong to other systems, or are not KApp's to hold |
| A manual timetable | The timetable is SINU's; a student edits only their plans |
| Administrators reading students' academic data | The portal manages KApp's own data |
| `course-service`, `assignment-service` | Deleted in October 2026 (#65); git keeps them |
| The 2025 HTML prototype | Deleted in October 2026 (#51). A better public demo is #92 |

## 8. Deferred

Known, deliberately postponed, and recorded so it reads as a decision rather than an oversight.

| Item | Note |
|---|---|
| Distributed rate limiting | Needs a shared store. **Revisit before running a second gateway instance** |
| Sharing a semáforo | The client draws it and exports an image; no backend involved |
| Events, clubs, recreation bookings, wellbeing services | After the questionnaire to the offices |
