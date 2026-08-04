# KApp · Implementation Status

> Last updated: July 2026

Delivery is sequenced **backend first, web second, mobile third**. The web client is the surface used to test the
API and to settle the interface design; the Kotlin and Swift clients inherit that design afterwards. Their low
position in the priority list below is that sequencing, not a change of target: the mobile apps are the product.

---

## Summary

| Area                          | Status       | Progress |
|-------------------------------|--------------|----------|
| Infrastructure                | [X] Complete | 100%     |
| Backend services              | [X] Complete | 100%     |
| Web frontend (test surface)   | [Med] Partial | 30%     |
| Android frontend (product)    | [ ] Not started | 0%    |
| iOS frontend (product)        | [ ] Not started | 0%    |
| DevOps                        | [Med] Partial | 50%     |
| Documentation                 | [Med] Partial | 70%     |

---

## Microservices

| Service               | Port   | Status         | Notes                              |
|-----------------------|--------|----------------|------------------------------------|
| Discovery Server      | 8761   | [X] Operational | Eureka dashboard working           |
| API Gateway           | 8080   | [X] Operational | JWT validation + routing           |
| Auth Service          | 8081   | [X] Operational | Login + JWT generation             |
| User Service          | 8082   | [X] Operational | Full CRUD + internal endpoints     |
| Course Service        | 8083   | [X] Operational | Courses, groups, enrollment        |
| Assignment Service    | 8084   | [X] Operational | Assignments, submissions, grading  |
| Common Library        | —      | [X] Operational | Shared DTOs + exceptions           |

---

## Features

### Authentication
- [x] Login with email and password
- [x] JWT with roles (STUDENT, PROFESSOR, ADMIN)
- [x] Centralized validation at the Gateway
- [x] BCrypt password hashing
- [ ] Refresh tokens
- [ ] Logout / token invalidation
- [ ] Role-based authorization enforcement (see `SECURITY-AUDIT.md`, finding S2)

### Users
- [x] Person CRUD
- [x] Member CRUD
- [x] Student CRUD
- [x] Employee CRUD
- [x] Internal endpoints (Feign)
- [ ] Editable user profile

### Courses
- [x] Course and group CRUD
- [x] Student enrollment
- [x] Student course list
- [x] Professor course list
- [x] Students per group
- [ ] Schedules
- [ ] Detailed academic programs

### Assignments
- [x] Create assignments (professor)
- [x] Pending assignments (student)
- [x] Submit assignments
- [x] Grade submissions
- [ ] File attachments
- [ ] Notifications

### Infrastructure
- [x] Eureka service discovery
- [x] API Gateway (Spring Cloud)
- [x] Circuit breaker (Resilience4j)
- [x] Docker Compose
- [x] Per-service Dockerfiles
- [ ] Config Server
- [ ] Distributed tracing
- [x] CI build pipeline (GitHub Actions)
- [ ] Continuous deployment
- [ ] Rate limiting

### Frontend

Web screens double as the design reference for the mobile clients.

- [x] Login page
- [x] Dashboard
- [x] Courses view
- [x] Assignments view
- [x] Grades view
- [x] Schedule view
- [x] Demo mode with sample data for backend-less static deployments (`js/demo.js`)
- [ ] Angular migration
- [ ] Android app (Kotlin)
- [ ] iOS app (Swift)

### DevOps
- [x] Docker Compose orchestration
- [x] Bash startup scripts
- [x] Technical documentation
- [x] CI build pipeline (GitHub Actions, `mvn verify` on JDK 21)
- [ ] CD / automated deployment
- [ ] Kubernetes manifests
- [ ] Monitoring (Prometheus/Grafana)

---

## Next Steps (by priority)

1. [High] **Authorization** — Enforce roles at the gateway and close the header-trust gap (`SECURITY-AUDIT.md` S1, S2)
2. [High] **Config Server** — Centralize configuration
3. [High] **Angular web** — Migrate the web frontend to Angular
4. [Med] **Refresh tokens** — Improve the authentication flow
5. [Med] **Rate limiting** — Protect the Gateway
6. [Low] **Kotlin app** — Build the Android client from the settled web design (low by sequence, not by value)
7. [Low] **Swift app** — Build the iOS client
8. [Low] **CD** — Extend the GitHub Actions pipeline to automated deployment

---

## Pending Work

Everything below is known and deliberately deferred: the project is a prototype in the thesis pre-proposal phase and
nothing is deployed. Recorded so it reads as a decision rather than an oversight. Security details and evidence live
in [SECURITY-AUDIT.md](SECURITY-AUDIT.md).

### Repository governance

| Item | Current state | What to do |
| ---- | ------------- | ---------- |
| Branch protection ruleset | Active on `main` and `develop`, but soft: zero required approvals, code owner review enabled with no `CODEOWNERS` file, no required status check, and organization admins bypass it. | Require one approval and make the CI workflow a required status check, so a red build blocks the merge. |
| Ruleset merge conflict | The ruleset requires linear history **and** allows merge commits only. Those are mutually exclusive: a pull request that is not fast-forward cannot be merged. | Switch the allowed merge method to squash, which also leaves one commit per pull request in `main`. |
| `CODEOWNERS` | Missing. It is a per-repository file and is **not** inherited from `K-Forge/.github`, unlike `CONTRIBUTING.md`, `SECURITY.md` and `CODE_OF_CONDUCT.md`. | Add `* @K-Forge/kapp-team`. Ownership must be the team, not a single person: GitHub does not accept a code owner approving their own pull request, so a sole owner blocks their own work. |
| Secret scanning | Disabled | Enable it: GitHub scans the tree and history for known credential formats and reports what it finds. Free on public repositories. |
| Push protection | Disabled | Enable it: rejects a push carrying a recognizable secret before it reaches the history. This is the control that would have prevented both credentials recorded as H1. |
| Dependabot | Alerts disabled, no `.github/dependabot.yml` | Enable the alerts, then add the configuration so Maven updates arrive as pull requests. Spring Boot 3.2.0 and Spring Cloud 2023.0.0 both have newer patch releases. |

### Presentation

| Item | Current state | What to do |
| ---- | ------------- | ---------- |
| Social preview image | Not set, so shared links render the generic GitHub card. | Upload `portfolio-cover.png` (already 1200 x 630, the exact size) under Settings, General, Social preview. There is no API for this; it has to be done from the interface. |
| Placeholder screens | `notas`, `horario`, `chat`, `clubes`, `pqr` and `inscribir` are empty shells. They are unreachable from the dashboard — the corresponding tiles are disabled and point at `#` — so the demo never lands on one. | Build them, or keep them unreachable until they exist. Do not link them from the navigation while empty. |

### Engineering

| Item | Current state | What to do |
| ---- | ------------- | ---------- |
| Test coverage | Zero across the six microservices. Deleting the monolith removed the only test file in the repository. | Cover the authorization path first: it is what the audit flags as critical (S1, S2) and what must not regress silently. |
| Authorization (S1, S2) | The gateway can be bypassed and no role is enforced anywhere. | Keep service ports off the host, make the propagated identity verifiable, and map `/api/admin/**`, `/api/professor/**` and `/api/student/**` to their roles. Tests first. |
| CI action versions | `actions/checkout@v4` and `actions/setup-java@v4` target Node 20, deprecated; the runner forces them onto Node 24 and annotates every run with a warning. | Bump both to `v5`. |
| Drawer identity field | `dashboard.html` declares `drawerCodeDrawer` while the other eight pages use `drawerCode`, which is the id `app.js` hydrates. The drawer therefore never shows the user code. Same class of defect as the two already fixed in the header. | Rename the id to `drawerCode`. |

### External and manual

| Item | Current state | What to do |
| ---- | ------------- | ---------- |
| Credential rotation (H1) | Two development credentials remain readable in the git history. | Confirm neither is reused anywhere and change them if they are. Rotation is the fix: the repository has been public since it was created, so any existing clone keeps the values regardless of what the history is rewritten to. |

---

## Project Structure

```
KApp/
├── app/
│   ├── backend/
│   │   ├── microservices/          # Backend — the only application code
│   │   │   ├── discovery-server/
│   │   │   ├── api-gateway/
│   │   │   ├── auth-service/
│   │   │   ├── user-service/
│   │   │   ├── course-service/
│   │   │   ├── assignment-service/
│   │   │   ├── common/
│   │   │   ├── docker-compose.yml
│   │   │   └── pom.xml
│   │   └── postman/                # Testing collections
│   ├── frontend/
│   │   ├── web/                    # Web client (HTML/JS to Angular)
│   │   └── mobile/
│   │       ├── kotlin/             # Android (planned)
│   │       └── swift/              # iOS (planned)
│   └── database/
│       ├── init.sql
│       ├── test_data.sql
│       └── delete_all_data.sql
├── docs/
│   ├── SRS.md
│   ├── REQUIREMENTS.md
│   ├── DESIGN.md
│   ├── DOCKER-GUIDE.md
│   ├── MICROSERVICES-IDEAS.md
│   ├── SECURITY-AUDIT.md
│   ├── K-COLORS.md
│   ├── researches/                 # Academic article reviews (PDF)
│   └── PROGRESS.md                 # This file
├── scripts/
│   ├── start-frontend.sh
│   └── start-microservices.sh
├── .github/
│   └── workflows/ci.yml
├── AGENTS.md
├── CONTRIBUTORS.md
├── LICENSE
└── README.md
```

---

*Update this file after every completed milestone.*
