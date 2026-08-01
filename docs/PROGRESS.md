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

## Pending — Repository Governance and Security

Deliberately deferred: the project is a prototype in the thesis pre-proposal phase, nothing is deployed, and these
items pay off once the codebase carries real work. Recorded here so they are decisions, not oversights. Details and
evidence live in [SECURITY-AUDIT.md](SECURITY-AUDIT.md).

| Item | Current state | What to do |
| ---- | ------------- | ---------- |
| Branch protection | Ruleset active on `main` and `develop`, but soft: zero required approvals, code owner review enabled with no `CODEOWNERS` file, no required status check, organization admins bypass it. | Add `CODEOWNERS`, require at least one approval, and make the CI workflow a required status check so a red build blocks the merge. |
| Secret scanning | Disabled | Enable it: GitHub scans the tree and history for known credential formats and reports what it finds. Free on public repositories. |
| Push protection | Disabled | Enable it: rejects a push that carries a recognizable secret before it reaches the history. This is the control that would have prevented both credentials recorded as H1. |
| Dependabot | Disabled, and no `.github/dependabot.yml` | Enable the alerts, then add the configuration file so Maven updates arrive as pull requests. Spring Boot 3.2.0 and Spring Cloud 2023.0.0 both have newer patch releases. |
| Test coverage | Zero across the six microservices | Cover the authorization path first — it is the area the audit flags as critical (S1, S2) and the one that must not regress silently. |

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
