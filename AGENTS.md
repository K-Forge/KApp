# KApp · Agent Context

> Operational context and rules for AI agents working in this repository.

---

## Project Overview

**KApp** is the **mobile application** for the Fundación Universitaria Konrad Lorenz community: native Android
(Kotlin) and iOS (Swift) clients, a web portal for the staff who administer it, and a Spring Boot microservices
backend behind a single gateway. It shows a student their timetable, their progress through their pensum and the
campus map, and gives the community notifications, a student voice, damage reports and lost and found.

**KApp reads SINU, the university's academic system, and never copies it.** Timetables, the catalogue, programmes,
pensums, levels and course statuses are read from SINU, read-only, cached for minutes at most. Nothing academic is
stored; a student only edits their own **plans**. Until the university opens SINU, a **test SINU adapter** serves
invented data with SINU's shape. `docs/SINU-ALIGNMENT.md` says what may and may not be stored.

**Members sign in with Microsoft** (Entra ID), in the apps and in the portal. auth-service verifies the Microsoft
sign-in and issues KApp's own tokens. Password sign-in exists only for development and tests.

**Dates and team.** Brian hands in the thesis proposal at the end of the 2026-2 term; the product is presented in
**2027-2**. Backend: Brian. Kotlin: Iván, Alejandro and Santiago. Swift: Brian.

Delivery is contract first: the clients build against the hand-written OpenAPI contracts in `docs/api/`, served as
Prism mocks, so client and server progress in parallel.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Mobile (Android) | Kotlin + Jetpack Compose |
| Mobile (iOS) | Swift + SwiftUI |
| Admin portal | Angular — `app/frontend/web-admin/`, compose `dev` profile |
| Backend | Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x — on Boot 3 on purpose: Mongock has no Boot 4 artifact |
| API Gateway | Spring Cloud Gateway (`:8080`) |
| Service addressing | By name, resolved by Docker Compose and Kubernetes. **No registry** |
| Identity | Microsoft Entra ID for sign-in; auth-service issues RS256 tokens with a published JWKS |
| IPC | OpenFeign (service-to-service REST) |
| Resilience | Resilience4j Circuit Breaker |
| Persistence | Spring Data MongoDB |
| Database | MongoDB Atlas, one shared development cluster ([ADR 0009](docs/adr/0009-atlas-is-the-only-development-database.md)); tests start their own through Testcontainers |
| Migrations | Mongock (versioned change units, distributed lock) |
| API contract | Hand-written OpenAPI 3.1 in `docs/api/`, served as Prism mocks |
| Testing | JUnit 5 + Testcontainers |
| Build | Maven multi-module |
| Deployment | Docker Compose locally; **Kubernetes** (kind locally, and in production) is backend phase 6 (#59). The dev server on the-hive follows `develop` with per-branch images on GHCR |
| Package manager | pnpm |

---

## Microservices

| Service | Port | Owns |
|---------|------|------|
| API Gateway | 8080 | Routing, CORS, rate limiting. **No authentication logic** |
| Auth Service | 8081 | Sign-in, KApp's tokens (RS256, JWKS), refresh tokens, visitor passes |
| User Service | 8082 | Profiles: names, picture, roles, active flag. A student's programme is read from SINU |
| Semaphore Service | 8083 | The student's plans. Catalogue, pensums and course statuses are read from SINU |
| Schedule Service | 8084 | Nothing: it reads the timetable from SINU and has **no database** |
| Map Service | 8085 | Buildings (each mapped to its SINU sedes), floors drawn as polygons, spaces, search |
| Common Library | — | Security and error auto-configuration |

All under `app/backend/microservices/`. `mock/` builds the Prism mocks.

**Gone, and kept only by git:** the Eureka registry (`discovery-server`, #64) and the frozen `course-service` and
`assignment-service` (#65). Do not bring any of them back.

**Planned:** `notification-service`, then points, voice, damage reports and lost and found, one service per feature
with a transactional outbox. The roadmap is [`docs/MICROSERVICES-IDEAS.md`](docs/MICROSERVICES-IDEAS.md).

---

## Repository Structure

```text
KApp/
├── app/
│   ├── backend/
│   │   └── microservices/           # Backend
│   │       ├── pom.xml              # Parent POM (multi-module)
│   │       ├── docker-compose.yml   # Profiles: mock, core, academic, map, full, dev
│   │       ├── api-gateway/  auth-service/  user-service/
│   │       ├── semaphore-service/  schedule-service/  map-service/
│   │       ├── common/              # Security and error auto-configuration
│   │       └── mock/                # Prism mocks behind one port
│   └── frontend/
│       ├── web-admin/               # Admin and developer portal (Angular)
│       └── mobile/
│           ├── kotlin/              # Android
│           └── swift/               # iOS
├── deploy/dev-server/               # The dev server on the-hive: compose and the agent that follows a branch
├── docs/
│   ├── api/                         # OpenAPI contracts — the source of truth — and their CHANGELOG
│   ├── adr/                         # Architecture decisions and their reasons
│   ├── map/  pensums/  design/  templates/
│   ├── RUNBOOK.md                   # How to run everything — start here
│   ├── ONBOARDING.md                # A new machine, in Spanish
│   ├── PROGRESS.md                  # Implementation status — read before large changes
│   ├── SINU-ALIGNMENT.md            # What KApp reads from SINU, and what it must never store
│   ├── ti/                          # What KApp asks of the IT office, in Spanish; .docx built by scripts/build-ti-docs.sh
│   ├── REQUIREMENTS.md  SRS.md  DESIGN.md  MICROSERVICES-IDEAS.md
│   ├── INTEGRATION-NOTES.md  SECURITY-AUDIT.md  ATLAS-SETUP.md  K-COLORS.md
│   └── DOCKER-GUIDE.md              # Superseded by RUNBOOK.md
├── scripts/
│   ├── check-git-conventions.sh     # The git rules as code: CI and the commit-msg hook run it
│   ├── install-git-hooks.sh         # Once per clone: enables .githooks/
│   ├── prune-local-branches.sh      # Deletes local branches whose pull request merged (run by the hooks)
│   ├── sync-contributing.sh         # Refreshes CONTRIBUTING.md from the organization
│   ├── restack.sh                   # Rebases a stack after its bottom is squash merged
│   └── generate-dev-secrets.sh  create-dev-accounts.sh  verify-db-isolation.sh
├── .githooks/
│   ├── commit-msg                   # Refuses a commit that breaks CONTRIBUTING.md
│   └── post-checkout  post-merge    # Prune merged local branches after a switch to develop or a pull
├── .github/
│   ├── CODEOWNERS                   # The lead reviews every path
│   ├── dependabot.yml               # Weekly, grouped dependency updates into develop
│   ├── pull_request_template.md     # Purpose, commits, testing, risks; replaces the organization's
│   └── workflows/
│       ├── ci.yml                   # Backend, contracts, portal, Android and the pensum seed, by path
│       ├── conventions.yml          # Branch, title, commits and area label of every pull request
│       ├── dev-images.yml           # Per-branch images for the dev server, on GHCR
│       └── restack.yml              # After a squash merge, rebases the pull requests stacked on it
├── CONTRIBUTING.md                  # Verbatim copy of the K-Forge guide. Never edit here
└── package.json
```

---

## Dev Commands

Full guide, including profiles, accounts and troubleshooting: **[docs/RUNBOOK.md](docs/RUNBOOK.md)**.

Requires JDK 21 and Docker. The repository pins the JDK with `.java-version` (jenv) and ships a Maven wrapper, so use
`./mvnw` rather than a system Maven.

```bash
cd app/backend/microservices

# Build and run every test. Tests use Testcontainers, so Docker must be running.
./mvnw -B verify

# One service only
./mvnw -B -pl map-service -am verify
```

There is no local database: every service reaches the shared Atlas cluster through its own `MONGO_*_URI` in `.env`
(`docs/ATLAS-SETUP.md`). Compose profiles exist so nobody runs every JVM to work on one service:

```bash
cd app/backend/microservices

# Client work: the Prism mocks alone. No JVM, no database.
docker compose --profile mock up -d      # all five on http://localhost:4000; 4010-4014 one per service

# Backend work: only what you need
docker compose --profile core up -d      # gateway, auth, user
docker compose --profile map up -d       # core + map-service
docker compose --profile academic up -d  # core + semaphore + schedule
docker compose --profile full up -d      # everything, mocks included
docker compose --profile dev up -d       # the admin portal, on :4300

docker compose --profile full --profile dev down
```

Only the gateway (8080) is published among the JVMs. Reaching a service directly is meant to fail. Containers are
named after the worktree they came from (`KAPP_STACK` in `.env`), so two worktrees' stacks never collide.

```bash
# Validate the API contracts the clients build against
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

### Roles

auth 1.0.0 defines them, and backend phase 3 (#56) implements them:

- **One profile role** per member, taken from the university directory at sign-in: `ROLE_STUDENT`,
  `ROLE_PROFESSOR` or `ROLE_STAFF`. A member the directory does not classify is `ROLE_STAFF`, which sees the map
  only. The app's tabs follow the profile role.
- **Permissions** on top, granted by an administrator, which only open the portal: `ROLE_ADMIN`, `ROLE_RECEPTION`,
  `ROLE_MAINTENANCE`, `ROLE_MODERATION`, `ROLE_WELLBEING`.
- `ROLE_GUEST` is a visitor with a one-day pass from reception: the map and nothing else, with no account.

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
- **Members have no password in production.** Password sign-in, registration, verification and invitation codes
  answer only when local sign-in is enabled, for development and tests.
- Never expose signing keys or DB credentials. Use environment variables.

### Data

- **Read SINU, never copy it.** Before adding a field, ask whose datum it is: anything academic belongs to SINU and
  is read, not stored. No identity document, phone or student code is stored for members, and no administrator reads
  students' academic data. `docs/SINU-ALIGNMENT.md` is the reference.
- MongoDB, one database **and one account** per service. Development uses the shared Atlas cluster; tests start a
  single-node replica set through Testcontainers, since a standalone `mongod` cannot do multi-document transactions.
- **Schema changes go in Mongock change units**, never applied by hand. They are append-only:
  never edit one that has run, add a new one.
- Every service that touches MongoDB must carry `@EnableMongock`. Mongock 5.5.1 ships no
  auto-configuration, so without it migrations are skipped in silence.
- Academic periods use the university's own format, `YYYYS` (e.g. `20262`). Validate with
  `@ValidAcademicPeriod` from `common`.

### Inter-Service Communication

Four Feign edges. Keep the number small: every extra one is a new failure mode and a new reason for a service to be
unable to answer on its own.

```
Auth Service     → (Feign) → User Service       # create the profile of a new credential, remove it with the account
User Service     → (Feign) → Auth Service       # suspend the credential when a profile is deactivated
Schedule Service → (Feign) → Semaphore Service  # course catalogue
Schedule Service → (Feign) → Map Service        # which building each SINU sede is
```

The first two run once per account change and carry the shared `X-Internal-Token`, since there may be no user token
yet. The last two read near-static reference data with the caller's own token, attached by `common`, and
schedule-service handles either one being unreachable (`PensumCatalogService`, `SedeBuildings`).

**There is no registry.** A service is addressed by its name — `http://user-service:8082` — which Docker Compose and
Kubernetes resolve. The gateway's routes and every Feign client take their URL from configuration
(`kapp.services.*` in the gateway), so a host-networked server can set `127.0.0.1` and a port instead.

Services that cross boundaries in the future do it through a **transactional outbox** in MongoDB, with idempotency
keys and no broker, not through new Feign edges.

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
- **`develop` only takes squash merges, through the merge queue.** The ruleset offers no other method, so the
  pull request title becomes the one commit on `develop`. Merge with "Merge when ready": the queue tests each
  pull request on top of the ones queued before it and merges them in order. CI runs only the jobs whose paths a
  pull request touches (`.github/workflows/ci.yml`, job `changes`); a skipped job counts as passed.
- **Releases are prepared on `develop`**, so nothing ever has to come back from `main`. First a pull request such
  as `chore/prepare-release-0-3-0`, titled `release: prepare version 0.3.0`, bumps `package.json` and writes
  `CHANGELOG.md`. Then `release/0.3.0` is cut from `develop` with no commits of its own, merged into `main` with a
  merge commit and tagged `v0.3.0`. A defect found during the release is fixed on `develop` and the release is cut
  again. This replaces CONTRIBUTING's merge back into `develop`.
- **A pull request into `develop` does not close its issue**, because only the default branch, `main`, does that.
  Close the issue by hand after the merge.
- **Four chats, one area each**: Kotlin, Swift, Backend and Docs/Chore. They coordinate through GitHub issues in
  English, labelled `area/kotlin`, `area/swift`, `area/backend`, `area/docs`, plus `contract` when a contract in
  `docs/api` changes. **Every pull request carries its area label too**, and Git conventions fails without one. A
  chat never touches another chat's worktree; what belongs to another area becomes an issue for it.
- **One session, one worktree.** Two sessions in the same folder share one index and one set of files: they stage
  each other's changes, overwrite each other's edits and run Gradle or Maven over each other's builds. A second
  session on an area gets its own worktree, `../KApp-worktrees/<area>-<topic>`, on its own branch.

  **The worktree follows the area you touch; the branch prefix follows the kind of change** (CONTRIBUTING.md).
  The two are independent: `chore/kotlin-build-config` is maintenance, so it is a `chore/*`, and it edits Gradle
  files, so it belongs in the `mobile-kotlin` worktree — not in `docs`. The defaults:

  | Path | Work on |
  |---|---|
  | `KApp/` | Nothing. It holds `develop`, for pulling and reviewing. Never commit here |
  | `../KApp-worktrees/docs` | `docs/`, `README.md`, this file, `.github/` and repository configuration |
  | `../KApp-worktrees/backend` | `app/backend/`, `web-admin`, `docs/api`, `docs/pensums`, `docs/map` |
  | `../KApp-worktrees/mobile-kotlin` | `app/frontend/mobile/kotlin/`, its Gradle and build files included |
  | `../KApp-worktrees/mobile-swift` | `app/frontend/mobile/swift/`, its Xcode project included |

  Ignored files do not follow a new worktree: copy `app/backend/microservices/.env` and
  `app/frontend/mobile/kotlin/local.properties`, or regenerate them.
- **A branch built on an unmerged branch** opens its pull request against that branch. When the parent is squash
  merged, `.github/workflows/restack.yml` rebases the child onto `develop`, retargets it and cascades to the pull
  requests stacked on it, pushing as the K-Forge restack App so their checks run again. Nobody runs git in
  between. A real conflict is left untouched, with the commands to finish by hand in a comment. Stack only when a
  feature needs unmerged code, and keep stacks to two or three levels.
- **One pull request, one purpose.** See *Pull requests and commits* below; it is what review checks first.
- **When your pull request merges, leave its branch.** Run `git switch --detach origin/develop` in your worktree
  (after `git fetch`), or start the next branch from there. The hooks then delete every local branch whose pull
  request merged: `.githooks/post-checkout` on a switch to develop, `.githooks/post-merge` after a pull. Both run
  `scripts/prune-local-branches.sh`, which never touches a branch some worktree has checked out or one with
  commits outside a merged pull request; run it by hand to see what it keeps and why.

### Pull requests and commits

How a company that ships for years works, adapted from Google's engineering practices ("Small CLs", "Writing good CL
descriptions"). The lead decided on 2026-10-13: **one pull request, one purpose**: neither a pull request for every
edit, nor everything packed into one.

**What one pull request is**

1. **One purpose you can say in one sentence**, which becomes its title. If the sentence needs an "and", it is two
   pull requests. It names its issue in the description with `Refs #N` (into `develop`, `Closes` does nothing).
2. **Complete.** The code, its tests and the docs or contract it changes go together, and `develop` works after
   the merge. A new endpoint, model or component lands with the code that uses it, never on its own.
3. **Sized for a real review: about 100 to 400 lines a person has to read.** Lockfiles, generated code, seeds,
   transcriptions, map data and photos do not count, and they go in a pull request of their own when they are
   large. Past 400 lines, split by slices that work on their own (sign-in, then renewal, then sign-out), stacked
   if each needs the previous one; never by layers that do nothing alone.
4. **Under about 50 lines only when the change stands on its own**: a real defect with the test that proves it,
   or a dependency update. A comment, a rename or a tweak travels with the next pull request on the same topic.
   Before opening one, check `gh pr list --label area/<area>`; if one on the same topic is still waiting for
   review, push to it and update its description.
5. **Kept apart:** a refactor never shares a pull request with a change in behaviour, and moving files never
   shares one with editing them.

**Commits inside it**

- **Each commit is one step that builds and passes its tests**, in an order that tells how the change was made:
  `feat: store the refresh token in the keystore`, then `feat: renew the session before it expires`, then
  `feat: sign out when a reused token is refused`, then `test: cover renewal, reuse and sign-out`. A feature
  usually takes three to eight.
- **No `wip`, `fix typo` or `address review` commits.** Fold them into the commit they correct
  (`git commit --fixup <sha>` and `git rebase -i --autosquash origin/develop`) before asking for review, then
  `git push --force-with-lease` on your own branch.
- On `develop` the pull request becomes one squashed commit titled like the pull request. The commits are for
  the review, and the one on `develop` is what gets reverted if something breaks.

**The description** follows `.github/pull_request_template.md`: why, what changed (one line per commit), how it
was tested (commands, and screenshots for anything visible), risks and what is left for later. It is written for
the person who finds this pull request a year from now.

**The way to merge**

1. Open the pull request as a **draft** as soon as the branch has its first commit, and push at least once a day.
   Work that exists only on one laptop is work that can be lost.
2. Mark it ready when it meets the five points above and CI is green.
3. The Docs/Chore chat reviews agent pull requests against this section and tells the lead which are ready. The
   lead reviews the people's. The lead merges with "Merge when ready".

**Work shared by both clients.** A feature both apps need is a parent issue without a client label, with one
sub-issue per client: `[Kotlin] ...` labelled `area/kotlin` and `[Swift] ...` labelled `area/swift` (as in #44 to
#47). Each client closes its own sub-issue by hand when its pull request merges; the parent shows the progress and
closes when both are done. Kotlin goes first; Swift resumes when the Android app is finished.

---

## Current State

Read `docs/PROGRESS.md` for the detailed status before proposing large changes.

- **Contracts:** the 2.0 set (#42) — semaphore 2.0, schedule 2.0, auth 1.0, user 1.0, map 3.6.
  `docs/api/CHANGELOG.md` holds the history. Write the spec first; springdoc output is a drift check against it,
  not the other way round.
- **Backend:** phase 1 removed Eureka and the frozen services; phase 2 made the academic data read-only, behind the
  test SINU adapter. Phase 3 is under way: rotating refresh tokens (#82) and development-only password sign-in
  (#83) are in; Microsoft sign-in, permissions and the new roles are next.
- **Android:** sign-in, home, profile, semáforo with plans, and timetable, against the mocks.
- **iOS:** in progress in its own worktree.
- **Portal:** built, tested in CI, and used by the team to sign in as a role and reproduce a failing call.
- **Dev server:** the-hive follows `develop`.
- The 2025 HTML/JS prototype and the legacy PostgreSQL schema were deleted (#51). A better public demo is planned
  (#92).

---

## Roadmap

The backend plan is phases 1 to 8, one issue each. Keep this list in sync with `docs/PROGRESS.md`.

1. Remove Eureka and the frozen services (#54).
2. Read-only academic data behind a test SINU (#55).
3. Microsoft sign-in, rotating refresh tokens, profile roles and permissions (#56). The Entra ID test tenant is #62.
4. End-to-end journeys per profile (#57).
5. EC floors faithful to the plans (#58), blocked on Brian's sketches.
6. Kubernetes with kind, its guide, and production (#59). Production hardware is agreed with the university's IT
   director; the fallback is Brian's Mac running kind.
7. `notification-service`, with an inbox, push and an outbox (#60).
8. Points, voice, damage reports and lost and found (#61).

Events, clubs and recreation bookings wait for a questionnaire to the offices. The clients follow each contract
through `area/kotlin` and `area/swift` issues.

---

## AI Agent Instructions

- **Never modify** `.env` files (contains secrets).
- **Backend location:** all backend work goes in `app/backend/microservices/`. The original monolith was deleted once the migration completed; it is only retrievable from the git history and must not be resurrected.
- **Schema:** changes go in Mongock change units (see Data), never in a hand-written schema file.
- **Read SINU, never copy it.** Nothing academic is stored, and no administrator reads students' academic data.
- **Before large changes:** Read `docs/PROGRESS.md` first.
- **Before any git operation:** Read [CONTRIBUTING.md](CONTRIBUTING.md), including its section for AI agents. The commit author is the member running the session, never the agent.
- **No tool attribution, anywhere.** Never write a `Co-Authored-By` trailer for a tool, a "generated with" line, a
  signature or the name of the assistant used — not in commits, pull request titles or descriptions, code, or
  documentation. `scripts/check-git-conventions.sh` refuses a commit authored by an automated identity or one whose
  message credits one. The history records what the team built, not what wrote it.
- **Product identity:** KApp is a **mobile app** for Konrad Lorenz. The microservices are the backend that powers the mobile clients. Do not describe KApp as a "web platform" — it is a mobile-first product.
- **Delivery sequence:** backend first, then **mobile directly**. The clients are built
  against the OpenAPI mocks rather than against a finished backend, which is what lets client and
  server work proceed at the same time.
- **Contract first.** Changing a request or response shape means editing `docs/api/*.openapi.yaml`
  first — the Kotlin and Swift teams build against those mocks. CI lints them on every push.
- **No emojis** in technical markdown documents.
- **No automatic commits.** Present changes for review first.
- **Documentation language:** English for repository documentation.

---

## Temporary Files

- `tmp/` is gitignored. Store one-off scripts and throwaway files there.
- Delete after use. Never commit anything from `tmp/`.
