<a id="top"></a>

<table width="100%" style="border: none; background-color: transparent;">
  <tr style="border: none; background-color: transparent;">
    <td align="center" width="20%" style="border: none; padding: 0;">
      <img src="./assets/KForge-Yellow-Logo.png" alt="K-Forge Logo" width="100%" style="max-width: 180px; border-radius: 10px;" />
    </td>
    <td align="center" width="80%" style="border: none; padding: 0;">
      <img src="./assets/project-banner.svg" alt="KApp Banner" width="100%" />
    </td>
  </tr>
</table>

<p align="center"><strong>University mobile app for Fundación Universitaria Konrad Lorenz. Native Android (Kotlin) and iOS (Swift) clients, powered by a Spring Boot microservices backend on MongoDB, with RS256 tokens verified by every service.</strong></p>

<p align="center">
  <a href="https://github.com/K-Forge/KApp/actions/workflows/ci.yml"><img src="https://github.com/K-Forge/KApp/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"/></a>
  &nbsp;
  <a href="https://kapp-black.vercel.app"><img src="https://img.shields.io/badge/Demo-frozen%20prototype-6B7280?logo=vercel&logoColor=white" alt="Demo of the frozen prototype"/></a>
  <br/><br/>
  <img src="https://img.shields.io/badge/Android-Kotlin-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android (Kotlin)"/>
  <img src="https://img.shields.io/badge/iOS-Swift-F05138?style=for-the-badge&logo=swift&logoColor=white" alt="iOS (Swift)"/>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.5"/>
  <img src="https://img.shields.io/badge/Spring%20Cloud-2025.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Cloud 2025.0"/>
  <img src="https://img.shields.io/badge/MongoDB-Atlas%208-47A248?style=for-the-badge&logo=mongodb&logoColor=white" alt="MongoDB Atlas 8"/>
  <img src="https://img.shields.io/badge/Tests-691%20backend%20%C2%B7%2059%20portal-0EA5E9?style=for-the-badge" alt="691 backend and 59 portal tests"/>
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
  <img src="https://img.shields.io/badge/Backend-MVP%20complete-22C55E?style=for-the-badge" alt="Backend MVP complete"/>
  <img src="https://img.shields.io/badge/Clients-not%20started-EAB308?style=for-the-badge" alt="Native clients not started"/>
  <img src="https://img.shields.io/badge/License-Internal%20use-8B5CF6?style=for-the-badge" alt="Internal use license"/>
</p>

---

## Table of Contents

- [Overview](#overview)
- [Project Status](#project-status)
- [Interface](#interface)
- [System Architecture](#system-architecture)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Documentation](#documentation)
- [Security](#security)
- [Contributing](#contributing)
- [Contributors](#contributors)
- [License](#license)

---

## Overview

KApp is the **university mobile application** for the Fundación Universitaria Konrad Lorenz community, developed by
the K-Forge development club. It is a thesis project, and its scope was narrowed deliberately in August 2026 for a
reason worth stating plainly: **the university's academic data is not available**, so the product cannot depend on
it. Accounts are created inside KApp, and the MVP is four things a student uses day to day — **identity and
profile, the campus map, a customisable preloaded timetable, and a customisable preloaded career semáforo**.
Courses, assignments and grading are explicitly out of scope; see [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).

The clients are thin. Everything they consume lives **server-side**: six independent Spring Boot services register
with a Eureka discovery server and are reached through a single API Gateway. **Every service validates the access
token itself** against a published JWKS rather than trusting a header the gateway sets, and each holds its own
MongoDB account with `readWrite` on exactly one database — so the separation between services is enforced by the
engine, not merely respected by the code.

Delivery goes **straight to mobile**. There is a web portal, but it is an administration and development console
for the team — not a product surface. The mobile clients are unblocked: the OpenAPI contracts are served as
Prism mocks, so Kotlin and Swift work does not wait on the backend.

---

## Project Status

KApp is a **degree thesis project**, due **November 2026**, built by six people in their spare time.

What that means when reading this repository:

- **The backend is built, tested and ready for the clients to build against.** Five services,
  **691 integration tests** on Testcontainers, from a repository that had none in August. Every
  service asserts its full role-by-endpoint authorization matrix — including every combination that
  must be refused, which are the ones that matter. The five OpenAPI contracts are the interface and
  they are stable: build against them.
- **The native clients are the product, and they have not been started.** They are unblocked: the
  OpenAPI contracts are served as Prism mocks, so Kotlin and Swift work does not wait on the
  backend.
- **The scope was narrowed deliberately** in August 2026, because the university's academic data is
  not available. Courses, assignments and grading are out — not pending.
  [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) says what is in and what is out, with nothing left
  implicit.
- **There is no production deployment.** It runs on the lead developer's machine until university
  hardware exists, and it has not been hardened for a public network. See [Security](#security).
- **The development database is a shared Atlas cluster, and the only one.** It holds the five
  databases, one per service with its own account, so the whole team works against the same data
  and nobody installs a server. There is no local MongoDB to fall back to — the stack needs the
  network ([ADR 0009](docs/adr/0009-atlas-is-the-only-development-database.md));
  [`docs/ONBOARDING.md`](docs/ONBOARDING.md) is the setup a teammate follows.
- **What is still blocked, and on whom**, is listed in [`docs/PROGRESS.md`](docs/PROGRESS.md) — an
  SMTP relay, an Entra ID application registration, the floor sketches, and the institutional codes
  the published pensums do not print.
- The original Spring Boot monolith was removed once the migration completed. It remains retrievable
  from the git history; `app/backend/microservices/` is the only backend.

---

## Interface

**The product's screens do not exist yet.** The Android and iOS clients are the deliverable and they
have not been built; showing mockups here as if they were shipped would be the wrong impression to
leave. What exists today are the two tools the team uses to build it.

### The floor editor

This is how a floor of the campus gets captured: walk it, count the squares, mark the lifts and
stairs, trace the corridors in the colour they are actually painted, place the rooms. One
self-contained HTML file — no server, no network, no build.

It matters more than a tool usually would. The map used to be modelled as a photograph of an
architectural plan, and obtaining those plans depended on other people's calendars — it was the
project's longest-lead item and the one most likely to slip before November. A schematic floor is
captured in an afternoon by the people who need it.
[ADR 0006](docs/adr/0006-schematic-map-not-floor-plan-images.md) records the trade.

<p align="center">
  <img src="./assets/screenshots/06-grid-editor.png" alt="The floor editor, showing floor 3 of Bloque A" width="100%"/>
  <br/>
  <sub>Floor 3 of Bloque A, loaded from the seed. The three <b>301</b> rooms — north, central and
  south — are three different rooms sharing one base code, which is the case most likely to send a
  student to the wrong door. Corridors run in the colours the wings are painted; the auditorium
  spans several cells. The editor refuses a room that would not fit or that would overlap another,
  so a mistake surfaces while somebody is still standing in the building.</sub>
</p>

```bash
open app/backend/microservices/map-service/src/main/resources/static/admin/grid-editor.html
```

### The admin and developer console

An Angular portal at `localhost:4300`: CRUD over programs, pensums, users, buildings, spaces,
invitation codes and visitor passes; a paste-and-correct importer that turns a pensum PDF into a
catalogue entry; an API console driven by the OpenAPI specs themselves, which explains every field
of a response from the contract; and a role inspector showing what each role may reach. It is a **team console, not a
product surface** — nothing a student ever sees.

```bash
cd app/backend/microservices && docker compose --profile core --profile dev up -d
```

### The frozen prototype

An earlier plain HTML/JS client is still in the tree at `app/frontend/web/`, and the **[live
demo](https://kapp-black.vercel.app)** runs it. It shows courses, assignments and grading — none of
which is being built any more. It is kept as the interface study it was, not as a description of the
product; see [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) for what is and is not in scope.

---

## System Architecture

The mobile clients run on the user's device; every service runs server-side. **The gateway is the only reachable
component** — service ports are deliberately unpublished — and service locations are resolved through Eureka rather
than hardcoded.

<p align="center">
  <img src="./assets/architecture.svg" alt="Three clients call one API gateway, which routes to five services, each owning one database. Eureka, the common library and MongoDB are shared by all five. Grades, enrolment, payments and an institutional production deployment are out of scope." width="100%"/>
</p>

Two properties are worth stating in words, because a box does not carry them:

**No service trusts the gateway.** Each one validates the access token itself against the published
JWKS, so a request that somehow reached a service directly is refused by that service rather than
by the boundary it went around — that gap was security finding S1.

**One MongoDB account per service, with `readWrite` on exactly one database.** The separation is
enforced by the engine rather than respected by convention, and `scripts/verify-db-isolation.sh`
asserts it by connecting with each account against every database
([ADR 0005](docs/adr/0005-per-service-database-credentials.md)).

---

## Key Features

- **Single entry point.** All client traffic goes through the gateway; routes are declared explicitly and resolved
  by service id (`lb://user-service`). Eureka's discovery locator is **off**, so registering a service does not
  silently publish it.
- **Per-service token validation.** Every service is an OAuth2 resource server verifying RS256 against a published
  JWKS. Identity comes from the signed token, never from a header.
- **Authorization asserted, not assumed.** Every endpoint carries an explicit rule, and each service's full
  role-by-endpoint matrix is asserted in tests — **including every combination that must be refused**, which are the
  ones that matter.
- **Database isolation the engine enforces.** One database *and one account* per service, each with `readWrite` on
  exactly one. `scripts/verify-db-isolation.sh` proves it in 25 checks.
- **Contract-first.** Five hand-written OpenAPI 3.1 specs, linted in CI and served as Prism mocks, so the mobile
  clients are never blocked on the backend.
- **Versioned migrations.** Mongock change units are ordered, audited and lock-protected, so several developers and
  CI can point at one database without racing.
- **691 integration tests** on Testcontainers, from a repository that had none in August.
- **A campus map drawn from data.** Floors are grids, not photographs — which removed the project's
  longest-lead dependency, since a schematic floor is captured by walking it.

---

## Tech Stack

| Technology | Role | Why this one |
| --- | --- | --- |
| Kotlin (Android) | Primary client | The product's main delivery target. |
| Swift (iOS) | Primary client | Consumes the same gateway API as Android. |
| Java 21 | Backend language | Long-term support release. |
| Spring Boot **3.5** | Service runtime | **Not Boot 4**: Mongock publishes no Boot 4 artifact. |
| Spring Cloud **2025.0** | Distributed layer | The release train for Boot 3.5; 2025.1.x targets Boot 4. |
| Spring Cloud Gateway | Edge routing | One entry point, with an explicit routing table rather than discovery-based exposure. |
| Netflix Eureka | Service discovery | Services are addressed by logical name, not host and port. |
| Spring Security 6.5 (OAuth2 resource server) | Authorization | **RS256** with a published JWKS. A shared symmetric secret handed to six services is six places that can mint an admin token — and Entra ID signs RS256, so that migration becomes a property change. |
| OpenFeign | Inter-service calls | Three edges only, all one-directional. |
| Spring Data MongoDB | Persistence | Document-shaped aggregates with a single writer each. See [ADR 0001](docs/adr/0001-mongodb-over-postgresql.md). |
| **Mongock 5.5.1** | Migrations | Versioned, ordered, audited change units with a distributed lock — which the project previously had none of. |
| MongoDB Atlas 8 | Database | One engine, not polyglot ([ADR 0004](docs/adr/0004-one-database-engine-not-polyglot.md)), and one cluster, shared, with no local alternative ([ADR 0009](docs/adr/0009-atlas-is-the-only-development-database.md)). The tests are the exception: Testcontainers starts them their own, because a test run has to be free to wipe its data. |
| Testcontainers | Testing | Real MongoDB per suite; no in-memory substitute pretending to be a database. |
| OpenAPI 3.1 + Prism | Contracts | Hand-written, linted in CI, served as mocks so client work never waits. |
| Maven (multi-module) | Build | The parent POM centralises every version, so parallel branches never edit it. |
| Docker + Docker Compose | Containerisation | Profiles (`core`, `academic`, `map`, `full`, `dev`, `cloud`) so a laptop runs only what is needed. |
| Angular 22 + Vitest | Admin portal | A team console, not a product surface. |
| pnpm | Tooling | Repository tooling and the portal's dependencies. |

---

## Getting Started

**Docker Desktop and pnpm are both required**, and Docker has to be *running*, not just installed —
every service, the database and even the tests' own database live in containers. Nothing else is:
there is no MongoDB to install, no Maven (the repository ships `./mvnw`) and no JDK unless you
intend to build outside Docker.

**The same commands work on Windows and macOS.** They are single `docker compose` invocations with
no shell syntax in them, so it makes no difference which shell pnpm hands them to.

```bash
git clone https://github.com/K-Forge/KApp.git
cd KApp
corepack enable && pnpm install

# Secrets are generated on your machine, never copied from an example: a password that
# passes through a chat or a commit stays in that history forever.
cd app/backend/microservices && ../../../scripts/generate-dev-secrets.sh > .env && cd -

pnpm run microservices:start     # the five services, the gateway and the portal
pnpm run microservices:status    # what came up
```

There is nothing to initialise afterwards. Mongock creates every index and loads the seed data —
pensums, buildings, spaces, invitation codes — the first time a service starts.

The profiles, what listens on which port, the development accounts, and what to do when something
will not start are all in
**[`docs/RUNBOOK.md`](docs/RUNBOOK.md)**. It is the operational document; this section is only the
shortest path to a running stack.

### Available scripts

| Command                         | Description                                                               |
| ------------------------------- | ------------------------------------------------------------------------- |
| `pnpm run microservices:start`  | Starts the five services, the gateway and the admin portal.               |
| `pnpm run microservices:cloud`  | Starts them against the shared Atlas cluster, plus the admin portal.      |
| `pnpm run microservices:mock`   | Prism mocks only — no JVM, no database. For client work.                  |
| `pnpm run microservices:status` | Shows what is running.                                                    |
| `pnpm run microservices:logs`   | Follows the logs. Add `-- <service>` for one of them.                     |
| `pnpm run microservices:stop`   | Stops everything, mocks and portal included.                              |
| `pnpm run prototype:serve`      | Serves the frozen HTML prototype in `app/frontend/web` on port 3000.      |

The admin portal has its own scripts, in `app/frontend/web-admin/package.json`, because they are
its build and not the repository's: `pnpm start`, `pnpm build`, `pnpm test`.

These are thin wrappers around `docker compose`, and deliberately so: **they work the same on
Windows and macOS**. They pass the compose file by path rather than changing directory first,
because `pnpm` runs scripts through `cmd.exe` on Windows and `sh` elsewhere, and the two do not
agree about `cd` with forward slashes. Nothing in them is shell-specific.

### Building a client against this

The backend is ready to be built against. Two ways in, and the choice is about what you are doing
rather than which is better:

**Against the contracts.** `docker compose --profile mock up -d` serves the five OpenAPI specs as
Prism mocks. Every endpoint answers with the examples in the contract, immediately, with no
database and no sign-in. This is the one to use while a screen is being laid out.

**Against the real services.** `docker compose --profile core up -d` starts the
services against the shared Atlas cluster — real data, real tokens, real 403s. Ask Brian for the
`.env`; [`docs/ONBOARDING.md`](docs/ONBOARDING.md) is the walkthrough.

Either way the interface is the same and it is in [`docs/api/`](docs/api/), not in this README:

- **Everything goes through the gateway on `:8080`.** Service ports are unpublished on purpose.
- **`POST /auth/login` returns an RS256 access token**; send it as `Authorization: Bearer <token>`.
  It lasts an hour, there is no refresh endpoint yet, and a `401` means "send the user back to
  sign-in" — see the Auth contract, which says so in the spec rather than leaving you to find out.
- **Every error has the same shape**: `timestamp`, `status`, `error`, `message`, `path`, and
  `details` when a field is at fault. Bind your form errors to `details`; show `message` to a person.
- **`x-roles` on every operation** says which roles may call it, written from what the services
  actually enforce. The portal's "Who can do what" screen renders the same data if you would rather
  read it as a table.

### Demo mode

The backend is not hosted anywhere, so a plain static deployment of the web client would show a login screen that
can never authenticate. [`app/frontend/web/js/demo.js`](app/frontend/web/js/demo.js) closes that gap: it intercepts
the API calls and answers them with sample data, letting a visitor sign in with any credentials and walk through the
student, professor and administrator views.

It activates **only when the client is served from a host other than `localhost`**, so local development keeps
talking to the real microservices and nothing about the normal workflow changes. To exercise it locally, append
`?demo=1` (and `?demo=0` to leave it):

```bash
pnpm run prototype:serve   # then open http://localhost:3000/login.html?demo=1
```

A persistent banner marks every screen as demo data, and offers a role switcher so the administrator panel is
reachable without credentials.

### Deploying the web demo

[`vercel.json`](vercel.json) configures the repository as a static deployment of `app/frontend/web`, with no build
step and a baseline set of security headers. With the Vercel CLI authenticated:

```bash
vercel link
```

```bash
vercel deploy --prod
```

Linking the repository from the Vercel dashboard works as well; the configuration file is picked up automatically,
and the framework preset should be left as "Other".

---

## Project Structure

```
KApp/
├── app/
│   ├── backend/
│   │   ├── microservices/            # The backend — Maven multi-module project
│   │   │   ├── discovery-server/     # Eureka registry (:8761)
│   │   │   ├── api-gateway/          # Routing, CORS, rate limiting (:8080) — the only open port
│   │   │   ├── auth-service/         # Credentials, RS256 tokens, JWKS, invitation codes,
│   │   │   │                         #   visitor passes (:8081)
│   │   │   ├── user-service/         # Profiles and directory search (:8082)
│   │   │   ├── semaphore-service/    # Catalogue, student progress, academic plans (:8083)
│   │   │   ├── schedule-service/     # Enrolments, meetings, agenda (:8084)
│   │   │   ├── map-service/          # Buildings, floors, spaces, search (:8085)
│   │   │   │   └── src/main/resources/static/admin/grid-editor.html   # The floor editor
│   │   │   ├── common/               # Error envelope, CurrentUser, role constants
│   │   │   ├── course-service/       # FROZEN — out of the reactor, compose and CI
│   │   │   ├── assignment-service/   # FROZEN — same
│   │   │   ├── docker-compose.yml    # Profiles: mock, core, academic, map, full, dev
│   │   │   └── pom.xml               # Parent POM — every version lives here
│   │   └── postman/                  # API collections
│   ├── frontend/
│   │   ├── web-admin/                # Admin and developer console (Angular 22)
│   │   ├── web/                      # FROZEN prototype — the live demo runs this
│   │   └── mobile/
│   │       ├── kotlin/               # Android client — the product, not started
│   │       └── swift/                # iOS client — the product, not started
│   └── database/init.sql             # Legacy PostgreSQL schema. Reference only; nothing reads it
├── docs/
│   ├── api/                          # Five OpenAPI 3.1 contracts — the source of truth
│   ├── adr/                          # Architecture decision records
│   ├── pensums/                      # The published plans: transcriptions, extraction tools, seed builder
│   ├── templates/                    # The pensum import CSV and its column reference
│   ├── PROGRESS.md                   # What is built, and what is blocked on whom
│   ├── RUNBOOK.md                    # How to start, stop and troubleshoot it
│   ├── SECURITY-AUDIT.md             # Findings S1–S12 and what closed each
│   ├── REQUIREMENTS.md · DESIGN.md   # Scope and architecture
│   └── ATLAS-SETUP.md                # Standing up the shared development cluster
├── scripts/
│   ├── generate-dev-secrets.sh       # Writes .env — secrets never leave the machine
│   ├── create-dev-accounts.sh        # The four team accounts
│   ├── verify-db-isolation.sh        # Proves each service reaches its own database and no other
│   └── verify-visitor-pass.py        # End-to-end check of the day pass through the gateway
├── .github/workflows/ci.yml          # Backend, contracts and portal
├── AGENTS.md                         # Operational context for AI agents
└── package.json                      # Repository tooling
```

---

## Documentation

**Start here** depending on what you came for:

| Document | Content |
| --- | --- |
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | **Start here on a new machine.** Ten minutes, in Spanish, no Java or MongoDB to install. |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | **How to run it.** Profiles, accounts, and a troubleshooting section where every entry is a failure we actually hit. |
| [docs/PROGRESS.md](docs/PROGRESS.md) | **What is built**, what each phase delivered, and what is blocked on whom. |
| [docs/api/](docs/api/) | The OpenAPI 3.1 contracts, one per service. **The source of truth** — linted in CI and served as mocks. |
| [docs/pensums/](docs/pensums/README.md) | The 23 published plans of study: how each PDF was read, what was loaded, and what the PDFs could not answer. |
| [docs/adr/](docs/adr/) | Architecture decision records: what was decided, what else was considered, and the consequences including the bad ones. |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | Scope. Every entry is built or explicitly out — no pending requirements nobody intends to implement. |
| [docs/DESIGN.md](docs/DESIGN.md) | The system as it is, with diagrams. |
| [docs/SECURITY-AUDIT.md](docs/SECURITY-AUDIT.md) | Findings S1–S12, what closed each, and what must happen before this is reachable from outside. |
| [docs/ATLAS-SETUP.md](docs/ATLAS-SETUP.md) | Standing up the shared development cluster. |
| [docs/templates/](docs/templates/) | The pensum import CSV and its column reference. |
| [docs/INTEGRATION-NOTES.md](docs/INTEGRATION-NOTES.md) | Findings worth carrying forward — the kind that cost a day to learn. |
| [docs/SRS.md](docs/SRS.md) | The original software requirements specification. Predates the August narrowing. |
| [docs/MICROSERVICES-IDEAS.md](docs/MICROSERVICES-IDEAS.md) | Service decomposition analysis. Ideas, not commitments. |
| [docs/DOCKER-GUIDE.md](docs/DOCKER-GUIDE.md) · [docs/K-COLORS.md](docs/K-COLORS.md) | Container guide; brand palette. |
| [docs/researches/](docs/researches/) | Academic article reviews on university mobile apps and student engagement. |
| [AGENTS.md](AGENTS.md) | Repository context and rules for AI agents. |

---

## Security

**Nothing here is deployed**: no server, no hosted environment, no user data. It runs on one laptop.

The prototype was audited against itself in August and the findings written down, with severity, evidence at file
and line, and what each needed: [docs/SECURITY-AUDIT.md](docs/SECURITY-AUDIT.md). **That description used to say
role-based authorization was not enforced, CORS was permissive, and services trusted an identity header set by the
gateway. All three were findings, and all three are closed** — every service now validates the token itself, every
endpoint carries an explicit rule asserted per role in tests, and the CORS wildcard is an allow-list.

Three findings remain open, deliberately, and each says what has to happen before KApp is reachable from outside a
laptop:

- **S7** — tokens simply expire after an hour; there is no refresh and no revocation.
- **S11** — the four development accounts hold `ROLE_ADMIN`, and the two seeded invitation codes ship in this
  repository. Both are correct while this runs on one machine and **must be revoked before it does not**.
- **H1** — two development credentials from the deleted monolith remain readable in the git history.

Secrets are generated per machine by `scripts/generate-dev-secrets.sh` and never committed; the working tree carries
no credential literal.

KApp stores one piece of personal data: the identity document a visitor presents for a day pass. It is readable only
by an administrator and **deleted automatically after 30 days** by a database TTL index —
[ADR 0007](docs/adr/0007-visitor-day-pass-instead-of-guest-accounts.md) records the Ley 1581 obligations and how each
is met.

To report a vulnerability, follow the security policy published by the
[K-Forge organization](https://github.com/K-Forge) or write to kforge.dev@gmail.com. Please do not open a public
issue for security reports.

---

## Contributing

Maintenance of this codebase is restricted to authorized members of K-Forge and the Fundación Universitaria Konrad
Lorenz. External pull requests are not accepted.

Contribution guidelines, issue templates and the security policy are maintained at the organization level in
[K-Forge/.github](https://github.com/K-Forge) and apply to this repository.

Repository-specific rules for authorized members:

- Branch naming follows `feature/*` and `bugfix/*`; commits follow the Conventional Commits specification.
- Any schema change must be reflected in `app/database/init.sql`.
- Backend work targets `app/backend/microservices/`. The web client under `app/frontend/web/` is the test surface for
  that API and the reference design for the future Kotlin and Swift clients: keep its screens in sync with what the
  mobile apps are meant to deliver.

---

## Contributors

Thanks to the club members who forge and drive this software project.

<table>
  <tr>
    <td align="center"><a href="https://github.com/13rianVargas"><img src="https://github.com/13rianVargas.png" width="100px;" alt="13rianVargas"/><br /><sub><b>Brian Vargas</b></sub></a></td>
    <td align="center"><a href="https://github.com/JulianAvila259"><img src="https://github.com/JulianAvila259.png" width="100px;" alt="JulianAvila259"/><br /><sub><b>Julian Avila </b></sub></a></td>
    <td align="center"><a href="https://github.com/SantiagoRR17"><img src="https://github.com/SantiagoRR17.png" width="100px;" alt="SantiagoRR17"/><br /><sub><b>Santiago Rocha</b></sub></a></td>
    <td align="center"><a href="https://github.com/DIEGO-ALI"><img src="https://github.com/DIEGO-ALI.png" width="100px;" alt="DIEGO-ALI"/><br /><sub><b>Diego Lares</b></sub></a></td>
  </tr>
</table>

---

## License

This repository is **source-available, not open source**. The source code is public for reading, study and technical
evaluation; it is not licensed for reuse.

Use, modification and redistribution are restricted under the [Internal Use License](LICENSE), which limits the
software to authorized members of the Fundación Universitaria Konrad Lorenz and the K-Forge development club.
The Spanish text of `LICENSE` is the binding version.

© 2025-2026 K-Forge Developers. All rights reserved.

---

<div align="center">
  <br>
  <a href="https://github.com/K-Forge">
    <img src="https://img.shields.io/badge/GitHub-K--Forge-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub"/>
  </a>
  &nbsp;
  <a href="https://kforge.vercel.app">
    <img src="https://img.shields.io/badge/Web-kforge.vercel.app-EAB308?style=for-the-badge&logo=vercel&logoColor=white" alt="Web"/>
  </a>
  &nbsp;
  <a href="mailto:kforge.dev@gmail.com">
    <img src="https://img.shields.io/badge/Email-kforge.dev-EA4335?style=for-the-badge&logo=gmail&logoColor=white" alt="Email"/>
  </a>
  <br><br>
  <sub>Forged by <a href="https://github.com/K-Forge"><strong>K-Forge</strong></a> — development club of Fundación Universitaria Konrad Lorenz</sub>
  <br><br>
  <a href="#top">
    <img src="https://img.shields.io/badge/%E2%96%B2_Back_to_top-EAB308?style=flat-square" alt="Back to top"/>
  </a>
  <br><br>
  <img src="https://capsule-render.vercel.app/api?type=waving&height=100&color=0:000000,100:EAB308&section=footer" width="100%"/>
</div>
