# KApp · Agent Context

> Operational context and rules for AI agents working in this repository.

---

## Project Overview

**KApp** is the **mobile application** for the Fundación Universitaria Konrad Lorenz community. The product vision is mobile-first: native Android (Kotlin) and iOS (Swift) clients that give students and staff access to academic management — courses, assignments, users, and authentication — from their phones.

The mobile clients are powered by a **Spring Boot microservices backend** behind a single gateway. Delivery is backend first, then mobile directly: the clients build against the hand-written OpenAPI contracts in `docs/api/`, served as Prism mocks, so client and server progress in parallel rather than in sequence. The old HTML/JS client was a prototype of the mobile layout and is frozen.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Mobile (Android) | Kotlin + Jetpack Compose — the product, in progress |
| Mobile (iOS) | Swift + SwiftUI — the product, in progress |
| Admin portal | Angular — `app/frontend/web-admin/`, compose `dev` profile |
| Backend | Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3 |
| Service discovery | Netflix Eureka (`:8761`) |
| API Gateway | Spring Cloud Gateway (`:8080`) |
| Security | Spring Security, RS256 JWT with a published JWKS, BCrypt |
| IPC | OpenFeign (service-to-service REST) |
| Resilience | Resilience4j Circuit Breaker |
| Persistence | Spring Data MongoDB |
| Database | MongoDB 7+ (single-node replica set locally) |
| Migrations | Mongock (versioned change units, distributed lock) |
| API contract | Hand-written OpenAPI 3.1 in `docs/api/`, served as Prism mocks |
| Testing | JUnit 5 + Testcontainers |
| Build | Maven multi-module |
| Containers | Docker + Docker Compose |
| Package manager | pnpm (install) + Bun (scripts) |

---

## Microservices

| Service | Port | Owns |
|---------|------|------|
| Discovery Server | 8761 | Eureka registry |
| API Gateway | 8080 | Routing, CORS, rate limiting. **No authentication logic** |
| Auth Service | 8081 | Credentials, RS256 signing, JWKS |
| User Service | 8082 | Profiles |
| Semaphore Service | 8083 | Academic catalogue, curricula, student progress |
| Schedule Service | 8084 | Student timetables |
| Map Service | 8085 | Buildings, floors, spaces |
| Common Library | — | Security and error auto-configuration |

All under `app/backend/microservices/`.

**Frozen, not deleted:** `course-service` and `assignment-service` remain in the tree but are
out of the reactor, the compose file and CI. They target PostgreSQL/JPA and are outside the MVP.
Do not build on them and do not re-add them to `<modules>`.

---

## Repository Structure

```text
KApp/
├── app/
│   ├── backend/
│   │   ├── microservices/           # Backend — the only application code
│   │   │   ├── pom.xml              # Parent POM (multi-module)
│   │   │   ├── docker-compose.yml
│   │   │   ├── discovery-server/    api-gateway/    common/
│   │   │   ├── auth-service/  user-service/  semaphore-service/
│   │   │   ├── schedule-service/  map-service/
│   │   │   ├── course-service/      # FROZEN, not in the reactor
│   │   │   └── assignment-service/  # FROZEN, not in the reactor
│   │   └── postman/                 # Postman collections
│   ├── frontend/
│   │   ├── web-admin/               # Admin and developer portal (Angular)
│   │   ├── web/                     # FROZEN prototype of the mobile layout
│   │   └── mobile/
│   │       ├── kotlin/              # Android
│   │       └── swift/               # iOS
│   └── database/
│       └── init.sql                 # LEGACY PostgreSQL schema, reference only
├── docs/
│   ├── api/                         # OpenAPI contracts — the source of truth
│   ├── adr/                         # Architecture decisions and their reasons
│   ├── RUNBOOK.md                   # How to run everything — start here
│   ├── PROGRESS.md                  # Implementation status — read before large changes
│   ├── INTEGRATION-NOTES.md         # Cross-cutting findings worth carrying forward
│   ├── SECURITY-AUDIT.md  SRS.md  REQUIREMENTS.md  DESIGN.md  K-COLORS.md
│   └── DOCKER-GUIDE.md              # Superseded by RUNBOOK.md
├── scripts/
│   ├── check-git-conventions.sh     # The git rules as code: CI and the commit-msg hook run it
│   ├── install-git-hooks.sh         # Once per clone: enables .githooks/
│   ├── sync-contributing.sh         # Refreshes CONTRIBUTING.md from the organization
│   ├── generate-dev-secrets.sh  create-dev-accounts.sh  verify-db-isolation.sh
│   └── start-frontend.sh
├── .githooks/commit-msg             # Refuses a commit that breaks CONTRIBUTING.md
├── .github/
│   ├── CODEOWNERS                   # The lead reviews every path
│   └── workflows/
│       ├── ci.yml                   # Backend, contracts and portal
│       └── conventions.yml          # Branch, title and commits of every pull request
├── CONTRIBUTING.md                  # Verbatim copy of the K-Forge guide. Never edit here
└── package.json
```

---

## Dev Commands

Full guide, including profiles, local accounts and troubleshooting:
**[docs/RUNBOOK.md](docs/RUNBOOK.md)**.

Requires JDK 21 and Docker. The repository pins the JDK with `.java-version` (jenv) and ships a
Maven wrapper, so use `./mvnw` rather than a system Maven.

```bash
cd app/backend/microservices

# Build and run every test. Tests use Testcontainers, so Docker must be running.
./mvnw -B verify

# One service only
./mvnw -B -pl map-service -am verify
```

Compose profiles exist so nobody has to run seven JVMs to work on one service:

```bash
cd app/backend/microservices

# Mobile / frontend work: the Prism mocks alone. No JVM, no Mongo, ~200 MB.
docker compose --profile mock up -d      # ports 4010-4014

# Backend work: only what you need
docker compose --profile core up -d      # mongo, discovery, gateway, auth, user
docker compose --profile map up -d       # core + map-service
docker compose --profile academic up -d  # core + semaphore + schedule
docker compose --profile full up -d      # everything

docker compose --profile full down
```

Only the gateway (8080) is published. Reaching a service directly is meant to fail.

```bash
# Validate the API contracts the mobile clients build against
npx --package=@redocly/cli@latest redocly lint docs/api/*.yaml
```

---

## Conventions

### Java

- Use Lombok to reduce boilerplate.
- **Each service owns its own DTOs.** `common` holds only cross-cutting concerns: security
  auto-configuration, the `ApiError` envelope, domain exception types and `AcademicPeriod`.
  Shared DTOs were removed deliberately — they couple the services into a distributed monolith
  and make `common` a merge-conflict hotspot across parallel worktrees.
- Roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`. A guest is someone with no
  university account; they may read the campus map and nothing else.

### Security

- **Every service validates the token itself.** Each is an OAuth2 resource server checking RS256
  against `auth-service`'s JWKS. Bypassing the gateway therefore gains nothing — this is what
  closed finding S1. Never reintroduce identity headers such as `X-User-Email`.
- Configure with `jwk-set-uri`, **never** `issuer-uri`: the latter performs OIDC discovery at bean
  creation, so a service refuses to start unless auth-service is already up — a boot-order
  deadlock under `docker compose up`.
- Read identity through `CurrentUser` from `common`, never from a request header.
- The `roles` claim carries prefixed names (`ROLE_STUDENT`), so the authority prefix is empty.
  Adding `ROLE_` again yields `ROLE_ROLE_STUDENT` and every `hasRole` check fails silently.
- `POST /internal/**` is authenticated with a shared `X-Internal-Token` and has no gateway route.
- Never expose signing keys or DB credentials. Use environment variables.

### Database

- MongoDB, one database per service on one instance. Run it as a **single-node replica set**:
  a standalone `mongod` cannot do multi-document transactions and fails at runtime, not startup.
- **Schema changes go in Mongock change units**, never applied by hand. They are append-only:
  never edit one that has run, add a new one.
- Every service that touches MongoDB must carry `@EnableMongock`. Mongock 5.5.1 ships no
  auto-configuration, so without it migrations are skipped in silence.
- Academic periods use the university's own format, `YYYYS` (e.g. `20262`). Validate with
  `@ValidAcademicPeriod` from `common`.
- `app/database/init.sql` is the legacy PostgreSQL schema, kept for reference only.

### Inter-Service Communication

Three Feign edges. Keep the number small: every extra one is a new failure mode and a new
reason for a service to be unable to answer on its own.

```
Auth Service      → (Feign) → User Service       # create a profile at registration
Semaphore Service → (Feign) → User Service       # resolve the student's programme
Schedule Service  → (Feign) → Semaphore Service  # course catalogue, cached 1h
```

**None of them sits on a hot path**, which is the property that makes three acceptable. The first
two fire once per account; the third reads near-static reference data through a cache, so
`schedule-service` keeps working while `semaphore-service` is briefly down.

Services are discovered by name via Eureka. The caller's token is propagated automatically by
`common`; registration is the exception, since no user token exists yet — that call carries a
shared internal secret and has no gateway route.

> **Open decision.** `semaphore → user` exists only to read `programCode` when a student's progress
> document is first created; afterwards the value is stored. Carrying `programCode` as a JWT claim
> would remove the edge entirely, at the cost of requiring a fresh sign-in after a programme change.
> Recorded rather than resolved: it was added without a decision, and it should have one.

### Git

**Read [CONTRIBUTING.md](CONTRIBUTING.md) before creating a branch, a commit or a pull request.** It holds the
full rules: the eight allowed commit types, Git Flow with the base and target of every branch type, the merge
strategy, SemVer, and a section written for AI agents. It is a verbatim copy of the K-Forge organization's canonical
guide, kept here because organization files are not included in a clone. Never edit it in this repository; run
`scripts/sync-contributing.sh` after the canonical one changes.

`scripts/check-git-conventions.sh` is the executable form of those rules. CI runs it on every pull request into
`main` or `develop` as the required `Git conventions` check, and `scripts/install-git-hooks.sh` (once per clone)
runs it before every local commit. When a rule here and the script disagree, fix one of them; do not work around it.

Rules specific to KApp:

- **Base branch is `develop`.** `main` only receives `release/*` and `hotfix/*`.
- **KApp has no production deployment, so there are no hotfixes.** A defect is a `bugfix/*` into `develop`.
- **Nobody pushes to `main` or `develop`**, administrators included. Admins may bypass only when merging a pull
  request, never to push. Every pull request needs the lead's review: `.github/CODEOWNERS` owns every path.
- **One session, one worktree.** Two sessions in the same folder share one index and one set of files: they stage
  each other's changes, overwrite each other's edits and run Gradle or Maven over each other's builds. A second
  session on an area gets its own worktree, `../KApp-worktrees/<area>-<topic>`, on its own branch. The defaults:

  | Path | Holds |
  |---|---|
  | `KApp/` | `develop`, for pulling and reviewing. Never commit here |
  | `../KApp-worktrees/docs` | `chore/*` branches: documentation, CI, repository configuration |
  | `../KApp-worktrees/backend` | `feature/*` and `bugfix/*` branches under `app/backend/` |
  | `../KApp-worktrees/mobile-kotlin` | `feature/*` and `bugfix/*` branches under `app/frontend/mobile/kotlin/` |
  | `../KApp-worktrees/mobile-swift` | `feature/*` and `bugfix/*` branches under `app/frontend/mobile/swift/` |

  Ignored files do not follow a new worktree: copy `app/backend/microservices/.env` and
  `app/frontend/mobile/kotlin/local.properties`, or regenerate them. Compose names its project after the directory,
  so stop the stack in one worktree before starting it in another.
- **A branch built on an unmerged branch** opens its pull request against that branch, and is retargeted to
  `develop` once the parent merges.

---

## Current State

Read `docs/PROGRESS.md` for up-to-date implementation status before proposing large changes.

- **Backend:** seven services on MongoDB. The Phase 0 skeleton is complete and verified — the full
  reactor builds with integration tests on Testcontainers. Domain logic for registration, the
  catalogue, progress, timetables and the map is the work in progress.
- **API contract:** five hand-written OpenAPI specs in `docs/api/`, served as Prism mocks. This is
  the source of truth. Write the spec first; springdoc output is a drift check against it, not the
  other way round.
- **Mobile (the product, in progress):** Kotlin and Swift clients, built against the mocks.
- **Web:** the admin and developer portal (Angular, `app/frontend/web-admin/`) is built, tested in CI and used by
  the mobile team to sign in as a role and reproduce a failing call. The old HTML/JS client under
  `app/frontend/web/` was a prototype of the mobile design and is frozen: it is not student-facing and nothing
  new is built on it.

---

## Roadmap

MVP scope is users, campus map, customisable preloaded schedule and customisable preloaded career
semaforo. Everything else is deferred. Keep this list in sync with `docs/PROGRESS.md`.

1. Registration: institutional e-mail, verification, invitation codes, and guest sign-up.
2. Academic catalogue and curricula, seeded from the published Ingenieria de Sistemas pensum.
3. Student progress with prerequisite-aware eligibility.
4. Timetables built from the student's curriculum, with manual day, time, room and group.
5. Campus map: buildings, floor plans and space search.
6. Kotlin (Android) and Swift (iOS) clients — the product.
7. Angular admin UI for managing preloaded data.
8. Entra ID adapter behind `IdentityProviderPort`, once the university grants a registration.
9. Deployment on university hardware: multi-architecture images, TLS, backups.
10. Deferred: refresh tokens and logout, Config Server, distributed tracing, Redis-backed rate
    limiting, database-per-service, Kubernetes.

---

## AI Agent Instructions

- **Never modify** `.env` files (contains secrets).
- **Backend location:** all backend work goes in `app/backend/microservices/`. The original monolith was deleted once the migration completed; it is only retrievable from the git history and must not be resurrected.
- **Schema:** changes go in Mongock change units (see Database). `app/database/init.sql` is the legacy PostgreSQL schema; nothing reads it, so do not update it.
- **Before large changes:** Read `docs/PROGRESS.md` first.
- **Before any git operation:** Read [CONTRIBUTING.md](CONTRIBUTING.md), including its section for AI agents. The commit author is the member running the session, never the agent.
- **No tool attribution, anywhere.** Never write a `Co-Authored-By` trailer for a tool, a "generated with" line, a
  signature or the name of the assistant used — not in commits, pull request titles or descriptions, code, or
  documentation. `scripts/check-git-conventions.sh` refuses a commit authored by an automated identity or one whose
  message credits one. The history records what the team built, not what wrote it.
- **Product identity:** KApp is a **mobile app** for Konrad Lorenz. The microservices are the backend that powers the mobile clients. Do not describe KApp as a "web platform" — it is a mobile-first product.
- **Demo mode:** `app/frontend/web/js/demo.js` intercepts API calls with sample data and activates only when the client is served from a host other than `localhost` (or forced with `?demo=1`). It exists so the interface can be deployed statically while no backend is hosted. Never point it at real data, and never let it change behaviour during local development.
- **Delivery sequence:** backend first, then **mobile directly**. The web client was a prototype of
  the mobile layout and is frozen; it is not a step on the path any more. The clients are built
  against the OpenAPI mocks rather than against a finished backend, which is what lets client and
  server work proceed at the same time.
- **Contract first.** Changing a request or response shape means editing `docs/api/*.openapi.yaml`
  first — four people are building against those mocks. CI lints them on every push.
- **No emojis** in technical markdown documents.
- **No automatic commits.** Present changes for review first.
- **Documentation language:** English for repository documentation.


---

## Temporary Files

- `tmp/` is gitignored. Store one-off scripts and throwaway files there.
- Delete after use. Never commit anything from `tmp/`.