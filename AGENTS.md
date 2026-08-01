# KApp · Agent Context

> Operational context and rules for AI agents working in this repository.

---

## K-Forge Ecosystem

K-Forge is a software development club at Fundación Universitaria Konrad Lorenz (FUKL), Bogotá, founded by Brian Vargas (@13rianVargas). The club builds real-world software products for the university and community.

| Project | Repo | Description |
|---------|------|-------------|
| K-Forge Website | `K-Forge/` | Public landing page (Angular, Vercel) |
| **KApp** | `KApp/` | University mobile app for Konrad Lorenz — you are here |
| TiendaQ | `TiendaQ/` | University e-commerce system (Spring Boot + Angular) |
| Roastory | `Roastory/` | Library-cafe management system (Node.js + MongoDB) |

---

## Project Overview

**KApp** is the **mobile application** for the Fundación Universitaria Konrad Lorenz community, developed by the K-Forge club. The product vision is mobile-first: native Android (Kotlin) and iOS (Swift) clients that give students and staff access to academic management — courses, assignments, users, and authentication — from their phones.

The mobile clients are powered by a **Spring Boot microservices backend** that exposes a unified API behind a Gateway. Delivery is sequenced backend first, web second, mobile third: the web frontend exercises the API while the backend is built and settles the interface design that the Kotlin and Swift clients will inherit.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Mobile (Android) | Kotlin — final product, not started |
| Mobile (iOS) | Swift — final product, not started |
| Web (API test surface, design reference) | HTML/JS/CSS → migrating to Angular |
| Backend | Java 21, Spring Boot 3.2, Spring Cloud 2023.0.0 |
| Service discovery | Netflix Eureka (`:8761`) |
| API Gateway | Spring Cloud Gateway (`:8080`) |
| Security | Spring Security + JWT (JJWT 0.11.5, BCrypt) |
| IPC | OpenFeign (service-to-service REST) |
| Resilience | Resilience4j Circuit Breaker |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 15+ (Neon cloud) |
| Build | Maven multi-module |
| Containers | Docker + Docker Compose |
| Package manager | pnpm (install) + Bun (scripts) |

---

## Microservices

| Service | Port | Directory |
|---------|------|-----------|
| Discovery Server | 8761 | `app/backend/microservices/discovery-server/` |
| API Gateway | 8080 | `app/backend/microservices/api-gateway/` |
| Auth Service | 8081 | `app/backend/microservices/auth-service/` |
| User Service | 8082 | `app/backend/microservices/user-service/` |
| Course Service | 8083 | `app/backend/microservices/course-service/` |
| Assignment Service | 8084 | `app/backend/microservices/assignment-service/` |
| Common Library | — | `app/backend/microservices/common/` |

---

## Repository Structure

```text
KApp/
├── app/
│   ├── backend/
│   │   ├── microservices/           # Backend — the only application code
│   │   │   ├── pom.xml              # Parent POM (multi-module)
│   │   │   ├── docker-compose.yml
│   │   │   ├── discovery-server/
│   │   │   ├── api-gateway/
│   │   │   ├── auth-service/
│   │   │   ├── user-service/
│   │   │   ├── course-service/
│   │   │   ├── assignment-service/
│   │   │   └── common/              # Shared DTOs and exceptions
│   │   └── postman/                 # Postman collections
│   ├── frontend/
│   │   ├── web/                     # Web client (HTML/JS/CSS → migrating to Angular)
│   │   └── mobile/
│   │       ├── kotlin/              # Android (future)
│   │       └── swift/               # iOS (future)
│   └── database/
│       ├── init.sql                 # Full schema (enums, tables, triggers)
│       ├── test_data.sql            # Mock data
│       └── delete_all_data.sql      # Cleanup
├── docs/
│   ├── SRS.md                       # Software Requirements Specification
│   ├── REQUIREMENTS.md
│   ├── DESIGN.md
│   ├── DOCKER-GUIDE.md
│   ├── K-COLORS.md
│   ├── PROGRESS.md                  # Implementation status — read before large changes
│   └── diagrams/
├── scripts/
│   ├── start-frontend.sh
│   └── start-microservices.sh
└── package.json
```

---

## Dev Commands

```bash
# One-time tooling setup
corepack enable && corepack prepare pnpm@latest --activate
curl -fsSL https://bun.sh/install | bash

# Start all microservices
./scripts/start-microservices.sh

# Start frontend
./scripts/start-frontend.sh

# Build (skip tests)
cd app/backend/microservices && mvn clean package -DskipTests

# Test (per service)
cd app/backend/microservices/<service> && mvn test

# Docker
cd app/backend/microservices && docker compose up -d --build
```

---

## Conventions

### Java

- Use Lombok to reduce boilerplate.
- DTOs and global exceptions live in the `common` module — not in individual services.
- Roles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.

### Security

- JWT validated at the API Gateway. Internal microservices are trusted.
- Gateway appends `X-User-Email` header to routed requests. Services read from this header.
- Never expose JWT secrets or DB credentials. Use environment variables.

### Database

- Schema defined in `app/database/init.sql`. Update it when adding tables/columns.
- Core tables: `person`, `member`, `student`, `employee`, `course`, `course_group`, `student_course`, `assignment`, `submission`, `audit_log`.
- Enums: `id_type`, `employee_type`, `contract_type`, `student_status`, `course_status`.
- Auditing via PostgreSQL triggers into `audit_log`.

### Inter-Service Communication

```
Assignment Service → (Feign) → Course Service → (Feign) → User Service
```

Services discovered by name via Eureka.

### Git

- **Commits:** Conventional Commits, English, lowercase, no scope, no final period.
  ```
  feat: add course enrollment endpoint
  fix: resolve jwt expiry handling
  chore: update spring boot to 3.2.5
  ```
- **Branches:** Git Flow — `main`, `develop`, `feature/*`, `bugfix/*`, `test/*`, `hotfix/*`, `release/*`.

### Versioning

SemVer `MAJOR.MINOR.PATCH`. Release cycle: alpha → beta → stable.

---

## Current State

Read `docs/PROGRESS.md` for up-to-date implementation status before proposing large changes.

- **Backend (current focus):** 6 microservices operational (discovery, gateway, auth, user, course, assignment). JWT validated at the gateway; role-based authorization still missing (see `docs/SECURITY-AUDIT.md`, S1 and S2).
- **Web (current test surface):** HTML/JS/CSS client that exercises the API end to end and settles the interface design. Angular migration pending.
- **Mobile (final product, not started):** Kotlin and Swift clients. They come after the web design is stable, and they inherit that design.

---

## Roadmap

Ordered by the delivery sequence: harden the backend, settle the design on web, then port to mobile. Keep this list
in sync with `docs/PROGRESS.md` and `docs/DESIGN.md`.

1. Enforce role-based authorization and close the gateway header-trust gap (`docs/SECURITY-AUDIT.md`, S1 and S2).
2. Centralize backend configuration via Spring Cloud Config Server.
3. Complete the web frontend migration to Angular — this is the reference design for the mobile clients.
4. Implement refresh tokens and logout flows.
5. Add rate limiting at the Gateway.
6. Build the Kotlin (Android) client from the settled web design.
7. Build the Swift (iOS) client.
8. Extend the CI pipeline into continuous deployment.
9. Implement distributed tracing (Zipkin).
10. Migrate to the database-per-service pattern.
11. Kubernetes deployment manifests.

---

## AI Agent Instructions

- **Never modify** `.env` files (contains secrets).
- **Backend location:** all backend work goes in `app/backend/microservices/`. The original monolith was deleted once the migration completed; it is only retrievable from the git history and must not be resurrected.
- **Schema:** Modify `app/database/init.sql` carefully. Align data types with existing structures.
- **Before large changes:** Read `docs/PROGRESS.md` and the contribution guidelines published by the K-Forge organization first.
- **Product identity:** KApp is a **mobile app** for Konrad Lorenz. The microservices are the backend that powers the mobile clients. Do not describe KApp as a "web platform" — it is a mobile-first product.
- **Demo mode:** `app/frontend/web/js/demo.js` intercepts API calls with sample data and activates only when the client is served from a host other than `localhost` (or forced with `?demo=1`). It exists so the interface can be deployed statically while no backend is hosted. Never point it at real data, and never let it change behaviour during local development.
- **Delivery sequence:** backend first, web second, mobile third. Web (`app/frontend/web/`) is the surface used to test the API and to settle the interface design; that design is later ported to Kotlin (`app/frontend/mobile/kotlin/`) and Swift (`app/frontend/mobile/swift/`), which are the final product. The mobile clients sitting at low priority in `docs/PROGRESS.md` is intentional sequencing, not a change of target.
- **No emojis** in technical markdown documents.
- **No automatic commits.** Present changes for review first.
- **Documentation language:** English for repository documentation.


---

## Temporary Files

- `tmp/` is gitignored. Store one-off scripts and throwaway files there.
- Delete after use. Never commit anything from `tmp/`.