# KApp · Containerization Guide

> **Superseded by [`RUNBOOK.md`](RUNBOOK.md) for anything you actually want to run.** This guide used to describe the
> 2025 stack too — a local database, the Eureka registry and the `course-service` and `assignment-service` deleted in
> October 2026 — and two guides that disagreed helped nobody, so those parts live in the runbook only. What stays
> here is background that is still accurate: the Dockerfile pattern, image optimisation, and what production needs.

Where to find what this guide used to cover:

| You want to | Read |
|---|---|
| Install what the stack needs | [Prerequisites](RUNBOOK.md#prerequisites) |
| Choose which services to start | [Profiles](RUNBOOK.md#profiles) |
| Run two worktrees' stacks side by side | [Stacks: one per worktree](RUNBOOK.md#stacks-one-per-worktree) |
| Start, stop and follow logs | [Starting and stopping](RUNBOOK.md#starting-and-stopping) |
| Know which port is which | [What runs where](RUNBOOK.md#what-runs-where) |
| Point the services at the database | [Pointing at Atlas](RUNBOOK.md#pointing-at-atlas) |
| Fix a service that will not start | [Troubleshooting](RUNBOOK.md#troubleshooting) |

In short: there is no local database, since every service reaches the shared Atlas cluster
([ADR 0009](adr/0009-atlas-is-the-only-development-database.md)). There is no registry either: services address each
other by name, which Docker Compose and Kubernetes resolve. The Prism mocks answer on `http://localhost:4000`.

---

## 1. The Dockerfile pattern

Every service already has its own `Dockerfile`. The real ones build from the reactor root, `app/backend/microservices`,
with the Maven wrapper, so `common` and the parent POM are in the build context. The pattern, reduced to its parts:

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Best practices:**

- Multi-stage build (final image without Maven/JDK)
- `alpine` base for lightweight images (around 150 MB)
- `EXPOSE` documents the service port

---

## 2. Image Optimization

### Reduce image size

```dockerfile
# Use the JRE instead of the JDK
FROM eclipse-temurin:21-jre-alpine

# Add a .dockerignore in each service
# Suggested contents:
target/
*.md
.git
.idea
```

### Maven dependency cache

```dockerfile
# Copy pom.xml first to take advantage of layer caching
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests
```

---

## 3. Production Considerations

Production runs on **Kubernetes**: locally with kind, and on hardware agreed with the university's IT director.
That is backend phase 6 (#59), which will document it. What it has to cover:

- [ ] Secrets from the cluster's secret store, not from `.env` files
- [ ] Service ports internal to the cluster; only the gateway exposed, behind TLS
- [ ] Health checks on every service, as readiness and liveness probes
- [ ] CPU and memory limits per container
- [ ] Images from the registry they are already published to, GHCR
- [ ] Centralised logging and alerting
- [ ] Backups of the database, copied off the host

---

_For more detail on the architecture, see [`DESIGN.md`](DESIGN.md)._
